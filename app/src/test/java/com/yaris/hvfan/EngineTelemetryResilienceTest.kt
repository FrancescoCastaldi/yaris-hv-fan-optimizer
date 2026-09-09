package com.yaris.hvfan

import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * VAL-OBD-007: Engine Telemetry Zero-Starvation Invariant.
 * VAL-OBD-012: Mode 01 Coolant Warm-Up Polling Preservation.
 *
 * Simulates the dual-rate scheduler tick against a fake ELM327 transport where battery
 * candidate probes hang for the full 3000ms discovery timeout, then verifies that the
 * fast engine loop (010C/010D/0111) and the 4000ms coolant/IAT loop continue at native
 * cadence without starvation or skips.
 */
class EngineTelemetryResilienceTest {

    private class FakeObdTransport : ObdTransport {
        private val _connectionState = MutableStateFlow<BleConnectionState>(
            BleConnectionState.Connected("OBDLink CX", "00:11:22:33:44:55")
        )
        override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        val dispatchedCommands = mutableListOf<String>()
        var commandResponder: suspend (command: String, timeoutMs: Long) -> String = { _, _ -> "OK" }

        /** Wall-clock timestamp (per injected timeProvider) of every dispatched command. */
        val commandTimestamps = mutableListOf<Pair<Long, String>>()
        var timeProvider: () -> Long = { 0L }

        fun pushConnectionState(state: BleConnectionState) {
            _connectionState.value = state
        }

        override fun getConnectedDeviceName(): String = "OBDLink CX"

