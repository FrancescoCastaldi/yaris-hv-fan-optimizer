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
            val clean = command.replace(" ", "").uppercase()
            val resp = when {
                clean in listOf("ATZ", "ATWS", "ATD", "ATBD") -> "ELM327 v1.5"
                clean.startsWith("ATE") || clean.startsWith("ATH") || clean.startsWith("ATL") ||
                clean.startsWith("ATSP") || clean.startsWith("ATSH") || clean.startsWith("ATCRA") ||
                clean.startsWith("ATFCS") || clean.startsWith("ATAL") || clean.startsWith("ATCAF") ||
                clean.startsWith("ATST") || clean.startsWith("ATSW") || clean.startsWith("ATIB") ||
                clean.startsWith("ATPB") || clean.startsWith("ATCM") || clean.startsWith("ATCF") -> "OK"
                clean == "ATDPN" -> "6"
                clean == "ATRV" -> "14.1V"
                clean == "0100" -> "41 00 BE 3F B8 11"
                clean == "0105" -> "41 05 5A"
                clean == "010C" -> "41 0C 0F A0"
                clean.startsWith("2101") -> "61 01 02 80 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 28 28 28 28 03"
                else -> "NO DATA"
            }
            return formatResponse(resp)
        }

        assertEquals("ELM327 v1.5\r\r>", getMockResponse("atz"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("at ws"))
        assertEquals("OK\r\r>", getMockResponse("at sp 6"))
        assertEquals("OK\r\r>", getMockResponse("AT SH 7E0"))
        assertEquals("OK\r\r>", getMockResponse("AT CRA 7E8"))
        assertEquals("14.1V\r\r>", getMockResponse("atrv"))
        assertEquals("41 00 BE 3F B8 11\r\r>", getMockResponse("0100"))
        assertEquals("41 05 5A\r\r>", getMockResponse("0105"))
        assertEquals("41 0C 0F A0\r\r>", getMockResponse("010C"))
        assertEquals("61 01 02 80 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 28 28 28 28 03\r\r>", getMockResponse("2101"))
        assertEquals("NO DATA\r\r>", getMockResponse("0999"))
    }
}
