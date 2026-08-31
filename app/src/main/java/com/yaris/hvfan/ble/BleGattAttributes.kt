package com.yaris.hvfan.ble

import java.util.UUID

object BleGattAttributes {
    // Client Characteristic Configuration Descriptor
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Common 16-bit UUID base
    private fun fromShortUuid(shortUuid: String): UUID {
        return UUID.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")
    }

    // Supported BLE OBD Service Candidates
    val SUPPORTED_SERVICES = listOf(
        fromShortUuid("fff0"), // vLinker MC+, Veepeak, Carista, Konnwei
        fromShortUuid("ffe0"), // HM-10 / CC2540 / ELM327 BLE standard
        fromShortUuid("18f0"), // Custom BLE OBD dongles
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART Service (NUS)
        UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"), // ISSC Transparent UART
        UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    )

    // Supported Write Characteristics
    val WRITE_CHARACTERISTICS = listOf(
        fromShortUuid("fff2"), // vLinker / Veepeak write
        fromShortUuid("fff1"),
        fromShortUuid("ffe1"), // HM-10 write/notify
        fromShortUuid("2af1"),
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"), // NUS TX
        UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")  // ISSC TX
    )

    // Supported Notify/Read Characteristics
    val NOTIFY_CHARACTERISTICS = listOf(
        fromShortUuid("fff1"), // vLinker / Veepeak notify
        fromShortUuid("fff2"),
        fromShortUuid("ffe1"), // HM-10 notify
        fromShortUuid("2af0"),
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"), // NUS RX
        UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")  // ISSC RX
    )
}