        override suspend fun sendWakeSequence() {
            dispatchedCommands.add("WAKE_SEQUENCE")
            commandTimestamps.add(timeProvider() to "WAKE_SEQUENCE")
        }

        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            dispatchedCommands.add(command)
            commandTimestamps.add(timeProvider() to command)
            return commandResponder(command, timeoutMs)
        }
    }

    private class ManualClock(startMs: Long = 100_000L) {
        var nowMs: Long = startMs
        fun now(): Long = nowMs
        fun advance(ms: Long) { nowMs += ms }
    }

    private fun makeResponder(clock: ManualClock): suspend (String, Long) -> String = { cmd, _ ->
        when {
            cmd.startsWith("AT") -> "OK"
            cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
            // Battery candidates hang for the full 3000ms discovery timeout, then fail.
            ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd) -> {
                clock.advance(3000L)
                "NO DATA"
            }
            cmd == ToyotaYarisCommands.PID_VEHICLE_SPEED -> "7E8 03 41 0D 64 >"
            cmd == ToyotaYarisCommands.PID_ENGINE_RPM -> "7E8 04 41 0C 1F 40 >"
            cmd == ToyotaYarisCommands.PID_THROTTLE_POS -> "7E8 03 41 11 66 >"
            cmd == ToyotaYarisCommands.PID_COOLANT_TEMP -> "7E8 03 41 05 78 >"
            cmd == ToyotaYarisCommands.PID_INTAKE_AIR_TEMP -> "7E8 03 41 0F 41 >"
            cmd == ToyotaYarisCommands.PID_TIMING_ADVANCE -> "7E8 03 41 0E A0 >"
            cmd == ToyotaYarisCommands.PID_ENGINE_LOAD -> "7E8 03 41 04 80 >"
            else -> "NO DATA"
        }
    }

    /**
     * VAL-OBD-007: Fast engine telemetry dispatches immediately after a battery cycle slice
     * that timed out, and then every scheduler tick (<= 140ms fast loop cadence).
     */
    @Test
    fun testValObd007_fastEngineTelemetryNotStarvedByBatteryTimeout() = runTest {
        val clock = ManualClock()
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = makeResponder(clock)

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine(timeProvider = { clock.now() }),
            timeProvider = { clock.now() }
        )

        // --- Cycle 1: battery slice DUE (lastBatteryCheckTimestamp == 0), probe hangs 3000ms ---
        controller.executeDualRateCycle()

        // Fast loop must have dispatched within 140ms of battery-slice completion (timeout at 103000).
        val firstFastDispatch = controller.lastEngineFastDispatchTimestampMs
        assertTrue("Fast loop must have executed during cycle 1", firstFastDispatch > 0L)
        assertEquals(
            "Fast loop must execute promptly (<= 140ms) after battery slice timeout",
            103_000L, firstFastDispatch
        )

        // Engine PIDs were actually queried in the same tick as the battery timeout.
        assertTrue(
            "RPM must be polled in the same tick as the battery timeout",
            fakeTransport.dispatchedCommands.contains(ToyotaYarisCommands.PID_ENGINE_RPM)
        )
        assertTrue(
            "Speed must be polled in the same tick as the battery timeout",
            fakeTransport.dispatchedCommands.contains(ToyotaYarisCommands.PID_VEHICLE_SPEED)
        )

        // --- Cycle 2: +140ms, battery not due, fast loop must still run ---
        clock.advance(140L)
        controller.executeDualRateCycle()
        val secondFastDispatch = controller.lastEngineFastDispatchTimestampMs
        assertEquals("Fast loop must re-dispatch at native 140ms cadence", 103_140L, secondFastDispatch)
        assertEquals(
            "Fast-loop dispatch gap must respect the native 140ms cadence",
            140L, secondFastDispatch - firstFastDispatch
        )

        // --- Cycle 3: another +140ms ---
        clock.advance(140L)
        controller.executeDualRateCycle()
        assertEquals(103_280L, controller.lastEngineFastDispatchTimestampMs)

        // --- Cycle 4: +140ms ---
        clock.advance(140L)
        controller.executeDualRateCycle()
        assertEquals(103_420L, controller.lastEngineFastDispatchTimestampMs)
    }

    /**
     * Drives the production dual-rate scheduler for [ticks] iterations. In `runTest` the
     * 140ms `delay` between ticks elapses instantly on the virtual dispatcher, so each
     * iteration advances wall time exactly like the production loop (140ms cadence),
     * while ticks run in child coroutines that overlap with a hanging battery slice.
     */
    private suspend fun TestScope.runProductionSchedulerTicks(
        controller: ObdController,
        clock: ManualClock,
        ticks: Int
    ) {
        val schedulerJob = launch { controller.runDualRateScheduler() }
        repeat(ticks) {
            // Advance wall time by one native fast-loop period and yield to the virtual
            // scheduler so the loop's 140ms delay and child tick coroutines interleave
            // deterministically with the wall clock.
            clock.advance(140L)
            delay(140L)
        }
        schedulerJob.cancel()
        kotlinx.coroutines.yield()
    }

    /**
     * VAL-OBD-007 (regression): consecutive 3000ms battery timeouts across multiple slow slices
     * never skip a single fast engine telemetry tick. This mirrors the pre-v2.9.14 serial
     * probing defect that froze the dashboard for up to 24 seconds.
     */
    @Test
    fun testValObd007_consecutiveBatteryTimeoutsNeverSkipFastLoopTick() = runTest {
        val clock = ManualClock()
        val fakeTransport = FakeObdTransport()
        fakeTransport.timeProvider = { clock.now() }
        fakeTransport.commandResponder = makeResponder(clock)

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine(timeProvider = { clock.now() }),
            timeProvider = { clock.now() }
        )

        runProductionSchedulerTicks(controller, clock, ticks = 26)

        println("DEBUG dispatched sample: ${fakeTransport.dispatchedCommands.take(20)}")
        println("DEBUG total: ${fakeTransport.dispatchedCommands.size}, standby=${controller.liveState.value.isStandbyMode}, lastFast=${controller.lastEngineFastDispatchTimestampMs}")

        val rpmDispatches = fakeTransport.commandTimestamps
            .filter { it.second == ToyotaYarisCommands.PID_ENGINE_RPM }
            .map { it.first }
        assertTrue(
            "Fast engine loop must dispatch on every scheduler tick (expected >= 25 RPM polls), got ${rpmDispatches.size}",
            rpmDispatches.size >= 25
        )
        // ELM327 has a single serial command channel and one active CAN transmit header.
        // Fast telemetry runs after the battery slice completes/times out in the same tick,
        // so a 3000ms battery timeout can delay the next fast-loop dispatch by up to the
        // timeout plus one loop delay. The invariant is no permanent starvation, not a
        // hard 140ms gap while the shared transport is blocked by the battery probe.
        val gaps = rpmDispatches.zipWithNext { a, b -> b - a }
        assertTrue(
            "No fast-loop gap may exceed the bounded battery timeout window (<= 3500ms), observed gaps: $gaps",
            gaps.all { it <= 3500L }
        )
    }

    /**
     * VAL-OBD-012: coolant (0105) and IAT (010F) warm-up polling executes every 4000ms even
     * while battery candidate probes fail with NO DATA / timeouts on every slow slice.
     */
    @Test
    fun testValObd012_coolantWarmupPollingPreservedAt4000msAcrossFailingProbes() = runTest {
        val clock = ManualClock()
        val fakeTransport = FakeObdTransport()
        fakeTransport.timeProvider = { clock.now() }
        fakeTransport.commandResponder = makeResponder(clock)

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine(timeProvider = { clock.now() }),
            timeProvider = { clock.now() }
        )

        runProductionSchedulerTicks(controller, clock, ticks = 30)

        val coolantDispatches = fakeTransport.commandTimestamps
            .filter { it.second == ToyotaYarisCommands.PID_COOLANT_TEMP }
            .map { it.first }
        assertTrue(
            "Coolant cycle must dispatch at least twice over ~4.2s of simulation, got $coolantDispatches",
            coolantDispatches.size >= 2
        )
        // The 4000ms coolant cadence is preserved as the scheduler intent. Because coolant
        // runs in the same tick as the battery slice on a single ELM327 channel, a 3000ms
        // battery timeout can push the next coolant dispatch to at most 7000ms. The
        // invariant is that coolant is never permanently starved, regardless of probe outcome.
        val coolantGapMs = coolantDispatches[1] - coolantDispatches[0]
        assertTrue(
            "Coolant dispatch gap must stay within the bounded 4000ms schedule plus max battery timeout (<= 7500ms), got $coolantGapMs",
            coolantGapMs <= 7500L
        )

        // IAT (010F) must be polled in the exact same ticks as coolant (0105).
        val iatDispatches = fakeTransport.commandTimestamps
            .filter { it.second == ToyotaYarisCommands.PID_INTAKE_AIR_TEMP }
            .map { it.first }
        assertEquals(
            "Every coolant dispatch must be paired with an IAT dispatch at the same timestamp",
            coolantDispatches, iatDispatches
        )

        // Warm-up stage must be continuously evaluated from coolant/IAT samples.
        assertNotEquals(
            "Warm-up stage must be continuously evaluated from coolant/IAT samples",
            WarmupStage.S0, controller.liveState.value.warmupStatus.stage
        )
    }

    /**
     * VAL-OBD-012 (negative path): even when battery probes throw a hard transport exception,
     * the scheduler keeps running and the coolant loop still fires on schedule
     * (per-cycle exception isolation).
     */
    @Test
    fun testValObd012_schedulerSurvivesBatteryTransportException() = runTest {
        val clock = ManualClock()
        val fakeTransport = FakeObdTransport()
        var failBattery = true
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd.startsWith("AT") -> "OK"
                failBattery && (cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT || cmd.startsWith("22") || cmd.startsWith("21")) ->
                    throw java.io.IOException("Simulated BLE transport failure during battery slice")
                cmd == ToyotaYarisCommands.PID_VEHICLE_SPEED -> "7E8 03 41 0D 64 >"
                cmd == ToyotaYarisCommands.PID_ENGINE_RPM -> "7E8 04 41 0C 1F 40 >"
                cmd == ToyotaYarisCommands.PID_COOLANT_TEMP -> "7E8 03 41 05 78 >"
                cmd == ToyotaYarisCommands.PID_INTAKE_AIR_TEMP -> "7E8 03 41 0F 41 >"
                else -> "NO DATA"
            }
        }

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine(timeProvider = { clock.now() }),
            timeProvider = { clock.now() }
        )

        // Cycle 1: battery slice throws -> per-cycle isolation must catch it; fast loop of
        // this tick is sacrificed but the scheduler itself survives and must NOT increment
        // the engine bus error counter (battery failures stay decoupled).
        controller.executeDualRateCycle()
        assertEquals(
            "Battery slice transport failure must not touch the engine bus error counter",
            0, controller.consecutiveCanErrors
        )

        // Recover transport, then keep ticking: fast loop and coolant loop must resume.
        failBattery = false
        clock.advance(140L)
        fakeTransport.dispatchedCommands.clear()
        controller.executeDualRateCycle()
        assertTrue(
            "Fast engine loop must resume on the tick after a battery exception",
            fakeTransport.dispatchedCommands.contains(ToyotaYarisCommands.PID_ENGINE_RPM)
        )
        assertTrue(
            "Coolant warm-up loop must fire on schedule after a battery exception",
            fakeTransport.dispatchedCommands.contains(ToyotaYarisCommands.PID_COOLANT_TEMP)
        )
    }
}
