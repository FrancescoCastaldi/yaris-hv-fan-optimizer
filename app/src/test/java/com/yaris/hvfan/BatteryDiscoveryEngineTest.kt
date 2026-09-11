package com.yaris.hvfan

import com.yaris.hvfan.obd.BatteryDiscoveryEngine
import com.yaris.hvfan.obd.ProbeStatus
import com.yaris.hvfan.obd.ToyotaYarisCommands
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying:
 * - VAL-OBD-003: Single Candidate Probe per Slow Polling Slice
 * - VAL-OBD-004: Stepped Candidate Progression with Cooldown Backoff
 * - VAL-OBD-005: Discovery Cache Lock on First Parseable Response
 */
class BatteryDiscoveryEngineTest {

    @Test
    fun testInitialStateAndCandidatePool() {
        val engine = BatteryDiscoveryEngine()
        assertNull(engine.latchedPid)
        assertFalse(engine.isDiscovered)
        assertEquals(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS, engine.candidates)
        assertEquals("2101", engine.getNextCandidate())
    }

    @Test
    fun testSteppedCandidateProgressionWithCooldown() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: First candidate is 2101
        val c1 = engine.getNextCandidate()
        assertEquals("2101", c1)
        // Candidate 1 fails (NO DATA)
        engine.onCandidateFailed("2101", ProbeStatus.NO_DATA, "NO DATA")
        assertTrue(engine.isCandidateInCooldown("2101"))
        assertEquals(5_000L, engine.getRemainingCooldownMs("2101"))

        // Cycle 2: Candidate advances to 21C3
        val c2 = engine.getNextCandidate()
        assertEquals("21C3", c2)
        // Candidate 2 fails (TIMEOUT)
        engine.onCandidateFailed("21C3", ProbeStatus.TIMEOUT, null)
        assertTrue(engine.isCandidateInCooldown("21C3"))

        // Cycle 3: Candidate advances to 21C4
        val c3 = engine.getNextCandidate()
        assertEquals("21C4", c3)
        // Candidate 3 fails (7F NRC)
        engine.onCandidateFailed("21C4", ProbeStatus.INVALID, "7F2122")
        assertTrue(engine.isCandidateInCooldown("21C4"))

        // Cycle 4: Candidate advances to 2161
        val c4 = engine.getNextCandidate()
        assertEquals("2161", c4)
        engine.onCandidateFailed("2161", ProbeStatus.INVALID, "GARBAGE")

        // Cycle 5: Candidate advances to 2228C1
        val c5 = engine.getNextCandidate()
        assertEquals("2228C1", c5)
        engine.onCandidateFailed("2228C1", ProbeStatus.NO_DATA, "NO DATA")

        // Cycle 6: Candidate advances to 2228C0
        val c6 = engine.getNextCandidate()
        assertEquals("2228C0", c6)
        engine.onCandidateFailed("2228C0", ProbeStatus.NO_DATA, "NO DATA")

        // Cycle 7: Candidate advances to 220101
        val c7 = engine.getNextCandidate()
        assertEquals("220101", c7)
        engine.onCandidateFailed("220101", ProbeStatus.NO_DATA, "NO DATA")

        // Now all 7 candidates have failed and are in cooldown!
        assertTrue(engine.areAllCandidatesInCooldown())
        assertNull(engine.getNextCandidate())

        // Advance time by 3s (still within 5s cooldown for 2101)
        currentTime += 3_000L
        assertTrue(engine.isCandidateInCooldown("2101"))
        assertNull(engine.getNextCandidate())

