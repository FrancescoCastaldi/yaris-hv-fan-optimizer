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
        assertEquals("AT SH 7E2", ToyotaYarisCommands.CMD_SET_HEADER_BATTERY_ECU)
        assertEquals("AT CRA 7EA", ToyotaYarisCommands.CMD_SET_RECEIVE_FILTER)
    }
}
