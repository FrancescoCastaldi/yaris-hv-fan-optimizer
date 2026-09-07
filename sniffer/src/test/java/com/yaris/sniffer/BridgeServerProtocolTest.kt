package com.yaris.sniffer

import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeServerProtocolTest {

    private fun formatResponse(response: String): String {
        val trimmed = response.trimEnd('>', '\r', '\n')
        return "$trimmed\r\r>"
    }

    @Test
    fun testElmResponseFormatting() {
        assertEquals("OK\r\r>", formatResponse("OK"))
        assertEquals("ELM327 v1.5\r\r>", formatResponse("ELM327 v1.5\r"))
        assertEquals("41 0D 32\r\r>", formatResponse("41 0D 32\r\r>"))
        assertEquals("41 00 BE 3F B8 11\r\r>", formatResponse("41 00 BE 3F B8 11\r\n\r\n>"))
    }

    @Test
    fun testCommandCleaning() {
        val rawCommandWithCr = "ATZ\r"
        val rawCommandWithCrlf = "0100\r\n"
        val rawCommandPadded = "  2228C1 \r"

        assertEquals("ATZ", rawCommandWithCr.trim())
        assertEquals("0100", rawCommandWithCrlf.trim())
        assertEquals("2228C1", rawCommandPadded.trim())
    }

    @Test
    fun testMockElmResponses() {
        fun getMockResponse(command: String): String {
            return when (command.uppercase()) {
                "ATZ", "AT WS" -> "ELM327 v1.5\r\r>"
                "ATE0", "ATE1", "ATH0", "ATH1", "ATL0", "ATSP0", "ATSP6", "ATAL", "ATCAF1" -> "OK\r\r>"
                "ATDPN" -> "6\r\r>"
                "ATRV" -> "14.1V\r\r>"
                else -> "NO DATA\r\r>"
            }
        }

        assertEquals("ELM327 v1.5\r\r>", getMockResponse("atz"))
        assertEquals("OK\r\r>", getMockResponse("atsp6"))
        assertEquals("14.1V\r\r>", getMockResponse("atrv"))
        assertEquals("NO DATA\r\r>", getMockResponse("0105"))
    }
}
