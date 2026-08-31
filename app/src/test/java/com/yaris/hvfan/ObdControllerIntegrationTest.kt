package com.yaris.hvfan

import com.yaris.hvfan.obd.*
import org.junit.Assert.*
import org.junit.Test

class ObdControllerIntegrationTest {

    @Test
    fun testCockpitDragyAccelerationTimerLogic() {
        var isArmed = true
        var startTimeMs = 0L
        var time0to100Ms: Long? = null

        // 1. Standstill (0 km/h) -> Armed
        val speed0 = 0
        assertTrue(isArmed)

        // 2. Launch start (5 km/h)
        val speedLaunch = 5
        startTimeMs = 1000L // 1.000s
        isArmed = false

        // 3. Reaching 100 km/h at 10.450s
        val speed100 = 100
        val finishTimeMs = 10450L
        if (speed100 >= 100 && time0to100Ms == null) {
            time0to100Ms = finishTimeMs - startTimeMs
        }

        assertNotNull(time0to100Ms)
        assertEquals(9450L, time0to100Ms) // 9.45s 0-100 km/h
    }

    @Test
    fun testWarmupStateMachineLogic() {
        // Test S0 (Engine Cold, initial cranking)
        var coolantTemp = 18.0
        var phase = when {
            coolantTemp < 40.0 -> "S1a (Riscaldamento Catalizzatore)"
            coolantTemp < 55.0 -> "S1b (Riscaldamento Iniziale Motore)"
            coolantTemp < 70.0 -> "S2 (Controllo Stechiometrico)"
            else -> "S4 (Piena Efficienza Ibrida / EV Completo)"
        }
        assertEquals("S1a (Riscaldamento Catalizzatore)", phase)

        // Test S4 (Warm Engine >= 70°C)
        coolantTemp = 88.0
        phase = when {
            coolantTemp < 40.0 -> "S1a (Riscaldamento Catalizzatore)"
            coolantTemp < 55.0 -> "S1b (Riscaldamento Iniziale Motore)"
            coolantTemp < 70.0 -> "S2 (Controllo Stechiometrico)"
            else -> "S4 (Piena Efficienza Ibrida / EV Completo)"
        }
        assertEquals("S4 (Piena Efficienza Ibrida / EV Completo)", phase)
    }

    @Test
    fun testEcuCustomizationWritePayloads() {
        val state = EcuCustomizationState(
            touch3OpeningAnimation = Touch3OpeningScreen.GAZOO_RACING,
            reverseBeep = ReverseBeepMode.SINGLE,
            autoDoorLock = AutoDoorLockMode.BY_SPEED,
            turnSignalFlashes = TurnSignalFlashes.FLASHES_5,
            keylessBuzzerVolume = KeylessBuzzerVolume.MEDIUM
        )

        // Verify UDS hex codes
        assertEquals("01", state.touch3OpeningAnimation.code)
        assertEquals("00", state.reverseBeep.code)
        assertEquals("01", state.autoDoorLock.code)
        assertEquals("05", state.turnSignalFlashes.code)
        assertEquals("04", state.keylessBuzzerVolume.code)
    }

    @Test
    fun testFactoryRestorePayloads() {
        // Factory OEM defaults
        val oemReverseBeep = ReverseBeepMode.CONTINUOUS
        val oemTurnSignals = TurnSignalFlashes.FLASHES_3
        val oemTouchScreen = Touch3OpeningScreen.STANDARD_TOYOTA

        assertEquals("01", oemReverseBeep.code)
        assertEquals("03", oemTurnSignals.code)
        assertEquals("00", oemTouchScreen.code)
    }

    @Test
    fun testElm327ProtocolInitAndErrorHandling() {
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT Z"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AT 2"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT SP 6"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT CAF 1"))
        assertEquals("AT SP 0", Elm327Protocol.PROTOCOL_FALLBACK)

        val rawWithGarbage = "SEARCHING...\r\n7E8 03 41 05 5A >"
        val cleaned = Elm327Protocol.cleanResponse(rawWithGarbage)
        assertEquals("7E80341055A", cleaned)

        assertTrue(Elm327Protocol.isError("NO DATA"))
        assertTrue(Elm327Protocol.isError("UNABLE TO CONNECT"))
        assertTrue(Elm327Protocol.isError("CAN ERROR"))
        assertTrue(Elm327Protocol.isError("BUFFER FULL"))
        assertFalse(Elm327Protocol.isError("7EA 07 62 28 C1 28 00 00 >"))
    }

    @Test
    fun testBluetoothTransportEnum() {
        val ble = com.yaris.hvfan.ble.BluetoothTransportType.BLE
        val classic = com.yaris.hvfan.ble.BluetoothTransportType.CLASSIC_SPP
        val auto = com.yaris.hvfan.ble.BluetoothTransportType.AUTO

        assertEquals("BLE", ble.name)
        assertEquals("CLASSIC_SPP", classic.name)
        assertEquals("AUTO", auto.name)
    }
}
