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
                Elm327Protocol.CMD_TIMEOUT_TELEMETRY,
                Elm327Protocol.CMD_TIMEOUT_ECU_CODING
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
        assertEquals(614.4, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_ECU_CODING), 0.1)
        // v2.9.14: esteso da 0x64 (~409.6ms) a 0xC8 (~819.2ms) per dare margine ai cloni
        // ELM327/Vlinker sul multi-frame UDS 2228C1 (vedi CHANGELOG v2.9.14).
        assertEquals(819.2, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU), 0.1)
        assertEquals(204.8, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_TELEMETRY), 0.1)
    }

    @Test
    fun testProtocolNumberProbeCommand() {
        assertEquals("AT DPN", Elm327Protocol.CMD_PROTOCOL_NUMBER)
    }

    @Test
    fun testFunctionalBroadcastAndRealCanProbeValidation() {
        assertEquals("7DF", ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST)
        assertEquals("AT SP 0", Elm327Protocol.PROTOCOL_FALLBACK)

        assertTrue(Elm327Protocol.hasSupportedPidsResponse("41 00 BE 7F A8 11 >"))
        assertTrue(Elm327Protocol.hasSupportedPidsResponse("SEARCHING...\r\n7E8 06 41 00 BE 7F A8 11 >"))
        assertFalse(Elm327Protocol.hasSupportedPidsResponse("OK\r\n>"))
        assertFalse(Elm327Protocol.hasSupportedPidsResponse("NO DATA\r\n>"))
        assertFalse(Elm327Protocol.hasSupportedPidsResponse("410C1F40"))
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
        val parsedWithHeaders = ToyotaYarisCommands.parseBatteryResponse(rawWithHeaders, false)
        assertNotNull(parsedWithHeaders)
        assertEquals(28.0, parsedWithHeaders!!.temp1, 0.1)
        assertEquals(29.0, parsedWithHeaders.temp2, 0.1)
        assertEquals(30.0, parsedWithHeaders.temp3, 0.1)
        assertEquals(27.0, parsedWithHeaders.temp4, 0.1)
        assertEquals(25.0, parsedWithHeaders.intakeTemp, 0.1)
        assertEquals(3, parsedWithHeaders.fanSpeedLevel)
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

    @Test
    fun testHybridAssistantHandshakeSequence() {
        assertEquals("AT WS", Elm327Protocol.CMD_WARM_START)
        assertEquals("AT H1", Elm327Protocol.CMD_HEADERS_ON)
        assertEquals("AT RV", Elm327Protocol.CMD_VOLTAGE)
        assertEquals("03", Elm327Protocol.CMD_PROBE_DTC)
        assertEquals("03", ToyotaYarisCommands.CMD_PROBE_DTC)

        // Verifiche composizione INIT_COMMANDS Hybrid Assistant
        assertEquals("AT WS", Elm327Protocol.INIT_COMMANDS.first())
        assertEquals("AT ST 96", Elm327Protocol.INIT_COMMANDS.last())
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT E0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT SP 6"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AT 1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT H1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT L0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT S0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT CAF 1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AR"))

        // Probe Mode 03 validation
        assertTrue(Elm327Protocol.isMode03Response("7E8 06 43 00 00 00 00 00 00 >"))
        assertTrue(Elm327Protocol.isMode03Response("43 00 00 00 00 00 00 >"))
        // Multi-ECU response: primary powertrain 7E8 responds positive, secondary 743 responds NRC
        assertTrue(Elm327Protocol.isMode03Response("7E8 06 43 00 00 00 00 00\r743 03 7F 03 12\r>"))
        // Multi-frame ISO-TP Mode 03 response (First Frame + Consecutive Frame with multiple DTCs)
        assertTrue(Elm327Protocol.isMode03Response("7E8 10 09 43 04 01 23 45 67\r7E8 21 89 00 00 00 00 00\r>"))
        // Lowercase hexadecimal support
        assertTrue(Elm327Protocol.isMode03Response("7e8 06 43 00 00 00 00 00 >"))
        // Positive response with DTC containing byte 7F (e.g. DTC P017F)
        assertTrue(Elm327Protocol.isMode03Response("43 01 01 7F >"))
        assertTrue(Elm327Protocol.isMode03Response("NO DATA"))
        assertFalse(Elm327Protocol.isMode03Response("CAN ERROR"))
        assertFalse(Elm327Protocol.isMode03Response("7E8 03 7F 03 12 >")) // NRC 12 (SubFunctionNotSupported)
        assertFalse(Elm327Protocol.isMode03Response("743 03 7F 03 12 >")) // NRC containing '43' in CAN ID
        assertFalse(Elm327Protocol.isMode03Response("743 02 01 02 >")) // Non-mode-03 containing 43 in CAN ID
    }

    @Test
    fun testPidParsersWithAndWithoutHeaders() {
        // Mode 01 PID con e senza header ATH1 (7E8)
        assertEquals(100, ToyotaYarisCommands.parseVehicleSpeed("7E8 03 41 0D 64 >"))
        assertEquals(100, ToyotaYarisCommands.parseVehicleSpeed("41 0D 64 >"))

        assertEquals(80f, ToyotaYarisCommands.parseCoolantTemp("7E8 03 41 05 78 >") ?: 0f, 0.1f)
        assertEquals(80f, ToyotaYarisCommands.parseCoolantTemp("41 05 78 >") ?: 0f, 0.1f)

        assertEquals(25f, ToyotaYarisCommands.parseIntakeAirTemp("7E8 03 41 0F 41 >") ?: 0f, 0.1f)
        assertEquals(25f, ToyotaYarisCommands.parseIntakeAirTemp("41 0F 41 >") ?: 0f, 0.1f)

        assertEquals(2000, ToyotaYarisCommands.parseEngineRpm("7E8 04 41 0C 1F 40 >"))
        assertEquals(2000, ToyotaYarisCommands.parseEngineRpm("41 0C 1F 40 >"))

        assertEquals(16.0f, ToyotaYarisCommands.parseTimingAdvance("7E8 03 41 0E A0 >") ?: 0f, 0.1f)
        assertEquals(16.0f, ToyotaYarisCommands.parseTimingAdvance("41 0E A0 >") ?: 0f, 0.1f)

        assertEquals(50.19f, ToyotaYarisCommands.parseEngineLoad("7E8 03 41 04 80 >") ?: 0f, 0.5f)
        assertEquals(50.19f, ToyotaYarisCommands.parseEngineLoad("41 04 80 >") ?: 0f, 0.5f)

        assertEquals(40.0f, ToyotaYarisCommands.parseThrottlePos("7E8 03 41 11 66 >") ?: 0f, 0.5f)
        assertEquals(40.0f, ToyotaYarisCommands.parseThrottlePos("41 11 66 >") ?: 0f, 0.5f)

        val multiWithHeader = ToyotaYarisCommands.parseMultiPidEngineResponse("7E8 08 41 0D 44 0C 1F 40 11 66 >")
        assertNotNull(multiWithHeader)
        assertEquals(68, multiWithHeader!!.speedKmh)
        assertEquals(2000, multiWithHeader.engineRpm)
        assertEquals(40.0f, multiWithHeader.throttlePercent!!, 0.5f)

        // Multi-frame ISO-TP Multi-PID con header CAN ATH1 (CF 7E8 21 tra byte RPM e throttle)
        val multiIsoTpWithHeaders = "7E8 10 08 41 0D 44 0C 1F\r7E8 21 40 11 66 00 00 00\r>"
        val parsedIsoTp = ToyotaYarisCommands.parseMultiPidEngineResponse(multiIsoTpWithHeaders)
        assertNotNull(parsedIsoTp)
        assertEquals(68, parsedIsoTp!!.speedKmh)
        assertEquals(2000, parsedIsoTp.engineRpm)
        assertEquals(40.0f, parsedIsoTp.throttlePercent!!, 0.5f)

        val multiWithoutHeader = ToyotaYarisCommands.parseMultiPidEngineResponse("41 0D 44 0C 1F 40 11 66 >")
        assertNotNull(multiWithoutHeader)
        assertEquals(68, multiWithoutHeader!!.speedKmh)
        assertEquals(2000, multiWithoutHeader.engineRpm)
        assertEquals(40.0f, multiWithoutHeader.throttlePercent!!, 0.5f)
    }

    @Test
    fun testBatteryResponseStandardTngaFrame() {
        val rawBattery = "62 28 C1 44 45 44 43 41 03"
        val parsed = ToyotaYarisCommands.parseBatteryResponse(rawBattery, false)
        assertNotNull("La risposta per 2228C1 standard TNGA deve essere valida per parseBatteryResponse", parsed)
        assertEquals(28.0, parsed!!.temp1, 0.1)
        assertEquals(29.0, parsed.temp2, 0.1)
        assertEquals(3, parsed.fanSpeedLevel)
    }

    @Test
    fun testBatteryResponseWithCorruptedDataHandledSafely() {
        // Frame con caratteri non esadecimali o frammenti corrotti
        val corrupt = "62 28 C1 44 ZZ 44 43 41 03"
        val parsed = ToyotaYarisCommands.parseBatteryResponse(corrupt, false)
        assertNull("Frame batteria con caratteri corrotti deve ritornare null in modo sicuro senza crash", parsed)
    }

    @Test
    fun testCleanResponseWithSearchingWithoutDots() {
        val raw = "SEARCHING\r7E8 06 41 00 BE 3F B8 11 >"
        assertEquals("7E8064100BE3FB811", Elm327Protocol.cleanResponse(raw))
    }

    @Test
    fun testCleanResponseWithSpacedLinePrefixes() {
        // Spazi prima del colon nei frame numerati
        val raw = "0 : 6228C1444546\r1 : 4341030000 00\r>"
        val clean = Elm327Protocol.cleanResponse(raw)
        assertEquals("6228C1444546434103000000", clean)
        val parsed = ToyotaYarisCommands.parseBatteryResponse(raw, false)
        assertNotNull(parsed)
        assertEquals(28.0, parsed!!.temp1, 0.1)
    }

    @Test
    fun testCanErrorMessagesAndAdapterAlerts() {
        // Test che tutti i messaggi di errore e alert hardware vengano identificati come isError
        val errors = listOf(
            "CAN ERROR",
            "BUS BUSY",
            "BUFFER FULL",
            "DATA ERROR",
            "<DATA ERROR",
            "ERR94",
            "FB ERROR",
            "BUS INIT: ERROR",
            "UNABLE TO CONNECT",
            "LP ALERT",
            "ACT ALERT",
            "LV RESET"
        )
        for (err in errors) {
            assertTrue("Dovrebbe essere identificato come errore: $err", Elm327Protocol.isError(err))
            assertNull("PID Speed non deve parsare stringhe di errore: $err", ToyotaYarisCommands.parseVehicleSpeed(err))
            assertNull("PID Coolant non deve parsare stringhe di errore: $err", ToyotaYarisCommands.parseCoolantTemp(err))
            assertNull("PID RPM non deve parsare stringhe di errore: $err", ToyotaYarisCommands.parseEngineRpm(err))
        }

        // Test che un errore accodato a un PID finto non produca valori validi
        assertNull(ToyotaYarisCommands.parseCoolantTemp("4105 ERROR"))
        assertNull(ToyotaYarisCommands.parseVehicleSpeed("410D CAN ERROR"))
    }
}
