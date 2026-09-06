package com.yaris.hvfan

import com.yaris.hvfan.obd.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests fulfilling:
 * - VAL-OBD-001: Decoupled Multi-Tier Capability State Machine
 * - VAL-OBD-002: Mandatory Capability Reset on Disconnect and Standby
 * - VAL-OBD-008: Error Counter Decoupling Between Battery Probes and Engine Bus
 */
class ObdStateMachineTest {

    @Test
    fun testDecoupledStateEvolutionIndependentTiers() {
        val stateMachine = ObdStateMachine()

        // 1. Initial State: all decoupled tiers at baseline
        val s0 = stateMachine.currentCapabilityState
        assertEquals(BleTransportState.Disconnected, s0.bleTransportState)
        assertEquals(ElmProtocolState.Uninitialized, s0.elmProtocolState)
        assertEquals(CanBusLinkState.Silent, s0.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, s0.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, s0.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, s0.fanControlCapability)

        // 2. BLE Transport Connected & Ready
        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        val s1 = stateMachine.currentCapabilityState
        assertEquals(BleTransportState.Ready, s1.bleTransportState)
        // Downstream tiers remain uninitialized - no optimistic premature flags
        assertEquals(ElmProtocolState.Uninitialized, s1.elmProtocolState)
        assertEquals(CanBusLinkState.Silent, s1.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, s1.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, s1.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, s1.fanControlCapability)

        // 3. ELM327 Initialized and Ready
        stateMachine.onElmInitializing()
        assertEquals(ElmProtocolState.Initializing, stateMachine.currentCapabilityState.elmProtocolState)
        stateMachine.onElmReady()
        val s2 = stateMachine.currentCapabilityState
        assertEquals(ElmProtocolState.Ready, s2.elmProtocolState)
        assertEquals(CanBusLinkState.Silent, s2.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, s2.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, s2.batteryEcuDiscoveryState)

        // 4. CAN bus locked on Engine Mode 01
        stateMachine.onCanSearching()
        assertEquals(CanBusLinkState.Searching, stateMachine.currentCapabilityState.canBusLinkState)
        stateMachine.onEngineTelemetrySuccess()
        val s3 = stateMachine.currentCapabilityState
        assertEquals(CanBusLinkState.Locked, s3.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Active, s3.engineTelemetryCapability)
        // CRITICAL: Battery and Fan capabilities remain Undiscovered/Unknown
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, s3.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, s3.fanControlCapability)