        // Advance time past 5s from initial failure of 2101
        currentTime += 3_000L // 6s elapsed
        assertFalse(engine.isCandidateInCooldown("2101"))
        // 2101 is eligible again!
        assertEquals("2101", engine.getNextCandidate())
    }

    @Test
    fun testFirstValidResponseLatchesActivePidAndCeasesProbing() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: 2101 fails
        val c1 = engine.getNextCandidate()
        assertEquals("2101", c1)
        engine.onCandidateFailed(c1!!, ProbeStatus.NO_DATA)

        // Cycle 2: 21C3 responds with valid payload
        val c2 = engine.getNextCandidate()
        assertEquals("21C3", c2)
        val validPayload = "61C33C3D3C3C3206"
        engine.onCandidateSuccess(c2!!, validPayload)

        // Verifications for VAL-OBD-005
        assertTrue(engine.isDiscovered)
        assertEquals("21C3", engine.latchedPid)
        // Candidate probing ceases
        assertNull(engine.getNextCandidate())

        val outcomes = engine.probeOutcomes
        assertEquals(ProbeStatus.NO_DATA, outcomes["2101"]?.status)
        assertEquals(ProbeStatus.SUCCESS, outcomes["21C3"]?.status)
        assertEquals(validPayload, outcomes["21C3"]?.rawResponse)
    }

    @Test
    fun testResetClearsLatchedAndCooldowns() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        engine.onCandidateSuccess("2101", "6101...")
        assertTrue(engine.isDiscovered)
        assertEquals("2101", engine.latchedPid)

        engine.reset()
        assertFalse(engine.isDiscovered)
        assertNull(engine.latchedPid)
        assertEquals(0, engine.probeOutcomes.size)
        assertEquals("2101", engine.getNextCandidate())
    }

    /**
     * Copre il nuovo tracking dei giri completi falliti (completedFailureCycles), usato dal
     * chiamante per distinguere un singolo NODATA transitorio da un pattern persistente
     * (probabile limite hardware dell'adapter OBD su cloni ELM327/Vlinker).
     */
    @Test
    fun testCompletedFailureCyclesIncrementOnFullChainFailureAndResetOnSuccess() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        fun failAllCandidatesOnce() {
            repeat(engine.candidates.size) {
                val candidate = engine.getNextCandidate()
                assertNotNull(candidate)
                engine.onCandidateFailed(candidate!!, ProbeStatus.NO_DATA, "NO DATA")
            }
        }

        // No failure cycle completed yet
        assertEquals(0, engine.completedFailureCycles)

        // First full lap through the fallback chain: all 6 candidates fail
        failAllCandidatesOnce()
        assertEquals(1, engine.completedFailureCycles)
        assertTrue(engine.areAllCandidatesInCooldown())

        // Advance past cooldown and run a second full failed lap
        currentTime += 6_000L
        failAllCandidatesOnce()
        assertEquals(2, engine.completedFailureCycles)

        // A successful candidate resets the failure cycle counter
        currentTime += 6_000L
        val candidate = engine.getNextCandidate()
        assertNotNull(candidate)
        engine.onCandidateSuccess(candidate!!, "6228C1...")
        assertEquals(0, engine.completedFailureCycles)

        // reset() also clears the counter
        engine.reset()
        assertEquals(0, engine.completedFailureCycles)
    }

    @Test
    fun testPermanentRejectionOnNrcServiceNotSupported() {
        val engine = BatteryDiscoveryEngine()
        assertEquals("2101", engine.getNextCandidate())

        // Simulazione rifiuto UDS NRC 11 (ServiceNotSupported) o 12 (SubFunctionNotSupported)
        engine.onCandidateRejected("2101", "7F2111")
        assertTrue(engine.isPidRejected("2101"))
        assertFalse(engine.isCandidateInCooldown("2101"))
        assertEquals(0L, engine.getRemainingCooldownMs("2101"))

        // Il cursore avanza al candidato successivo
        val next = engine.getNextCandidate()
        assertEquals("21C3", next)

        // Anche dopo reset dei cooldown, 2101 resta escluso e non viene più riproposto
        assertNotEquals("2101", engine.getNextCandidate())

        // Il reset completo ripristina anche i PID rifiutati
        engine.reset()
        assertFalse(engine.isPidRejected("2101"))
        assertEquals("2101", engine.getNextCandidate())
    }

    @Test
    fun testRapidFailuresCooldownDurationAndFastRecovery() {
        var currentTime = 50_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Fallimento rapido durante handshake (tutti e 7 i candidati falliscono in 700ms)
        for (i in 0 until engine.candidates.size) {
            val candidate = engine.getNextCandidate()
            assertNotNull(candidate)
            engine.onCandidateFailed(candidate!!, ProbeStatus.NO_DATA, "NO DATA")
            currentTime += 100L // 100ms tra i candidati
        }

        // Tutti i candidati sono in cooldown (700ms trascorsi < 5000ms)
        assertTrue(engine.areAllCandidatesInCooldown())
        assertNull(engine.getNextCandidate())

        // Avanziamo a 4900ms dal primo fallimento (4200ms aggiuntivi): ancora in cooldown
        currentTime += 4200L
        assertTrue(engine.isCandidateInCooldown("2101"))
        assertNull(engine.getNextCandidate())

        // A 5100ms dal primo fallimento: 2101 esce dal cooldown e torna immediatamente eleggibile (recupero veloce in 5s anziché 30s)
        currentTime += 200L
        assertFalse(engine.isCandidateInCooldown("2101"))
        assertEquals("2101", engine.getNextCandidate())
    }
}
