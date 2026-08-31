package com.yaris.hvfan.ble

import java.util.UUID

object BleGattAttributes {
    // Client Characteristic Configuration Descriptor (CCCD)
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Helper per generare UUID standard a 16-bit
    fun fromShortUuid(shortUuid: String): UUID {
        return UUID.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")
    }

    // Servizi BLE OBD Primari noti (in ordine di priorità)
    val KNOWN_OBD_SERVICES = listOf(
        fromShortUuid("fff0"),                                     // vLinker MC+, Veepeak, Carista, Konnwei
        fromShortUuid("ffe0"),                                     // Vgate iCar Pro, HM-10, CC2540, ELM327 BLE
        fromShortUuid("18f0"),                                     // Vgate iCar Pro (IOS-Vlink alternative)
        UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"), // ISSC Transparent UART (Vgate / Microchip)
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART Service (NUS)
        UUID.fromString("0000e0ff-3c17-d293-8e48-14fe2e4da212"), // Vgate Custom E0FF
        fromShortUuid("fee0"),                                     // Vgate / Telit
        fromShortUuid("fef0"),
        UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")  // Custom BLE OBD
    )

    // Caratteristiche di Scrittura (TX verso dongle OBD)
    val WRITE_CHARACTERISTICS = listOf(
        fromShortUuid("fff2"),                                     // vLinker / Veepeak Write
        fromShortUuid("fff1"),
        fromShortUuid("ffe1"),                                     // Vgate iCar Pro / HM-10 (Write & Notify condivisi)
        fromShortUuid("2af1"),                                     // Vgate 18F0 Write
        fromShortUuid("2af0"),
        UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"), // ISSC TX
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"), // NUS TX
        UUID.fromString("0000e1ff-3c17-d293-8e48-14fe2e4da212")  // Vgate E1FF Write
    )

    // Caratteristiche di Notifica / Lettura (RX da dongle OBD)
    val NOTIFY_CHARACTERISTICS = listOf(
        fromShortUuid("fff1"),                                     // vLinker / Veepeak Notify
        fromShortUuid("fff2"),
        fromShortUuid("ffe1"),                                     // Vgate iCar Pro / HM-10 (Write & Notify condivisi)
        fromShortUuid("2af0"),                                     // Vgate 18F0 Notify
        fromShortUuid("2af1"),
        UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616"), // ISSC RX
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"), // NUS RX
        UUID.fromString("0000e1ff-3c17-d293-8e48-14fe2e4da212")  // Vgate E1FF Notify
    )

    // UUID generici da escludere durante la ricerca fallback di caratteristiche seriali
    val EXCLUDED_SERVICES = listOf(
        fromShortUuid("1800"), // Generic Access
        fromShortUuid("1801"), // Generic Attribute
        fromShortUuid("180a"), // Device Information
        fromShortUuid("180f")  // Battery Service
    )
}
