package com.yaris.hvfan

import com.yaris.hvfan.obd.ToyotaYarisCommands
import org.junit.Assert.*
import org.junit.Test

class ToyotaCommandsTest {

    @Test
    fun testParseBatteryResponse_validTngaFrame() {
        val rawResponse = "62 28 C1 40 42 41 3F 3E 06 >"
        val result = ToyotaYarisCommands.parseBatteryResponse(rawResponse, isForced = true)

        assertNotNull(result)
        result?.let {
            assertEquals(24.0, it.temp1, 0.01)
            assertEquals(26.0, it.temp2, 0.01)
            assertEquals(25.0, it.temp3, 0.01)
            assertEquals(23.0, it.temp4, 0.01)
            assertEquals(26.0, it.maxTemp, 0.01)
            assertEquals(22.0, it.intakeTemp, 0.01)
            assertEquals(6, it.fanSpeedLevel)
            assertTrue(it.isFanForced)
        }
    }

    @Test
    fun testParseBatteryResponse_errorResponseReturnsNull() {
        val raw = "NO DATA\r>"
        val result = ToyotaYarisCommands.parseBatteryResponse(raw, isForced = false)
        assertNull(result)
    }

    @Test
    fun testParseBatteryResponse_nonBatteryFramesReturnNull() {
        // Engine frame from Mode 01 PID 00 must return null
        val engineFrame = "7E8 06 41 00 BE 7F A8 11 >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(engineFrame, isForced = false))

        // Engine RPM response must return null
        val rpmFrame = "7E8 04 41 0C 1F 40 >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(rpmFrame, isForced = false))

        // Arbitrary hex garbage must return null
        val garbageFrame = "7EA 08 AA BB CC DD EE FF >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(garbageFrame, isForced = false))
    }

    @Test
    fun testParseBatteryTemperature2187_validFrame() {
        // Formula: ((A * 256) + B) * 255.9f / 65535f - 50.0f
        // Channel 1: 0x4B07 -> 19207 * 255.9 / 65535 - 50.0 = 75.0 - 50.0 = 25.0 °C
        // Channel 2: 0x4D38 -> 19768 * 255.9 / 65535 - 50.0 = 77.19 - 50.0 = 27.19 °C
        // Channel 3: 0x4EB8 -> 20152 * 255.9 / 65535 - 50.0 = 78.69 - 50.0 = 28.69 °C
        // Channel 4: 0x4A00 -> 18944 * 255.9 / 65535 - 50.0 = 73.97 - 50.0 = 23.97 °C
        val raw2187 = "61 87 4B 07 4D 38 4E B8 4A 00 >"
        val status = ToyotaYarisCommands.parseBatteryTemperature2187(raw2187, isForced = false)

        assertNotNull(status)
        status?.let {
            assertEquals(25.0, it.temp1, 0.1)
            assertEquals(27.19, it.temp2, 0.1)
            assertEquals(28.69, it.temp3, 0.1)
            assertEquals(28.69, it.maxTemp, 0.1)
            assertEquals(25.0, it.minTemp, 0.1)
            assertEquals(26.96, it.avgTemp, 0.1)
            assertEquals(23.97, it.intakeTemp, 0.1)
            assertEquals(0, it.fanSpeedLevel)
            assertFalse(it.isFanForced)
        }

        // Also test through parseBatteryResponse
        val statusDelegated = ToyotaYarisCommands.parseBatteryResponse(raw2187, isForced = true)
        assertNotNull(statusDelegated)
        statusDelegated?.let {
            assertEquals(28.69, it.maxTemp, 0.1)
            assertEquals(25.0, it.minTemp, 0.1)
            assertEquals(6, it.fanSpeedLevel)
            assertTrue(it.isFanForced)
        }
    }

    @Test
    fun testParseBatteryResponse_21CE_liveData() {
        val raw21CE = "61 CE 01 02 03 04 05 06 >"
        val status = ToyotaYarisCommands.parseBatteryResponse(raw21CE, isForced = true)
        assertNotNull(status)
        status?.let {
            assertEquals(25.0, it.maxTemp, 0.01)
            assertEquals(25.0, it.minTemp, 0.01)
            assertEquals(6, it.fanSpeedLevel)
            assertTrue(it.isFanForced)
        }
    }

    @Test
    fun testBatteryFallbackPidsPriority() {
        // Must contain real documented Toyota PIDs 2187 and 21CE at the top
        assertEquals("2187", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0])
        assertEquals("21CE", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[1])
        assertEquals("2101", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[2])
    }

    @Test
    fun testMeterAndGatewayUdsCandidates() {
        assertTrue(ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP.contains("01AC"))
        assertTrue(ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP.contains("01A0"))
        assertTrue(ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP.contains("2010"))
        assertTrue(ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP.contains("1020"))
        assertTrue(ToyotaYarisCommands.CANDIDATE_DIDS_METER_REVERSE_BEEP.contains("01A7"))

        assertEquals("40", ToyotaYarisCommands.BCM_PREFIX)
        assertEquals("40 22 B001", ToyotaYarisCommands.buildGatewayUdsRead("B001", useBcmPrefix = true))
        assertEquals("22B001", ToyotaYarisCommands.buildGatewayUdsRead("B001", useBcmPrefix = false))
        assertEquals("40 2E B001 01", ToyotaYarisCommands.buildGatewayUdsWrite("B001", "01", useBcmPrefix = true))
        assertEquals("2EB00101", ToyotaYarisCommands.buildGatewayUdsWrite("B001", "01", useBcmPrefix = false))
    }

    @Test
    fun testActiveTestCommandConstants() {
        assertEquals("300806", ToyotaYarisCommands.CMD_FAN_MAX_SPEED_UDS)
        assertEquals("2F580306", ToyotaYarisCommands.CMD_FAN_MAX_SPEED_ALT)
        assertEquals("2F5800", ToyotaYarisCommands.CMD_FAN_RETURN_CONTROL_TO_ECU)
        assertEquals("300800", ToyotaYarisCommands.CMD_FAN_STOP_OR_RESET)
        assertEquals("AT SH 7E2", ToyotaYarisCommands.CMD_SET_HEADER_BATTERY_ECU)
        assertEquals("AT CRA 7EA", ToyotaYarisCommands.CMD_SET_RECEIVE_FILTER)
    }

    @Test
    fun testGetFilterForHeader() {
        assertEquals("7E8", ToyotaYarisCommands.getFilterForHeader("7E0"))
        assertEquals("7E8", ToyotaYarisCommands.getFilterForHeader("7e0"))
        assertEquals("7E8", ToyotaYarisCommands.getFilterForHeader(" 7E0 "))
        assertEquals("7EA", ToyotaYarisCommands.getFilterForHeader("7E2"))
        assertEquals("7C8", ToyotaYarisCommands.getFilterForHeader("7C0"))
        assertEquals("758", ToyotaYarisCommands.getFilterForHeader("750"))
        assertEquals("7CC", ToyotaYarisCommands.getFilterForHeader("7C4"))
        assertEquals("7A8", ToyotaYarisCommands.getFilterForHeader("7A0"))
        assertNull(ToyotaYarisCommands.getFilterForHeader("7DF"))
        assertNull(ToyotaYarisCommands.getFilterForHeader("UNKNOWN"))
    }
}
