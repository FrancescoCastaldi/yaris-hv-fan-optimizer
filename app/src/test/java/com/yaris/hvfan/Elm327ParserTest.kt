package com.yaris.hvfan

import com.yaris.hvfan.obd.Elm327Protocol
import org.junit.Assert.*
import org.junit.Test

class Elm327ParserTest {

    @Test
    fun testCleanResponse_removesPromptAndSpaces() {
        val raw = 41 00 BE 1F B8 10 \r\r>
        val clean = Elm327Protocol.cleanResponse(raw)
        assertEquals(4100BE1FB810, clean)
    }

    @Test
    fun testCleanResponse_removesBusInitAndSearching() {
        val raw = SEARCHING...\rBUS INIT: OK\r62 28 C1 45 >
        val clean = Elm327Protocol.cleanResponse(raw)
        assertEquals(6228C145, clean)
    }

    @Test
    fun testIsError_detectsCommonObdErrors() {
        assertTrue(Elm327Protocol.isError(NO DATA\r>))
        assertTrue(Elm327Protocol.isError(CAN ERROR\r>))
        assertTrue(Elm327Protocol.isError(UNABLE TO CONNECT\r>))
        assertTrue(Elm327Protocol.isError(TIMEOUT))
        assertFalse(Elm327Protocol.isError(62 28 C1 40 40 40 40 >))
    }
}
