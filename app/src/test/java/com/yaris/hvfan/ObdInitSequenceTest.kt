package com.yaris.hvfan

import com.yaris.hvfan.obd.Elm327Protocol
import com.yaris.hvfan.obd.ToyotaYarisCommands
import org.junit.Assert.*
import org.junit.Test

/**
 * Regressioni sulla sequenza di inizializzazione ELM327 (bug di campo v2.9.7: l'adattatore
 * rispondeva ad AT RV con 13.0V ma ogni PID CAN, incluso lo standard 0100, tornava NO DATA).
 */
class ObdInitSequenceTest {

    /** AT ST hh: hh esadecimale x 4.096 ms. Il default di fabbrica ELM327 e' 0x32 (~205 ms). */
    private fun atStTimeoutMs(command: String): Double {
        val hex = command.trim().uppercase().removePrefix("AT ST").trim()
        return hex.toInt(16) * 4.096
    }

    private val elm327DefaultTimeoutMs = 0x32 * 4.096

    @Test
    fun testInitCommandsContainNoReceiveAddressFilter() {
        for (cmd in Elm327Protocol.INIT_COMMANDS) {
            val compact = cmd.uppercase().replace(" ", "")
            assertFalse(
                "AT CRA non deve comparire nella sequenza di init (scarta i frame in ingresso sui cloni): $cmd",
                compact.startsWith("ATCRA")
            )
        }
    }

    @Test
    fun testInitCommandsResetAutomaticReceiveFilter() {
        assertEquals("AT AR", Elm327Protocol.CMD_AUTO_RECEIVE)
        assertTrue(
            "AT AR deve essere presente per annullare eventuali filtri CRA residui",
            Elm327Protocol.INIT_COMMANDS.contains(Elm327Protocol.CMD_AUTO_RECEIVE)
        )
    }

    @Test
    fun testProtocolSelectionPrecedesTimingCommands() {
        val spIndex = Elm327Protocol.INIT_COMMANDS.indexOf("AT SP 6")
        assertTrue("AT SP 6 deve essere presente nella sequenza di init", spIndex >= 0)

        val stIndexes = Elm327Protocol.INIT_COMMANDS.withIndex()
            .filter { it.value.uppercase().startsWith("AT ST ") }
            .map { it.index }
        assertTrue("almeno un comando AT ST deve essere presente", stIndexes.isNotEmpty())

        for (stIndex in stIndexes) {
            assertTrue(
                "AT SP 6 (indice $spIndex) deve precedere AT ST (indice $stIndex)",
                spIndex < stIndex
            )
        }
    }

    @Test
    fun testNoTimeoutBelowElm327DefaultInInitAndHandshake() {
        val timeoutCommands = Elm327Protocol.INIT_COMMANDS.filter { it.uppercase().startsWith("AT ST ") } +
            listOf(
                Elm327Protocol.CMD_TIMEOUT_HANDSHAKE,
                Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU,
                Elm327Protocol.CMD_TIMEOUT_TELEMETRY
            )
        assertTrue(timeoutCommands.isNotEmpty())

        for (cmd in timeoutCommands) {
            val ms = atStTimeoutMs(cmd)
            assertTrue(
                "$cmd = ${ms}ms e' sotto il default ELM327 (${elm327DefaultTimeoutMs}ms)",
                ms >= elm327DefaultTimeoutMs
            )
        }

        assertEquals(614.4, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE), 0.1)
        assertEquals(409.6, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU), 0.1)
        assertEquals(204.8, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_TELEMETRY), 0.1)
    }

    @Test
    fun testProtocolNumberProbeCommand() {
        assertEquals("AT DPN", Elm327Protocol.CMD_PROTOCOL_NUMBER)
    }

    @Test
    fun testCleanResponseHandlesTotalLengthPrefixAndMultiFrame() {
        // Riga di lunghezza totale ELM327 ("014") seguita dai frame ISO-TP numerati "0:" e "1:"
        val raw = "014\r0:6228C1444546\r1:4341030000 00\r>"
        assertEquals("0146228C1444546434103000000", Elm327Protocol.cleanResponse(raw))

        val parsed = ToyotaYarisCommands.parseBatteryResponse(raw, false)
        assertNotNull(parsed)
        assertEquals(28.0, parsed!!.temp1, 0.1)
        assertEquals(29.0, parsed.temp2, 0.1)
        assertEquals(30.0, parsed.temp3, 0.1)
        assertEquals(27.0, parsed.temp4, 0.1)
        assertEquals(25.0, parsed.intakeTemp, 0.1)
        assertEquals(3, parsed.fanSpeedLevel)

        // Stessa risposta con header attivi (AT H1)
        val rawWithHeaders = "7EA1014 6228C1444546\r7EA21 4341030000 00\r>"
        val cleanWithHeaders = Elm327Protocol.cleanResponse(rawWithHeaders)
        assertEquals("7EA10146228C1444546" + "7EA21434103000000", cleanWithHeaders)
        assertFalse(cleanWithHeaders.contains(" "))
        assertTrue(cleanWithHeaders.contains("6228C1"))
    }

    @Test
    fun testBatteryNegativeUdsResponseIsRejected() {
        // 7F 22 31 = requestOutOfRange su Mode 22, 7F 22 12 = subFunctionNotSupported
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7F2231", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7F 22 31\r>", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7EA 03 7F 22 31\r>", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7F2212", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7F 22 12\r>", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7EA 03 7F 22 12\r>", false))
    }
}
