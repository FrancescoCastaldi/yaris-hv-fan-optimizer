package com.yaris.hvfan.obd

import com.yaris.hvfan.ble.BleConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for OBD communication transport (Bluetooth Classic SPP / BLE GATT / Mock).
 * Enables decoupled unit testing of protocol engines and controllers without Android Context or hardware.
 */
interface ObdTransport {
    val connectionState: StateFlow<BleConnectionState>
    suspend fun sendCommand(command: String, timeoutMs: Long = 3500L): String
    suspend fun sendWakeSequence() {}
    fun getConnectedDeviceName(): String? = null
}
