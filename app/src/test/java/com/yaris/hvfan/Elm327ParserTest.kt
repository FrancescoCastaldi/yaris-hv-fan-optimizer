package com.yaris.hvfan

import com.yaris.hvfan.obd.Elm327Protocol
import org.junit.Assert.*
import org.junit.Test

class Elm327ParserTest {

    @Test
    fun testCleanResponse() {
        val raw = "62 28 C1 40 42 \r\n>"
        val cleaned = Elm327Protocol.cleanResponse(raw)
        assertEquals("6228C14042", cleaned)
    }

    @Test
    fun testIsError() {
        assertTrue(Elm327Protocol.isError("NO DATA"))
        assertTrue(Elm327Protocol.isError("CAN ERROR"))
        assertTrue(Elm327Protocol.isError("UNABLE TO CONNECT"))
        assertTrue(Elm327Protocol.isError("TIMEOUT"))
        assertFalse(Elm327Protocol.isError("6228C14042"))
    }

    @Test
    fun testExtractUdsNrc() {
        // ATH0 compatto
        val nrc11 = Elm327Protocol.extractUdsNrc("7F2211")
        assertNotNull(nrc11)
        assertEquals("22", nrc11!!.serviceId)
        assertEquals("11", nrc11.nrc)

        // ATH0 con spazi
        val nrc12 = Elm327Protocol.extractUdsNrc("7F 22 12\r\n>")
        assertNotNull(nrc12)
        assertEquals("22", nrc12!!.serviceId)
        assertEquals("12", nrc12.nrc)

        // ATH1 con header CAN e spazi
        val nrc22 = Elm327Protocol.extractUdsNrc("7EA 03 7F 22 22\r\n>")
        assertNotNull(nrc22)
        assertEquals("22", nrc22!!.serviceId)
        assertEquals("22", nrc22.nrc)

        // ATH1 compatto con CAN ID
        val nrc78 = Elm327Protocol.extractUdsNrc("7EA037F2278")
        assertNotNull(nrc78)
        assertEquals("22", nrc78!!.serviceId)
        assertEquals("78", nrc78.nrc)

        // Mode 21 NRC
        val nrc21 = Elm327Protocol.extractUdsNrc("7EA 03 7F 21 11\r\n>")
        assertNotNull(nrc21)
        assertEquals("21", nrc21!!.serviceId)
        assertEquals("11", nrc21.nrc)

        // Non-NRC positive responses
        assertNull(Elm327Protocol.extractUdsNrc("41 0C 1F 40\r\n>"))
        assertNull(Elm327Protocol.extractUdsNrc("62 28 C1 44 45 44 43 41 03\r\n>"))
        assertNull(Elm327Protocol.extractUdsNrc("NO DATA\r\n>"))
        assertNull(Elm327Protocol.extractUdsNrc("CAN ERROR\r\n>"))
    }
}
