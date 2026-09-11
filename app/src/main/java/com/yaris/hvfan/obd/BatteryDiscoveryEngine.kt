package com.yaris.hvfan.obd

enum class ProbeStatus {
    PENDING,
    SUCCESS,
    NO_DATA,
    TIMEOUT,
    REJECTED,
    INVALID
}

data class ProbeResult(
    val pid: String,
    val status: ProbeStatus,
    val rawResponse: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Phased non-blocking battery PID discovery engine.
 *
 * Core Responsibilities & Invariants:
 * 1. Exactly ONE candidate PID probed per eligible slow cycle slice (3500ms).
 * 2. Rejected, timed-out, or invalid candidates enter a cooldown state (5s)
 *    and the discovery cursor advances to the next candidate on subsequent cycles.
 * 3. The first candidate returning a valid, parseable battery payload is latched as
 *    activeBatteryPid, transitioning discovery to Discovered and ceasing further candidate probing.
 * 4. Maximum probe timeout bound <= 3000ms. Questo bound resta invariato: e' il timeout BLE
 *    esterno usato solo durante la fase di discovery (probing dei candidati), ben al di sopra
 *    del timeout ELM interno AT ST FF (~1044ms, Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU) usato per
 *    la risposta multi-frame UDS 2228C1, quindi non necessita di incremento.
 * 5. Lifecycle teardown / resets clear cached latched PID, cooldowns, and cursor.
 * 6. completedFailureCycles conta i giri completi della fallback chain terminati tutti in
 *    fallimento (nessun candidato riuscito). Usato dal chiamante per distinguere un singolo
 *    NODATA transitorio da un pattern persistente (probabile limite hardware dell'adapter OBD).
 */
class BatteryDiscoveryEngine(
    val candidates: List<String> = ToyotaYarisCommands.BATTERY_FALLBACK_PIDS,
    val cooldownDurationMs: Long = DEFAULT_COOLDOWN_MS,
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val DEFAULT_COOLDOWN_MS = 5_000L // 5s cooldown per candidate backoff (FIX 4); accelerazione discovery batteria
        const val MAX_PROBE_TIMEOUT_MS = 3000L
    }

    var latchedPid: String? = null
        private set

    val activeBatteryPid: String? get() = latchedPid

    val isDiscovered: Boolean get() = latchedPid != null

    private var cursorIndex: Int = 0
    private val cooldownMap = mutableMapOf<String, Long>()
    private val permanentlyRejectedPids = mutableSetOf<String>()
    val rejectedPids: Set<String> get() = permanentlyRejectedPids.toSet()

    private val _probeOutcomes = mutableMapOf<String, ProbeResult>()
    val probeOutcomes: Map<String, ProbeResult> get() = _probeOutcomes.toMap()

    private var completedCycleCount: Int = 0

    /**
     * Numero di giri completi della fallback chain in cui OGNI candidato e' fallito senza mai
     * latchare una risposta valida. Si azzera su onCandidateSuccess() e reset().
     */
    val completedFailureCycles: Int get() = completedCycleCount

    /**
     * Verifica se un PID è stato marcato come non supportato (NRC 11/12) ed escluso permanentemente.
     */
    fun isPidRejected(pid: String): Boolean = permanentlyRejectedPids.contains(pid)

    /**
     * Returns the next candidate PID eligible for probing, or null if all candidates
     * are currently in cooldown or if a candidate is already latched.
     */
    fun getNextCandidate(): String? {
        if (isDiscovered) return null
        if (candidates.isEmpty()) return null

        val now = timeProvider()
        for (i in candidates.indices) {
            val idx = (cursorIndex + i) % candidates.size
            val candidate = candidates[idx]
            if (permanentlyRejectedPids.contains(candidate)) {
                continue
            }
            val cooldownUntil = cooldownMap[candidate] ?: 0L
            if (now >= cooldownUntil) {
                // Return candidate without advancing cursor yet; cursor advances on outcome
                return candidate
            }
        }
        return null
    }

    /**
     * Called when a candidate probe succeeds with a valid parseable battery status.
     * Latches the candidate as active battery PID and ceases further discovery probing.
     */
    fun onCandidateSuccess(pid: String, rawResponse: String? = null) {
        latchedPid = pid
        completedCycleCount = 0
        _probeOutcomes[pid] = ProbeResult(
            pid = pid,
            status = ProbeStatus.SUCCESS,
            rawResponse = rawResponse,
            timestamp = timeProvider()
        )
    }

    /**
     * Marca un PID come permanentemente non supportato dall'ECU (es. NRC 7F xx 11 / 12)
     * escludendolo all'istante dalla discovery chain senza ulteriori ritentativi.
     */
    fun onCandidateRejected(pid: String, rawResponse: String? = null) {
        val now = timeProvider()
        permanentlyRejectedPids.add(pid)
        _probeOutcomes[pid] = ProbeResult(
            pid = pid,
            status = ProbeStatus.REJECTED,
            rawResponse = rawResponse,
            timestamp = now
        )
        advanceCursorAfterPid(pid)
    }

    /**
     * Called when a candidate probe fails (timeout, NO DATA, error, negative response, or unparseable).
     * Places the candidate into cooldown (5s) and advances the discovery cursor to the next candidate.
     * Every time the cursor wraps back to the start of the candidate list, a full failed cycle of the
     * fallback chain has completed (see completedFailureCycles).
     */
    fun onCandidateFailed(pid: String, status: ProbeStatus = ProbeStatus.NO_DATA, rawResponse: String? = null) {
        val now = timeProvider()
        if (status == ProbeStatus.REJECTED) {
            permanentlyRejectedPids.add(pid)
        } else {
            cooldownMap[pid] = now + cooldownDurationMs
        }
        _probeOutcomes[pid] = ProbeResult(
            pid = pid,
            status = status,
            rawResponse = rawResponse,
            timestamp = now
        )
        advanceCursorAfterPid(pid)
    }

    private fun advanceCursorAfterPid(pid: String) {
        val currentIndex = candidates.indexOf(pid)
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % candidates.size
        } else {
            (cursorIndex + 1) % candidates.size
        }
        if (nextIndex == 0 && candidates.isNotEmpty()) {
            completedCycleCount++
        }
        cursorIndex = nextIndex
    }

    /**
     * Check if a specific candidate is currently in cooldown.
     */
    fun isCandidateInCooldown(pid: String): Boolean {
        if (permanentlyRejectedPids.contains(pid)) return false
        val now = timeProvider()
        val cooldownUntil = cooldownMap[pid] ?: return false
        return now < cooldownUntil
    }

    /**
     * Get remaining cooldown time in milliseconds for a candidate, or 0 if not in cooldown.
     */
    fun getRemainingCooldownMs(pid: String): Long {
        if (permanentlyRejectedPids.contains(pid)) return 0L
        val now = timeProvider()
        val cooldownUntil = cooldownMap[pid] ?: return 0L
        return (cooldownUntil - now).coerceAtLeast(0L)
    }

    /**
     * Check if all non-rejected candidates are currently in cooldown.
     */
    fun areAllCandidatesInCooldown(): Boolean {
        if (candidates.isEmpty()) return false
        val eligibleCandidates = candidates.filter { !permanentlyRejectedPids.contains(it) }
        if (eligibleCandidates.isEmpty()) return true
        return eligibleCandidates.all { isCandidateInCooldown(it) }
    }

    /**
     * Resets discovery state, clears latched PID, cooldowns, outcomes, and resets cursor.
     */
    fun reset() {
        latchedPid = null
        cursorIndex = 0
        completedCycleCount = 0
        cooldownMap.clear()
        _probeOutcomes.clear()
        permanentlyRejectedPids.clear()
    }
}
