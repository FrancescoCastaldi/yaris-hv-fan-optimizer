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
}
