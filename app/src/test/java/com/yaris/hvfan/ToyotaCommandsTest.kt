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
