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
        assertEquals(ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA, engine.getNextCandidate())
    }

    @Test
    fun testSteppedCandidateProgressionWithCooldown() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: First candidate is 2228C1
        val c1 = engine.getNextCandidate()
        assertEquals("2228C1", c1)
        // Candidate 1 fails (NO DATA)
        engine.onCandidateFailed("2228C1", ProbeStatus.NO_DATA, "NO DATA")
        assertTrue(engine.isCandidateInCooldown("2228C1"))
        assertEquals(30_000L, engine.getRemainingCooldownMs("2228C1"))

        // Cycle 2: Candidate advances to 2228C0
        val c2 = engine.getNextCandidate()
        assertEquals("2228C0", c2)
        // Candidate 2 fails (TIMEOUT)
        engine.onCandidateFailed("2228C0", ProbeStatus.TIMEOUT, null)
        assertTrue(engine.isCandidateInCooldown("2228C0"))

        // Cycle 3: Candidate advances to 220101
        val c3 = engine.getNextCandidate()
        assertEquals("220101", c3)
        // Candidate 3 fails (7F NRC)
        engine.onCandidateFailed("220101", ProbeStatus.REJECTED, "7F2211")
        assertTrue(engine.isCandidateInCooldown("220101"))

        // Cycle 4: Candidate advances to 2101
        val c4 = engine.getNextCandidate()
        assertEquals("2101", c4)
        engine.onCandidateFailed("2101", ProbeStatus.NO_DATA, "NO DATA")

        // Cycle 5: Candidate advances to 21C3
        val c5 = engine.getNextCandidate()
        assertEquals("21C3", c5)
        engine.onCandidateFailed("21C3", ProbeStatus.INVALID, "GARBAGE")

        // Cycle 6: Candidate advances to 2161
        val c6 = engine.getNextCandidate()
        assertEquals("2161", c6)
        engine.onCandidateFailed("2161", ProbeStatus.NO_DATA, "NO DATA")

        // Now all 6 candidates have failed and are in cooldown!
        assertTrue(engine.areAllCandidatesInCooldown())
        assertNull(engine.getNextCandidate())

        // Advance time by 15s (still within 30s cooldown for 2228C1)
        currentTime += 15_000L
        assertTrue(engine.isCandidateInCooldown("2228C1"))
        assertNull(engine.getNextCandidate())

        // Advance time past 30s from initial failure of 2228C1
        currentTime += 16_000L // 31s elapsed
        assertFalse(engine.isCandidateInCooldown("2228C1"))
        // 2228C1 is eligible again!
        assertEquals("2228C1", engine.getNextCandidate())
    }

    @Test
    fun testFirstValidResponseLatchesActivePidAndCeasesProbing() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        // Cycle 1: 2228C1 fails
        val c1 = engine.getNextCandidate()
        assertEquals("2228C1", c1)
        engine.onCandidateFailed(c1!!, ProbeStatus.NO_DATA)

        // Cycle 2: 2228C0 responds with valid payload
        val c2 = engine.getNextCandidate()
        assertEquals("2228C0", c2)
        val validPayload = "6228C03C3D3C3C3206"
        engine.onCandidateSuccess(c2!!, validPayload)

        // Verifications for VAL-OBD-005
        assertTrue(engine.isDiscovered)
        assertEquals("2228C0", engine.latchedPid)
        // Candidate probing ceases
        assertNull(engine.getNextCandidate())

        val outcomes = engine.probeOutcomes
        assertEquals(ProbeStatus.NO_DATA, outcomes["2228C1"]?.status)
        assertEquals(ProbeStatus.SUCCESS, outcomes["2228C0"]?.status)
        assertEquals(validPayload, outcomes["2228C0"]?.rawResponse)
    }

    @Test
    fun testResetClearsLatchedAndCooldowns() {
        var currentTime = 100_000L
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })

        engine.onCandidateSuccess("2228C1", "6228C1...")
        assertTrue(engine.isDiscovered)
        assertEquals("2228C1", engine.latchedPid)

        engine.reset()
        assertFalse(engine.isDiscovered)
        assertNull(engine.latchedPid)
        assertEquals(0, engine.probeOutcomes.size)
        assertEquals("2228C1", engine.getNextCandidate())
    }
}
