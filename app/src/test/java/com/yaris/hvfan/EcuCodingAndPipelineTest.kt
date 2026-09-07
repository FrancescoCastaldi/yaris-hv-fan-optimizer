package com.yaris.hvfan

import com.yaris.hvfan.obd.*
import org.junit.Assert.*
import org.junit.Test

class EcuCodingAndPipelineTest {

    @Test
    fun testVehicleSpeedParsing_validResponses() {
        val raw50kmh = "410D32>"
        val speed50 = ToyotaYarisCommands.parseVehicleSpeed(raw50kmh)
        assertEquals(50, speed50)

        val raw100kmh = "41 0D 64 \r\n>"
        val speed100 = ToyotaYarisCommands.parseVehicleSpeed(raw100kmh)
        assertEquals(100, speed100)

        val rawInvalid = "NO DATA>"
        val speedNull = ToyotaYarisCommands.parseVehicleSpeed(rawInvalid)
        assertNull(speedNull)
    }

    @Test
    fun testTimingAdvanceFormula() {
        // Formula: (A / 2.0) - 64
        // If A = 160 (0xA0): (160 / 2) - 64 = 80 - 64 = +16.0 °BTDC
        val rawByte = 160
        val advance = (rawByte / 2.0f) - 64.0f
        assertEquals(16.0f, advance, 0.01f)
    }

    @Test
    fun testEngineLoadAndThrottleFormula() {
        // Formula: (A * 100) / 255
        // If A = 128: (128 * 100) / 255 = 50.19 %
        val rawByte = 128
        val loadPercent = (rawByte * 100.0f) / 255.0f
        assertEquals(50.196f, loadPercent, 0.01f)
    }

    @Test
    fun testBatteryThermalDeratingLogic() {
        val normalState = HvBatteryStatus(
            temp1 = 26.0, temp2 = 28.0, temp3 = 27.0, temp4 = 25.0,
            maxTemp = 28.0, isThermalThrottled = false
        )
        assertFalse(normalState.isThermalThrottled)

        val throttledState = HvBatteryStatus(
            temp1 = 34.0, temp2 = 37.0, temp3 = 35.0, temp4 = 36.0,
            maxTemp = 37.0, isThermalThrottled = 37.0 >= 36.0
        )
        assertTrue(throttledState.isThermalThrottled)
    }

    @Test
    fun testEcuCodingEnumsAndDefaults() {
        val state = EcuCustomizationState()

        // Verify Touch 3 & Comfort Defaults
        assertEquals(Touch3OpeningScreen.GAZOO_RACING, state.touch3OpeningAnimation)
        assertEquals(ReverseBeepMode.SINGLE, state.reverseBeep)
        assertEquals(AutoDoorLockMode.BY_SPEED, state.autoDoorLock)
        assertEquals(TurnSignalFlashes.FLASHES_5, state.turnSignalFlashes)
        assertEquals(RsaSpeedBeepMode.MUTE, state.rsaSpeedLimitBeep)
        assertEquals(CameraOffDelay.SEC_5, state.rearCameraDelay)
        assertFalse(state.touchScreenBeep)
        assertTrue(state.windowsWithKeyFob)
        assertTrue(state.autoDoorUnlock)

        // Codifiche expansion OEM defaults
        assertTrue(state.rearSeatbeltBeep)
        assertEquals(DoorUnlockMode.ALL_DOORS, state.doorUnlockMode)
        assertFalse(state.footwellLightingInDrive)
        assertTrue(state.wiperSpeedLink)
        assertTrue(state.rctaEnabled)
        assertTrue(state.ltaEnabled)
        assertFalse(state.pcsRememberLast)
        assertTrue(state.blowerOnDefroster)
        assertEquals(TemperatureCalibration.ZERO, state.temperatureCalibration)
    }

    @Test
    fun testCanHeaderConstantsIntegrity() {
        assertEquals("750", ToyotaYarisCommands.HEADER_BODY_ECU)
        assertEquals("758", ToyotaYarisCommands.CRA_BODY_ECU)
        assertEquals("7C0", ToyotaYarisCommands.HEADER_METER_ECU)
        assertEquals("7C8", ToyotaYarisCommands.CRA_METER_ECU)
        assertEquals("7E2", ToyotaYarisCommands.HEADER_BATTERY_ECU)
        assertEquals("7EA", ToyotaYarisCommands.CRA_BATTERY_ECU)
        assertEquals("7E0", ToyotaYarisCommands.HEADER_ENGINE_ECU)
        assertEquals("7E8", ToyotaYarisCommands.CRA_ENGINE_ECU)
    }

    @Test
    fun testUdsPositiveResponseValidation() {
        // Positive responses
        assertTrue(Elm327Protocol.isUdsPositiveResponse("6101"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("61A7"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7C8 03 61 A7 00 >"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("758 05 61 01 02 03 04 >"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("5003")) // Mode 10 03 positive response

        // Negative Response Code (NRC 7F)
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 21 11"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 3B 22"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 10 12"))

        // Errors and NO DATA
        assertFalse(Elm327Protocol.isUdsPositiveResponse("NO DATA"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("NODATA"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("ERROR"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("?"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse(""))
    }

    @Test
    fun testVehicleStandbyHysteresis() {
        assertTrue(Elm327Protocol.isVehicleStandby(11.6f))
        assertTrue(Elm327Protocol.isVehicleStandby(12.2f))
        assertTrue(Elm327Protocol.isVehicleStandby(12.6f))
        assertFalse(Elm327Protocol.isVehicleStandby(12.7f))
        assertFalse(Elm327Protocol.isVehicleStandby(13.0f))
        assertFalse(Elm327Protocol.isVehicleStandby(14.2f))
        assertFalse(Elm327Protocol.isVehicleStandby(null))
    }
}

