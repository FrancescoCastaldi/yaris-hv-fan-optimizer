package com.yaris.hvfan

import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ObdControllerBatteryDiscoveryTest {

    private class FakeObdTransport : ObdTransport {
        private val _connectionState = MutableStateFlow<BleConnectionState>(
            BleConnectionState.Connected("OBDLink CX", "00:11:22:33:44:55")
        )
        override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        val dispatchedCommands = mutableListOf<String>()
        val commandTimeouts = mutableListOf<Long>()
        var commandResponder: suspend (command: String, timeoutMs: Long) -> String = { _, _ -> "OK" }

        override fun getConnectedDeviceName(): String = "OBDLink CX"

        override suspend fun sendWakeSequence() {
            dispatchedCommands.add("WAKE_SEQUENCE")
        }

        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            dispatchedCommands.add(command)
            commandTimeouts.add(timeoutMs)
            return commandResponder(command, timeoutMs)
        }
    }

    /**
     * VAL-OBD-003: Exactly ONE candidate PID probed per slow cycle slice (3500ms).
     * Replacing serial probing of up to 6 candidates with exactly ONE candidate.
     */
    @Test
    fun testValObd003_exactlyOneCandidateProbedPerSlowCycle() = runTest {
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd.startsWith("AT") -> "OK"
                cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
                ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd) -> "NO DATA"
                else -> "OK"
            }
        }

        val stateMachine = ObdStateMachine()
        val engine = BatteryDiscoveryEngine()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = stateMachine,
            discoveryEngine = engine
        )

        // Execute exactly ONE slow cycle
        controller.executeBatteryThermalCycle()

        // Filter commands sent to see candidate queries
        val probedCandidates = fakeTransport.dispatchedCommands.filter { cmd ->
            ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)
        }

        assertEquals(
            "Exactly one candidate PID must be probed in a single slow cycle, not serial probing",
            1,
            probedCandidates.size
        )
        assertEquals(
            "First candidate should be BATTERY_FALLBACK_PIDS[0]",
            ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0],
            probedCandidates[0]
        )
    }

    /**
     * VAL-OBD-004: Stepped candidate progression with cooldown backoff (>= 30s).
     * Failed candidates enter cooldown and cursor advances to next candidate on subsequent cycles.
     */
    @Test
    fun testValObd004_steppedCandidateProgressionWithCooldownBackoff() = runTest {
        var currentTime = 100_000L
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd.startsWith("AT") -> "OK"
                cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
                ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd) -> "NO DATA"
                else -> "OK"
            }
        }

        val stateMachine = ObdStateMachine()
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = stateMachine,
            discoveryEngine = engine
        )

        val probedAcrossCycles = mutableListOf<String>()

        // Simulate 3 consecutive slow cycles (each spaced by 3500ms)
        for (cycle in 1..3) {
            fakeTransport.dispatchedCommands.clear()
            controller.executeBatteryThermalCycle()

            val candidatesInThisCycle = fakeTransport.dispatchedCommands.filter {
                ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(it)
            }
            assertEquals(1, candidatesInThisCycle.size)
            probedAcrossCycles.add(candidatesInThisCycle[0])

            currentTime += 3500L
        }

        // Verify progression: candidate 0 -> candidate 1 -> candidate 2
        assertEquals(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0], probedAcrossCycles[0])
        assertEquals(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[1], probedAcrossCycles[1])
        assertEquals(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[2], probedAcrossCycles[2])

        // Verify candidate 0 is still in cooldown and was NOT re-probed at cycle 2 or 3
        assertTrue(engine.isCandidateInCooldown(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0]))
        assertTrue(engine.isCandidateInCooldown(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[1]))
    }

    /**
     * VAL-OBD-005: Discovery cache lock on first parseable response.
     * The first valid response latches candidate as activeBatteryPid and ceases further discovery probing.
     */
    @Test
    fun testValObd005_discoveryCacheLockOnFirstParseableResponse() = runTest {
        val validPid = ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA // "2228C1"
        val sampleBatteryResponse = "7EA 21 00 62 28 C1 1B 00 9C 00 00 00 00 00 00 00 1E 20 22 21 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"

        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd.startsWith("AT") -> "OK"
                cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
                cmd == validPid -> sampleBatteryResponse
                else -> "NO DATA"
            }
        }

        val stateMachine = ObdStateMachine()
        val engine = BatteryDiscoveryEngine()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = stateMachine,
            discoveryEngine = engine
        )

        // Cycle 1: First candidate succeeds
        controller.executeBatteryThermalCycle()

        assertTrue("Engine must report discovered", engine.isDiscovered)
        assertEquals("Latched PID must match valid candidate", validPid, engine.latchedPid)
        assertEquals("Active battery PID must match latched PID", validPid, controller.activeBatteryPid)
        assertEquals(
            "State machine must be in Discovered state",
            BatteryEcuDiscoveryState.Discovered,
            stateMachine.currentCapabilityState.batteryEcuDiscoveryState
        )

        // Cycle 2: Subsequent cycle queries latched PID only, ceases candidate discovery probing
        fakeTransport.dispatchedCommands.clear()
        controller.executeBatteryThermalCycle()

        val queriesInCycle2 = fakeTransport.dispatchedCommands.filter {
            ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(it)
        }
        assertEquals("Subsequent cycle should query exactly 1 PID", 1, queriesInCycle2.size)
        assertEquals("Subsequent cycle should query latched PID only", validPid, queriesInCycle2[0])
    }

    /**
     * VAL-OBD-006: Invariant CAN header restoration via guaranteed finally blocks.
     * Any code path modifying the CAN transmit header to 7E2 guarantees restoration to
     * standard Engine (7E0) or Functional Broadcast (7DF) via try/finally blocks even under
     * timeouts or exceptions.
     */
    @Test
    fun testValObd006_headerRestorationGuaranteedUnderNormalExecution() = runTest {
        val fakeTransport = FakeObdTransport()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        controller.executeBatteryThermalCycle()

        // Verify header was switched to 7E2 and restored to 7E0 at the end of the thermal cycle
        val atShCommands = fakeTransport.dispatchedCommands.filter { it.startsWith("AT SH") }
        assertTrue("Must include AT SH 7E2", atShCommands.contains("AT SH 7E2"))
        assertEquals("Final header switch must restore 7E0", "AT SH 7E0", atShCommands.last())
        assertEquals("Controller currentCanHeader should be 7E0", ToyotaYarisCommands.HEADER_ENGINE_ECU, controller.currentCanHeader)
    }

    @Test
    fun testValObd006_headerRestorationGuaranteedUnderException() = runTest {
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            if (cmd.startsWith("AT SH 7E2")) {
                "OK"
            } else if (cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT) {
                throw IOException("Simulated BLE communication failure on 7E2")
            } else {
                "OK"
            }
        }

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        try {
            controller.executeBatteryThermalCycle()
        } catch (_: Exception) {
            // Expected
        }

        // Verify that despite the exception during 7E2 transmission, 7E0 header was restored in finally block
        val lastAtSh = fakeTransport.dispatchedCommands.lastOrNull { it.startsWith("AT SH") }
        assertEquals("Finally block must guarantee restoration of header 7E0", "AT SH 7E0", lastAtSh)
    }

    /**
     * VAL-OBD-011: Strict timeout bounding for discovery candidate probes.
     * Candidate evaluation timeout upper bound <= 3000ms enforced cleanly without hanging scheduler.
     */
    @Test
    fun testValObd011_candidateEvaluationTimeoutBoundedTo3000ms() = runTest {
        val fakeTransport = FakeObdTransport()
        val candidateTimeouts = mutableListOf<Long>()

        fakeTransport.commandResponder = { cmd, timeoutMs ->
            if (ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)) {
                candidateTimeouts.add(timeoutMs)
            }
            "OK"
        }

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        controller.executeBatteryThermalCycle()

        assertEquals("Must have probed one candidate", 1, candidateTimeouts.size)
        assertTrue(
            "Candidate probe timeout bound must be <= 3000ms, was ${candidateTimeouts[0]}ms",
            candidateTimeouts[0] <= 3000L
        )
    }

    @Test
    fun testValObd011_hangingCandidateProbeCompletesCleanlyWithinTimeout() = runTest {
        val fakeTransport = FakeObdTransport()
        var callExecuted = false

        fakeTransport.commandResponder = { cmd, _ ->
            if (ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)) {
                callExecuted = true
                // Simulate delay exceeding 3000ms
                delay(5000L)
                "7EA 03 7F 22 11"
            } else {
                "OK"
            }
        }

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        // In virtual test time, withTimeoutOrNull(3000L) will cancel the hanging call
        controller.executeBatteryThermalCycle()

        assertTrue("Candidate probe was attempted", callExecuted)
        assertFalse("Engine should not be discovered on timed-out candidate", controller.discoveryEngine.isDiscovered)
        // Verify header restoration was still executed
        assertEquals(ToyotaYarisCommands.HEADER_ENGINE_ECU, controller.currentCanHeader)
    }

    /**
     * Copre l'aumento del timeout ELM interno AT ST per il multi-frame UDS 2228C1: deve essere
     * inviato ogni volta che l'header CAN passa a 7E2 (batteria), e deve restare ben al di sotto
     * dei timeout BLE esterni (MAX_PROBE_TIMEOUT_MS discovery / BATTERY_PID_TIMEOUT_MS steady-state).
     */
    @Test
    fun testBatteryEcuHeaderSwitchAppliesConservativeIsoTpTimeout() = runTest {
        val fakeTransport = FakeObdTransport()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        controller.executeBatteryThermalCycle()

        assertEquals("AT ST C8", Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU)
        assertTrue(
            "Must apply the conservative AT ST C8 (~819ms) timeout before probing battery ECU 7E2",
            fakeTransport.dispatchedCommands.contains(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU)
        )
    }

    /**
     * Copre il nuovo alert distinto "probabile limite hardware dell'adapter OBD": deve attivarsi
     * solo dopo che l'intera fallback chain e' stata tentata senza successo per >= 2 giri completi
     * consecutivi (tutti i candidati in cooldown), e NON prima.
     */
    @Test
    fun testHardwareLimitationWarningActivatesAfterRepeatedFullFailureCycles() = runTest {
        var currentTime = 100_000L
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd.startsWith("AT") -> "OK"
                cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
                ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd) -> "NO DATA"
                else -> "OK"
            }
        }

        val stateMachine = ObdStateMachine()
        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = stateMachine,
            discoveryEngine = engine
        )

        // Lap 1: all 6 fallback candidates fail once (each enters 30s cooldown)
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            currentTime += 3500L
        }
        assertEquals(1, engine.completedFailureCycles)
        assertNull(
            "Warning must stay null before the failure-cycle threshold is reached",
            controller.liveState.value.batteryAdapterLimitationWarning
        )

        // Advance exactly past the first candidate's cooldown expiry to unblock lap 2
        currentTime = 130_000L

        // Lap 2: all 6 candidates fail again -> completes the 2nd full failure cycle
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            currentTime += 3500L
        }
        assertEquals(2, engine.completedFailureCycles)
        assertTrue(engine.areAllCandidatesInCooldown())

        val warning = controller.liveState.value.batteryAdapterLimitationWarning
        assertNotNull("Warning must activate after repeated full failure cycles", warning)
        assertTrue("Warning must point the user at STN11xx/STN21xx hardware", warning!!.contains("STN"))
    }
}