        // 5. Battery probing initiated
        stateMachine.onBatteryDiscoveryProbing()
        assertEquals(BatteryEcuDiscoveryState.Probing, stateMachine.currentCapabilityState.batteryEcuDiscoveryState)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)

        // 6. Battery discovered and latched
        stateMachine.onBatteryDiscovered("2228C1")
        val s4 = stateMachine.currentCapabilityState
        assertEquals(BatteryEcuDiscoveryState.Discovered, s4.batteryEcuDiscoveryState)
        assertEquals("2228C1", stateMachine.cachedBatteryPid)
        // Fan capability remains Unknown until actively confirmed
        assertEquals(FanControlCapability.Unknown, s4.fanControlCapability)

        // 7. Fan capability confirmed
        stateMachine.onFanControlConfirmed()
        val s5 = stateMachine.currentCapabilityState
        assertEquals(FanControlCapability.Capable, s5.fanControlCapability)
        assertTrue(stateMachine.isFanControlAuthorized)
        assertTrue(stateMachine.isEcuAckConfirmed)
    }

    @Test
    fun testMandatoryCapabilityResetOnDisconnect() {
        val stateMachine = ObdStateMachine()

        // Set up fully active and discovered state
        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        stateMachine.onElmReady()
        stateMachine.onEngineTelemetrySuccess()
        stateMachine.onBatteryDiscovered("2228C1")
        stateMachine.onFanControlConfirmed()

        assertEquals(BatteryEcuDiscoveryState.Discovered, stateMachine.currentCapabilityState.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Capable, stateMachine.currentCapabilityState.fanControlCapability)
        assertEquals("2228C1", stateMachine.cachedBatteryPid)
        assertTrue(stateMachine.isFanControlAuthorized)
        assertTrue(stateMachine.isEcuAckConfirmed)

        // Trigger transport severance (BLE Disconnected)
        stateMachine.onTransportStateChanged(BleTransportState.Disconnected)

        val teardownState = stateMachine.currentCapabilityState
        assertEquals(BleTransportState.Disconnected, teardownState.bleTransportState)
        assertEquals(ElmProtocolState.Uninitialized, teardownState.elmProtocolState)
        assertEquals(CanBusLinkState.Silent, teardownState.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, teardownState.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, teardownState.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, teardownState.fanControlCapability)

        // Verify cached battery PID lock and fan authorizations are cleared immediately
        assertNull(stateMachine.cachedBatteryPid)
        assertFalse(stateMachine.isFanControlAuthorized)
        assertFalse(stateMachine.isEcuAckConfirmed)
        assertEquals(0, stateMachine.consecutiveCanErrors)
    }

    @Test
    fun testMandatoryCapabilityResetOnVehicleStandby() {
        val stateMachine = ObdStateMachine()

        // Set up operational state
        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        stateMachine.onElmReady()
        stateMachine.onEngineTelemetrySuccess()
        stateMachine.onBatteryDiscovered("2228C1")
        stateMachine.onFanControlConfirmed()

        // Vehicle enters standby (< 13.0V)
        stateMachine.onVehicleStandby()

        val standbyState = stateMachine.currentCapabilityState
        // Transport and ELM layers remain Ready, but operational capabilities are cleared
        assertEquals(BleTransportState.Ready, standbyState.bleTransportState)
        assertEquals(ElmProtocolState.Ready, standbyState.elmProtocolState)
        assertEquals(CanBusLinkState.Silent, standbyState.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, standbyState.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, standbyState.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, standbyState.fanControlCapability)

        // Cached PID lock and fan authorizations cleared
        assertNull(stateMachine.cachedBatteryPid)
        assertFalse(stateMachine.isFanControlAuthorized)
        assertFalse(stateMachine.isEcuAckConfirmed)
    }

    @Test
    fun testMandatoryCapabilityResetOnCanBusAutoRecovery() {
        val stateMachine = ObdStateMachine()

        // Set up operational state
        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        stateMachine.onElmReady()
        stateMachine.onEngineTelemetrySuccess()
        stateMachine.onBatteryDiscovered("2228C1")
        stateMachine.onFanControlConfirmed()

        // Auto-recovery triggered
        stateMachine.onCanBusAutoRecovery()

        val recoveryState = stateMachine.currentCapabilityState
        assertEquals(CanBusLinkState.Searching, recoveryState.canBusLinkState)
        assertEquals(EngineTelemetryCapability.Unavailable, recoveryState.engineTelemetryCapability)
        assertEquals(BatteryEcuDiscoveryState.Undiscovered, recoveryState.batteryEcuDiscoveryState)
        assertEquals(FanControlCapability.Unknown, recoveryState.fanControlCapability)

        // Cached PID and fan authorizations cleared
        assertNull(stateMachine.cachedBatteryPid)
        assertFalse(stateMachine.isFanControlAuthorized)
        assertFalse(stateMachine.isEcuAckConfirmed)
        assertEquals(0, stateMachine.consecutiveCanErrors)
    }

    @Test
    fun testErrorCounterDecouplingBatteryProbeFailure() {
        val stateMachine = ObdStateMachine()

        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        stateMachine.onElmReady()
        stateMachine.onEngineTelemetrySuccess()

        assertEquals(0, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)

        // Simulate 10 consecutive secondary ECU (7E2) failures (timeouts, NO DATA, NRC 7F)
        repeat(10) {
            stateMachine.onBatteryProbeFailed(isExhausted = false)
            // Mode 01 engine telemetry succeeds in parallel
            stateMachine.onEngineTelemetrySuccess()
            assertEquals("consecutiveCanErrors must remain 0 during battery probe failures", 0, stateMachine.consecutiveCanErrors)
            assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)
            assertEquals(CanBusLinkState.Locked, stateMachine.currentCapabilityState.canBusLinkState)
        }

        // consecutiveBatteryErrors tracks secondary ECU failures independently
        assertEquals(10, stateMachine.consecutiveBatteryErrors)
        // Engine bus health counter is completely decoupled and remains 0
        assertEquals(0, stateMachine.consecutiveCanErrors)
    }

    @Test
    fun testEngineTelemetryFailureDegradationAndRecovery() {
        val stateMachine = ObdStateMachine()

        stateMachine.onTransportStateChanged(BleTransportState.Ready)
        stateMachine.onElmReady()
        stateMachine.onEngineTelemetrySuccess()
        assertEquals(0, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)

        // 1st and 2nd engine failures
        stateMachine.onEngineTelemetryFailure()
        assertEquals(1, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)

        stateMachine.onEngineTelemetryFailure()
        assertEquals(2, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)

        // 3rd failure -> Degraded
        stateMachine.onEngineTelemetryFailure()
        assertEquals(3, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Degraded, stateMachine.currentCapabilityState.engineTelemetryCapability)

        // 6th failure -> Unavailable & Silent
        repeat(3) { stateMachine.onEngineTelemetryFailure() }
        assertEquals(6, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Unavailable, stateMachine.currentCapabilityState.engineTelemetryCapability)
        assertEquals(CanBusLinkState.Silent, stateMachine.currentCapabilityState.canBusLinkState)

        // Recovery on new positive engine response
        stateMachine.onEngineTelemetrySuccess()
        assertEquals(0, stateMachine.consecutiveCanErrors)
        assertEquals(EngineTelemetryCapability.Active, stateMachine.currentCapabilityState.engineTelemetryCapability)
        assertEquals(CanBusLinkState.Locked, stateMachine.currentCapabilityState.canBusLinkState)
    }
}
