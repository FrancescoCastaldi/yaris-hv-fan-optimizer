package com.yaris.hvfan.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates multi-tier decoupled capabilities, lifecycle resets, and error isolation.
 *
 * Invariants:
 * 1. BleTransportState leaves Ready -> ALL downstream states reset to baseline uninitialized values.
 * 2. Vehicle Standby (<13.0V) or Auto-Recovery -> Clears cached battery PID, fan authorizations,
 *    and operational capabilities.
 * 3. Secondary ECU (7E2) timeouts / NO DATA / errors -> Decoupled from consecutiveCanErrors
 *    (which solely monitors engine bus health).
 */
class ObdStateMachine {

    private val _capabilityState = MutableStateFlow(ObdCapabilityState())
    val capabilityState: StateFlow<ObdCapabilityState> = _capabilityState.asStateFlow()

    val currentCapabilityState: ObdCapabilityState get() = _capabilityState.value

    var consecutiveCanErrors: Int = 0
        private set

    var consecutiveBatteryErrors: Int = 0
        private set

    var cachedBatteryPid: String? = null
        private set

    var isFanControlAuthorized: Boolean = false
        private set

    var isEcuAckConfirmed: Boolean = false
        private set

    // --- Transport Lifecycle ---

    fun onTransportStateChanged(state: BleTransportState) {
        if (state != BleTransportState.Ready) {
            // Teardown Rule: When BleTransportState leaves Ready, ALL downstream states
            // immediately reset to their baseline uninitialized values.
            teardownAllCapabilities(state)
        } else {
            _capabilityState.value = _capabilityState.value.copy(
                bleTransportState = BleTransportState.Ready
            )
        }
    }

    // --- Protocol Lifecycle ---

    fun onElmInitializing() {
        _capabilityState.value = _capabilityState.value.copy(
            elmProtocolState = ElmProtocolState.Initializing
        )
    }

    fun onElmReady() {
        _capabilityState.value = _capabilityState.value.copy(
            elmProtocolState = ElmProtocolState.Ready
        )
    }

    fun onElmError() {
        _capabilityState.value = _capabilityState.value.copy(
            elmProtocolState = ElmProtocolState.Error
        )
    }

    // --- CAN Link & Engine Telemetry Lifecycle ---

    fun onCanSearching() {
        _capabilityState.value = _capabilityState.value.copy(
            canBusLinkState = CanBusLinkState.Searching
        )
    }

    fun onCanLocked() {
        _capabilityState.value = _capabilityState.value.copy(
            canBusLinkState = CanBusLinkState.Locked
        )
    }

    fun onEngineTelemetrySuccess() {
        consecutiveCanErrors = 0
        _capabilityState.value = _capabilityState.value.copy(
            canBusLinkState = CanBusLinkState.Locked,
            engineTelemetryCapability = EngineTelemetryCapability.Active
        )
    }

    fun onEngineTelemetryFailure() {
        consecutiveCanErrors++
        if (consecutiveCanErrors >= 3) {
            _capabilityState.value = _capabilityState.value.copy(
                engineTelemetryCapability = EngineTelemetryCapability.Degraded
            )
        }
        if (consecutiveCanErrors >= 6) {
            _capabilityState.value = _capabilityState.value.copy(
                canBusLinkState = CanBusLinkState.Silent,
                engineTelemetryCapability = EngineTelemetryCapability.Unavailable
            )
        }
    }

    // --- Battery ECU Discovery Lifecycle ---

    fun onBatteryDiscoveryProbing() {
        _capabilityState.value = _capabilityState.value.copy(
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Probing
        )
    }

    fun onBatteryDiscovered(pid: String) {
        cachedBatteryPid = pid
        consecutiveBatteryErrors = 0
        _capabilityState.value = _capabilityState.value.copy(
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Discovered
        )
    }

    fun onBatteryProbeFailed(isExhausted: Boolean = false) {
        consecutiveBatteryErrors++
        // CRITICAL (VAL-OBD-008): Secondary ECU (7E2) failures DO NOT increment consecutiveCanErrors!
        // consecutiveCanErrors is strictly untouched here.
        if (isExhausted) {
            _capabilityState.value = _capabilityState.value.copy(
                batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Unsupported
            )
        }
    }

    // --- Fan Control Capability Lifecycle ---

    fun onFanControlConfirmed() {
        isFanControlAuthorized = true
        isEcuAckConfirmed = true
        _capabilityState.value = _capabilityState.value.copy(
            fanControlCapability = FanControlCapability.Capable,
            fanActuationState = FanActuationState.CONFIRMED
        )
    }

    fun onFanControlRejectedOrUnsupported() {
        isFanControlAuthorized = false
        isEcuAckConfirmed = false
        _capabilityState.value = _capabilityState.value.copy(
            fanControlCapability = FanControlCapability.Unsupported,
            fanActuationState = FanActuationState.UNAVAILABLE
        )
    }

    fun onFanActuationStateChanged(actuationState: FanActuationState) {
        _capabilityState.value = _capabilityState.value.copy(
            fanActuationState = actuationState
        )
    }

    fun clearFanAuthorizations() {
        isFanControlAuthorized = false
        isEcuAckConfirmed = false
    }

    // --- Vehicle Standby & Auto-Recovery Lifecycle ---

    /**
     * Called when vehicle enters READY (12V >= 13.0V, DC-DC converter active).
     */
    fun onVehicleReady() {
        // Vehicle READY confirmed; preparing for CAN probing and discovery
    }

    /**
     * Called when vehicle enters standby (< 13.0V, DC-DC converter off).
     * Mandatory capability reset: operational ECU capabilities, cached battery PID locks,
     * and fan control authorizations are immediately cleared.
     */
    fun onVehicleStandby() {
        cachedBatteryPid = null
        clearFanAuthorizations()
        consecutiveBatteryErrors = 0
        _capabilityState.value = _capabilityState.value.copy(
            canBusLinkState = CanBusLinkState.Silent,
            engineTelemetryCapability = EngineTelemetryCapability.Unavailable,
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
            fanControlCapability = FanControlCapability.Unknown
        )
    }

    /**
     * Called during CAN bus auto-recovery.
     * Mandatory capability reset: operational ECU capabilities, cached battery PID locks,
     * and fan control authorizations are immediately cleared.
     */
    fun onCanBusAutoRecovery() {
        cachedBatteryPid = null
        clearFanAuthorizations()
        consecutiveCanErrors = 0
        consecutiveBatteryErrors = 0
        _capabilityState.value = _capabilityState.value.copy(
            canBusLinkState = CanBusLinkState.Searching,
            engineTelemetryCapability = EngineTelemetryCapability.Unavailable,
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
            fanControlCapability = FanControlCapability.Unknown
        )
    }

    /**
     * Teardown all capabilities and cached locks on transport disconnect.
     */
    fun teardownAllCapabilities(newTransportState: BleTransportState = BleTransportState.Disconnected) {
        cachedBatteryPid = null
        clearFanAuthorizations()
        consecutiveCanErrors = 0
        consecutiveBatteryErrors = 0
        _capabilityState.value = ObdCapabilityState(
            bleTransportState = newTransportState,
            elmProtocolState = ElmProtocolState.Uninitialized,
            canBusLinkState = CanBusLinkState.Silent,
            engineTelemetryCapability = EngineTelemetryCapability.Unavailable,
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
            fanControlCapability = FanControlCapability.Unknown
        )
    }
}
