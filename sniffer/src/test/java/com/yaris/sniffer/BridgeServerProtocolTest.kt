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
            return formatResponse(com.yaris.sniffer.server.BridgeServer.getMockResponse(command))
        }

        assertEquals("ELM327 v1.5\r\r>", getMockResponse("atz"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("at ws"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("ATI"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("STI"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("AT@1"))
        assertEquals("ELM327 v1.5\r\r>", getMockResponse("ST DI"))
        assertEquals("OK\r\r>", getMockResponse("at sp 6"))
        assertEquals("OK\r\r>", getMockResponse("AT TP 6"))
        assertEquals("OK\r\r>", getMockResponse("AT FC"))
        assertEquals("OK\r\r>", getMockResponse("AT CS"))
        assertEquals("OK\r\r>", getMockResponse("AT M 0"))
        assertEquals("OK\r\r>", getMockResponse("ATAT1"))
        assertEquals("OK\r\r>", getMockResponse("ATS0"))
        assertEquals("OK\r\r>", getMockResponse("ATH1"))
        assertEquals("OK\r\r>", getMockResponse("AT SH 7E0"))
        assertEquals("OK\r\r>", getMockResponse("AT CRA 7E8"))
        assertEquals("14.1V\r\r>", getMockResponse("atrv"))
        assertEquals("ON\r\r>", getMockResponse("AT IGN"))
        assertEquals("43 00 00 00 00 00 00\r\r>", getMockResponse("03"))
        assertEquals("44\r\r>", getMockResponse("04"))
        assertEquals("47 00 00 00 00 00 00\r\r>", getMockResponse("07"))
        assertEquals("49 00 55 40 00 00\r\r>", getMockResponse("0900"))
        assertEquals("49 02 01 56 4E 4B 4B 44 33 46 33 30 30 30 31 32 33 34 35\r\r>", getMockResponse("0902"))
        assertEquals("4A 00 00 00 00 00 00\r\r>", getMockResponse("0A"))
        assertEquals("41 00 BE 3F B8 11\r\r>", getMockResponse("0100"))
        assertEquals("41 20 80 00 00 00\r\r>", getMockResponse("0120"))
        assertEquals("41 04 80\r\r>", getMockResponse("0104"))
        assertEquals("41 05 5A\r\r>", getMockResponse("0105"))
        assertEquals("41 0C 0F A0\r\r>", getMockResponse("010C"))
        assertEquals("41 0D 28\r\r>", getMockResponse("010D"))
        assertEquals("41 0D 28 0C 0F A0 11 66\r\r>", getMockResponse("010D0C11"))
        assertEquals("62 28 C1 44 45 44 43 41 03\r\r>", getMockResponse("2228C1"))
        assertEquals("61 01 02 80 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 28 28 28 28 03\r\r>", getMockResponse("2101"))
        assertEquals("NO DATA\r\r>", getMockResponse("0999"))
    }
}
