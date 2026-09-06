package com.yaris.hvfan.obd

import com.yaris.hvfan.ble.BleConnectionState

/**
 * Multi-tier state hierarchy for decoupled OBD-II communication and ECU capabilities.
 * Designed according to System Architecture v2.9.14:
 * 1. BleTransportState: Bluetooth socket connectivity
 * 2. ElmProtocolState: ELM327 command set readiness
 * 3. CanBusLinkState: CAN physical/data link layer status
 * 4. EngineTelemetryCapability: Mode 01 broadcast capability
 * 5. BatteryEcuDiscoveryState: Toyota hybrid battery ECU discovery
 * 6. FanControlCapability: Active IO control capability
 */

enum class BleTransportState {
    Disconnected,
    Connecting,
    Connected,
    Ready;

    companion object {
        fun fromBleConnectionState(state: BleConnectionState): BleTransportState = when (state) {
            is BleConnectionState.Disconnected, is BleConnectionState.Error -> Disconnected
            is BleConnectionState.Scanning, is BleConnectionState.Connecting, is BleConnectionState.Reconnecting -> Connecting
            is BleConnectionState.Connected -> Connected
            is BleConnectionState.Ready -> Ready
        }
    }
}

enum class ElmProtocolState {
    Uninitialized,
    Initializing,
    Ready,
    Error
}

enum class CanBusLinkState {
    Silent,
    Searching,
    Locked
}

enum class EngineTelemetryCapability {
    Unavailable,
    Active,
    Degraded
}

enum class BatteryEcuDiscoveryState {
    Undiscovered,
    Probing,
    Discovered,
    Unsupported
}

enum class FanControlCapability {
    Unknown,
    Capable,
    Unsupported
}

/**
 * 4-way fan actuation state as defined in Section 4 of System Architecture.
 */
enum class FanActuationState {
    OEM_AUTOMATIC,
    REQUESTED,
    CONFIRMED,
    UNAVAILABLE
}

data class ObdCapabilityState(
    val bleTransportState: BleTransportState = BleTransportState.Disconnected,
    val elmProtocolState: ElmProtocolState = ElmProtocolState.Uninitialized,
    val canBusLinkState: CanBusLinkState = CanBusLinkState.Silent,
    val engineTelemetryCapability: EngineTelemetryCapability = EngineTelemetryCapability.Unavailable,
    val batteryEcuDiscoveryState: BatteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
    val fanControlCapability: FanControlCapability = FanControlCapability.Unknown,
    val fanActuationState: FanActuationState = FanActuationState.OEM_AUTOMATIC
) {
    /**
     * Resets all operational capabilities down to their uninitialized/baseline values
     * while preserving the transport and protocol layer states.
     */
    fun resetOperationalCapabilities(): ObdCapabilityState {
        return copy(
            canBusLinkState = CanBusLinkState.Silent,
            engineTelemetryCapability = EngineTelemetryCapability.Unavailable,
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
            fanControlCapability = FanControlCapability.Unknown,
            fanActuationState = FanActuationState.OEM_AUTOMATIC
        )
    }

    /**
     * Complete teardown on transport disconnect/severance.
     */
    fun resetToDisconnected(): ObdCapabilityState {
        return ObdCapabilityState(
            bleTransportState = BleTransportState.Disconnected,
            elmProtocolState = ElmProtocolState.Uninitialized,
            canBusLinkState = CanBusLinkState.Silent,
            engineTelemetryCapability = EngineTelemetryCapability.Unavailable,
            batteryEcuDiscoveryState = BatteryEcuDiscoveryState.Undiscovered,
            fanControlCapability = FanControlCapability.Unknown,
            fanActuationState = FanActuationState.OEM_AUTOMATIC
        )
    }
}
