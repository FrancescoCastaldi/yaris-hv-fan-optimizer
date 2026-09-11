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
        assertEquals("2187", engine.getNextCandidate())
    }

    @Test
    fun testSteppedCandidateProgressionWithCooldown() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: First candidate is 2187
        val c1 = engine.getNextCandidate()
        assertEquals("2187", c1)
        engine.onCandidateFailed("2187", ProbeStatus.NO_DATA, "NO DATA")
        assertTrue(engine.isCandidateInCooldown("2187"))
        assertEquals(5_000L, engine.getRemainingCooldownMs("2187"))

        // Cycle 2: Candidate advances to 21CE
        val c2 = engine.getNextCandidate()
        assertEquals("21CE", c2)
        engine.onCandidateFailed("21CE", ProbeStatus.TIMEOUT, null)
        assertTrue(engine.isCandidateInCooldown("21CE"))

        // Cycle 3: Candidate advances to 2101
        val c3 = engine.getNextCandidate()
        assertEquals("2101", c3)
        engine.onCandidateFailed("2101", ProbeStatus.NO_DATA, "NO DATA")
        assertTrue(engine.isCandidateInCooldown("2101"))

        // Cycle 4: Candidate advances to 21C3
        val c4 = engine.getNextCandidate()
        assertEquals("21C3", c4)
        engine.onCandidateFailed("21C3", ProbeStatus.TIMEOUT, null)
        assertTrue(engine.isCandidateInCooldown("21C3"))

        // Cycle 5: Candidate advances to 21C4
        val c5 = engine.getNextCandidate()
        assertEquals("21C4", c5)
        engine.onCandidateFailed("21C4", ProbeStatus.INVALID, "7F2122")
        assertTrue(engine.isCandidateInCooldown("21C4"))

        // Cycle 6: Candidate advances to 2161
        val c6 = engine.getNextCandidate()
        assertEquals("2161", c6)
        engine.onCandidateFailed("2161", ProbeStatus.INVALID, "GARBAGE")

        // Cycle 7: Candidate advances to 2228C1
        val c7 = engine.getNextCandidate()
        assertEquals("2228C1", c7)
        engine.onCandidateFailed("2228C1", ProbeStatus.NO_DATA, "NO DATA")

        // Cycle 8: Candidate advances to 2228C0
        val c8 = engine.getNextCandidate()
        assertEquals("2228C0", c8)
        engine.onCandidateFailed("2228C0", ProbeStatus.NO_DATA, "NO DATA")

        // Cycle 9: Candidate advances to 220101
        val c9 = engine.getNextCandidate()
        assertEquals("220101", c9)
        engine.onCandidateFailed("220101", ProbeStatus.NO_DATA, "NO DATA")

        // Now all 9 candidates have failed and are in cooldown!
        assertTrue(engine.areAllCandidatesInCooldown())
        assertNull(engine.getNextCandidate())

        // Advance time by 3s (still within 5s cooldown for 2187)
        currentTime += 3_000L
        assertTrue(engine.isCandidateInCooldown("2187"))
        assertNull(engine.getNextCandidate())

        // Advance time past 5s from initial failure of 2187
        currentTime += 3_000L // 6s elapsed
        assertFalse(engine.isCandidateInCooldown("2187"))
        // 2187 is eligible again!
        assertEquals("2187", engine.getNextCandidate())
    }

    @Test
    fun testFirstValidResponseLatchesActivePidAndCeasesProbing() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: 2187 fails
        val c1 = engine.getNextCandidate()
        assertEquals("2187", c1)
        engine.onCandidateFailed(c1!!, ProbeStatus.NO_DATA)

        // Cycle 2: 21CE responds with valid payload
        val c2 = engine.getNextCandidate()
        assertEquals("21CE", c2)
        val validPayload = "61CE3C3D3C3C3206"
        engine.onCandidateSuccess(c2!!, validPayload)

        // Verifications for VAL-OBD-005
        assertTrue(engine.isDiscovered)
        assertEquals("21CE", engine.latchedPid)
        // Candidate probing ceases
        assertNull(engine.getNextCandidate())

        val outcomes = engine.probeOutcomes
        assertEquals(ProbeStatus.NO_DATA, outcomes["2187"]?.status)
        assertEquals(ProbeStatus.SUCCESS, outcomes["21CE"]?.status)
        assertEquals(validPayload, outcomes["21CE"]?.rawResponse)
    }

    @Test
    fun testResetClearsLatchedAndCooldowns() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        engine.onCandidateSuccess("2187", "6187...")
        assertTrue(engine.isDiscovered)
        assertEquals("2187", engine.latchedPid)

        engine.reset()
        assertFalse(engine.isDiscovered)
        assertNull(engine.latchedPid)
        assertEquals(0, engine.probeOutcomes.size)
        assertEquals("2187", engine.getNextCandidate())
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

        // First full lap through the fallback chain
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
        assertEquals("2187", engine.getNextCandidate())

        // Simulazione rifiuto UDS NRC 11 (ServiceNotSupported) o 12 (SubFunctionNotSupported)
        engine.onCandidateRejected("2187", "7F2111")
        assertTrue(engine.isPidRejected("2187"))
        assertFalse(engine.isCandidateInCooldown("2187"))
        assertEquals(0L, engine.getRemainingCooldownMs("2187"))

        // Il cursore avanza al candidato successivo
        val next = engine.getNextCandidate()
        assertEquals("21CE", next)

        // Anche dopo reset dei cooldown, 2187 resta escluso e non viene più riproposto
        assertNotEquals("2187", engine.getNextCandidate())

        // Il reset completo ripristina anche i PID rifiutati
        engine.reset()
        assertFalse(engine.isPidRejected("2187"))
        assertEquals("2187", engine.getNextCandidate())
    }

    @Test
    fun testRapidFailuresCooldownDurationAndFastRecovery() {
        var currentTime = 50_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Fallimento rapido durante handshake (tutti i candidati falliscono in sequenza)
        for (i in 0 until engine.candidates.size) {
            val candidate = engine.getNextCandidate()
            assertNotNull(candidate)
            engine.onCandidateFailed(candidate!!, ProbeStatus.NO_DATA, "NO DATA")
            currentTime += 100L // 100ms tra i candidati
        }

        // Tutti i candidati sono in cooldown
        assertTrue(engine.areAllCandidatesInCooldown())
        assertNull(engine.getNextCandidate())

        // Avanziamo a 4900ms dal primo fallimento (4000ms aggiuntivi): ancora in cooldown
        currentTime += 4000L
        assertTrue(engine.isCandidateInCooldown("2187"))
        assertNull(engine.getNextCandidate())

        // A 5100ms dal primo fallimento: 2187 esce dal cooldown e torna immediatamente eleggibile
        currentTime += 400L
        assertFalse(engine.isCandidateInCooldown("2187"))
        assertEquals("2187", engine.getNextCandidate())
    }
}
