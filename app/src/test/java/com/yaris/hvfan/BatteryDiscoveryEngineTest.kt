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
        assertEquals(30_000L, engine.getRemainingCooldownMs("2101"))

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
        engine.onCandidateFailed("21C4", ProbeStatus.REJECTED, "7F2111")
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

        // Advance time by 15s (still within 30s cooldown for 2101)
        currentTime += 15_000L
        assertTrue(engine.isCandidateInCooldown("2101"))
        assertNull(engine.getNextCandidate())

        // Advance time past 30s from initial failure of 2101
        currentTime += 16_000L // 31s elapsed
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
        currentTime += 31_000L
        failAllCandidatesOnce()
        assertEquals(2, engine.completedFailureCycles)

        // A successful candidate resets the failure cycle counter
        currentTime += 31_000L
        val candidate = engine.getNextCandidate()
        assertNotNull(candidate)
        engine.onCandidateSuccess(candidate!!, "6228C1...")
        assertEquals(0, engine.completedFailureCycles)

        // reset() also clears the counter
        engine.reset()
        assertEquals(0, engine.completedFailureCycles)
    }
}
