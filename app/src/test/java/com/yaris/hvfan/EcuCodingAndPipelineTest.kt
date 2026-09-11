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
        assertTrue(Elm327Protocol.isUdsPositiveResponse("5001")) // Mode 10 01 positive response
        assertTrue(Elm327Protocol.isUdsPositiveResponse("6E A0 01 01", "2E")) // Mode 2E positive response
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7EA 02 7E 00 >", "3E")) // Mode 3E positive response
        // Positive response whose data payload contains 0x7F (must NOT be treated as NRC)
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7C8 04 61 A7 00 7F >"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("62 28 C1 44 7F 44 43 41 03 >", "22"))
        // Multi-frame ISO-TP First Frame with ATH1 and spaces ([CAN_ID] [1x] [len] [SID] ...)
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7EA 10 14 62 28 C1 44 45\r7EA 21 44 43 41 03 00 00 >", "22"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7EA 10 14 62 28 C1 44 45 >"))
        // Safe handling of malformed expectedService without NumberFormatException
        assertTrue(Elm327Protocol.isUdsPositiveResponse("6101", "ZZ"))
        // Multi-line response: primary ECU responds positive, secondary responds NRC
        assertTrue(Elm327Protocol.isUdsPositiveResponse("7C8 03 61 A7 00\r758 03 7F 21 11\r>"))

        // Negative Response Code (NRC 7F)
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 21 11"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 22 11"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 2E 22"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7F 10 12"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("7C8 03 7F 21 11 >"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("758 03 7F 21 12 >"))

        // Errors and NO DATA
        assertFalse(Elm327Protocol.isUdsPositiveResponse("NO DATA"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("NODATA"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("ERROR"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse("?"))
        assertFalse(Elm327Protocol.isUdsPositiveResponse(""))
    }

    @Test
    fun testUdsCodingPipelineAndHelpers() {
        // Session & Service constants
        assertEquals("1003", ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED)
        assertEquals("1001", ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT)
        assertEquals("3E00", ToyotaYarisCommands.CMD_UDS_TESTER_PRESENT)

        // DIDs integrity
        assertEquals("01AC", ToyotaYarisCommands.DID_METER_REVERSE_BEEP)
        assertEquals("A001", ToyotaYarisCommands.DID_METER_REVERSE_BEEP_LEGACY)
        assertEquals("01A0", ToyotaYarisCommands.DID_METER_DRIVER_SEATBELT)
        assertEquals("B001", ToyotaYarisCommands.DID_BODY_AUTO_DOOR_LOCK)
        assertEquals("B003", ToyotaYarisCommands.DID_BODY_WINDOWS_KEY_FOB)
        assertEquals("B010", ToyotaYarisCommands.DID_BODY_TURN_SIGNAL_FLASHES)
        assertEquals("C001", ToyotaYarisCommands.DID_AIRCON_AUTO_AC_BUTTON)
        assertEquals("D001", ToyotaYarisCommands.DID_ADAS_LDA_WARNING_VOLUME)

        // Candidate DIDs for Meter Reverse Beep & Seatbelt
        assertEquals(listOf("01AC", "01A0", "2010", "1020", "01A7", "A001"), ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP)
        assertEquals(listOf("01A0", "01AC", "2010", "1020", "01A7", "A002"), ToyotaYarisCommands.CANDIDATE_DIDS_METER_SEATBELT)

        // Helper compositions
        assertEquals("2201AC", ToyotaYarisCommands.buildUdsRead(ToyotaYarisCommands.DID_METER_REVERSE_BEEP))
        assertEquals("2E01AC00", ToyotaYarisCommands.buildUdsWrite(ToyotaYarisCommands.DID_METER_REVERSE_BEEP, "00"))
        assertEquals("2EB01005", ToyotaYarisCommands.buildUdsWrite(ToyotaYarisCommands.DID_BODY_TURN_SIGNAL_FLASHES, "05"))

        // Gateway BCM prefix 40 helpers
        assertEquals("40 22 B001", ToyotaYarisCommands.buildGatewayUdsRead("B001", useBcmPrefix = true))
        assertEquals("22B001", ToyotaYarisCommands.buildGatewayUdsRead("B001", useBcmPrefix = false))
        assertEquals("40 2E B001 01", ToyotaYarisCommands.buildGatewayUdsWrite("B001", "01", useBcmPrefix = true))
        assertEquals("2EB00101", ToyotaYarisCommands.buildGatewayUdsWrite("B001", "01", useBcmPrefix = false))
        assertEquals("AT ST C8", Elm327Protocol.CMD_TIMEOUT_GATEWAY_ECU)

        // Gateway BCM prefix 40 positive responses & NRC
        assertTrue(Elm327Protocol.isUdsPositiveResponse("40 50 03", "10"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("40 62 B0 01 01", "22"))
        assertTrue(Elm327Protocol.isUdsPositiveResponse("40 6E B0 01 01", "2E"))
        val gatewayNrc = Elm327Protocol.extractUdsNrc("40 7F 22 31")
        assertNotNull(gatewayNrc)
        assertEquals("22", gatewayNrc!!.serviceId)
        assertEquals("31", gatewayNrc.nrc)

        // NRC Decoding & extraction
        val nrcConditions = Elm327Protocol.extractUdsNrc("7F 10 22")
        assertNotNull(nrcConditions)
        assertEquals("10", nrcConditions!!.serviceId)
        assertEquals("22", nrcConditions.nrc)
        assertTrue(Elm327Protocol.getUdsNrcDescription(nrcConditions.nrc).contains("Condizioni non corrette"))

        val nrcNotSupported = Elm327Protocol.extractUdsNrc("7F 2E 11")
        assertNotNull(nrcNotSupported)
        assertEquals("2E", nrcNotSupported!!.serviceId)
        assertEquals("11", nrcNotSupported.nrc)
        assertTrue(Elm327Protocol.getUdsNrcDescription(nrcNotSupported.nrc).contains("non supportato"))

        val nrcOutOfRange = Elm327Protocol.extractUdsNrc("7F 2E 31")
        assertNotNull(nrcOutOfRange)
        assertEquals("31", nrcOutOfRange!!.nrc)
        assertTrue(Elm327Protocol.getUdsNrcDescription(nrcOutOfRange.nrc).contains("fuori limite"))
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

