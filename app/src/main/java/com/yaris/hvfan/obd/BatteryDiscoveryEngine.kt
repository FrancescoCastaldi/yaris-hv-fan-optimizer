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
 * 2. Rejected, timed-out, or invalid candidates enter a cooldown state (>= 30s)
 *    and the discovery cursor advances to the next candidate on subsequent cycles.
 * 3. The first candidate returning a valid, parseable battery payload is latched as
 *    activeBatteryPid, transitioning discovery to Discovered and ceasing further candidate probing.
 * 4. Maximum probe timeout bound <= 3000ms. Questo bound resta invariato: e' il timeout BLE
 *    esterno usato solo durante la fase di discovery (probing dei candidati), ben al di sopra
 *    del timeout ELM interno AT ST C8 (~819ms, Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU) usato per
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
        const val DEFAULT_COOLDOWN_MS = 30_000L
        const val MAX_PROBE_TIMEOUT_MS = 3000L
    }

    var latchedPid: String? = null
        private set

    val activeBatteryPid: String? get() = latchedPid

    val isDiscovered: Boolean get() = latchedPid != null

    private var cursorIndex: Int = 0
    private val cooldownMap = mutableMapOf<String, Long>()
    private val _probeOutcomes = mutableMapOf<String, ProbeResult>()
    val probeOutcomes: Map<String, ProbeResult> get() = _probeOutcomes.toMap()

    private var completedCycleCount: Int = 0

    /**
     * Numero di giri completi della fallback chain in cui OGNI candidato e' fallito senza mai
     * latchare una risposta valida. Si azzera su onCandidateSuccess() e reset().
     */
    val completedFailureCycles: Int get() = completedCycleCount

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
     * Called when a candidate probe fails (timeout, NO DATA, error, negative response, or unparseable).
     * Places the candidate into cooldown (>= 30s) and advances the discovery cursor to the next candidate.
     * Every time the cursor wraps back to the start of the candidate list, a full failed cycle of the
     * fallback chain has completed (see completedFailureCycles).
     */
    fun onCandidateFailed(pid: String, status: ProbeStatus = ProbeStatus.NO_DATA, rawResponse: String? = null) {
        val now = timeProvider()
        cooldownMap[pid] = now + cooldownDurationMs
        _probeOutcomes[pid] = ProbeResult(
            pid = pid,
            status = status,
            rawResponse = rawResponse,
            timestamp = now
        )
        // Advance cursor to next candidate after this one
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
        val now = timeProvider()
        val cooldownUntil = cooldownMap[pid] ?: return false
        return now < cooldownUntil
    }

    /**
     * Get remaining cooldown time in milliseconds for a candidate, or 0 if not in cooldown.
     */
    fun getRemainingCooldownMs(pid: String): Long {
        val now = timeProvider()
        val cooldownUntil = cooldownMap[pid] ?: return 0L
        return (cooldownUntil - now).coerceAtLeast(0L)
    }

    /**
     * Check if all candidates are currently in cooldown.
     */
    fun areAllCandidatesInCooldown(): Boolean {
        if (candidates.isEmpty()) return false
        return candidates.all { isCandidateInCooldown(it) }
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
    }
}
