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
        // v3.0.7: esteso a 0xFF (~1044.5ms) per ricezione affidabile multi-frame pacco celle Denso ISO-TP
        assertEquals(1044.5, atStTimeoutMs(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU), 0.1)
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

    @Test
    fun testVgateCalibratedInitCommandsSequence() {
        assertEquals("AT Z", Elm327Protocol.VGATE_CALIBRATED_INIT_COMMANDS.first())
        assertEquals("AT ST 96", Elm327Protocol.VGATE_CALIBRATED_INIT_COMMANDS.last())

        assertEquals(
            listOf(
                "AT Z", "AT E0", "AT L0", "AT S0", "AT H0",
                "AT SP 6", "AT AT 1", "AT CAF 1", "AT AR", "AT ST 96"
            ),
            Elm327Protocol.VGATE_CALIBRATED_INIT_COMMANDS
        )

        for (cmd in Elm327Protocol.VGATE_CALIBRATED_INIT_COMMANDS) {
            val compact = cmd.uppercase().replace(" ", "")
            assertFalse(
                "AT CRA non deve comparire nella sequenza calibrata Vgate: $cmd",
                compact.startsWith("ATCRA")
            )
            assertFalse(
                "AT FC non deve comparire nella sequenza calibrata Vgate: $cmd",
                compact.startsWith("ATFC")
            )
        }

        assertTrue(
            "AT AR deve essere presente per garantire filtro hardware pulito",
            Elm327Protocol.VGATE_CALIBRATED_INIT_COMMANDS.contains("AT AR")
        )
    }

    @Test
    fun testStage1PositiveResponseDetection() {
        // Mode 01 PID 0C (RPM) positive detection
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "410C1F40"))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "41 0C 1F 40 >"))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "7E8 04 41 0C 1F 40 >"))
        // Engine stopped (0 RPM)
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "41 0C 00 00 >"))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "7E8 04 41 0C 00 00 >"))
        // Lowercase
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010c", "7e8 04 41 0c 1f 40 >"))
        // With SEARCHING
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", "SEARCHING...\r\n41 0C 1F 40 >"))
        // Negative / error
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", "NO DATA\r\n>"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", "CAN ERROR\r\n>"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", "TIMEOUT"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", "?\r\n>"))

        // Mode 01 PID 0D (Speed) positive detection
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010D", "41 0D 00 >")) // 0 km/h
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010D", "41 0D 32 >")) // 50 km/h
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010D", "7E8 03 41 0D 64 >"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010D", "NO DATA"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010D", "CAN ERROR"))

        // Mode 01 PID 00 (Supported PIDs) positive detection
        assertTrue(Elm327Protocol.isStage1PositiveResponse("0100", "41 00 BE 7F A8 11 >"))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("0100", "7E8 06 41 00 BE 7F A8 11 >"))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("0100", "NO DATA"))
    }

    @Test
    fun testStage2BatteryFallbackChainPidsParsing() {
        assertEquals(7, ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size)
        assertEquals("2101", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0])
        assertEquals("21C3", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[1])
        assertEquals("21C4", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[2])
        assertEquals("2161", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[3])
        assertEquals("2228C1", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[4])
        assertEquals("2228C0", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[5])
        assertEquals("220101", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[6])

        // Candidate 1: 2228C1
        val res28C1 = "62 28 C1 44 45 44 43 41 03"
        val parsed28C1 = ToyotaYarisCommands.parseBatteryResponse(res28C1, false)
        assertNotNull(parsed28C1)
        assertEquals(28.0, parsed28C1!!.temp1, 0.1)

        // Candidate 2: 2228C0
        val res28C0 = "62 28 C0 44 45 44 43 41 03"
        val parsed28C0 = ToyotaYarisCommands.parseBatteryResponse(res28C0, false)
        assertNotNull(parsed28C0)
        assertEquals(28.0, parsed28C0!!.temp1, 0.1)

        // Candidate 3: 2101 (Mode 21 response 6101)
        val res2101 = "61 01 44 45 44 43 41 03"
        val parsed2101 = ToyotaYarisCommands.parseBatteryResponse(res2101, false)
        assertNotNull(parsed2101)
        assertEquals(28.0, parsed2101!!.temp1, 0.1)

        // Candidate 4: 21C3 (Lithium pack response 61C3)
        val res21C3 = "61 C3 44 45 44 43 41 03"
        val parsed21C3 = ToyotaYarisCommands.parseBatteryResponse(res21C3, false)
        assertNotNull(parsed21C3)
        assertEquals(28.0, parsed21C3!!.temp1, 0.1)

        // Candidate 5: 2161 (Legacy KWP response 6161)
        val res2161 = "61 61 44 45 44 43 41 03"
        val parsed2161 = ToyotaYarisCommands.parseBatteryResponse(res2161, false)
        assertNotNull(parsed2161)
        assertEquals(28.0, parsed2161!!.temp1, 0.1)

        // Invalid candidate responses
        assertNull(ToyotaYarisCommands.parseBatteryResponse("NO DATA\r\n>", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("CAN ERROR\r\n>", false))
        assertNull(ToyotaYarisCommands.parseBatteryResponse("7F 22 31\r\n>", false))
    }

    @Test
    fun testCloneDongleAtResponsesAndMultilineSearchingCanError() {
        // Clone adapters returning '?' on AT AT 1 or AT CAF 1 or AT AR
        val cloneQuestionMark = "?\r\n>"
        assertEquals("?", Elm327Protocol.cleanResponse(cloneQuestionMark))
        assertTrue(Elm327Protocol.isError(cloneQuestionMark))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", cloneQuestionMark))

        // Multiline stream with SEARCHING... followed by UNABLE TO CONNECT
        val searchingUnableToConnect = "SEARCHING...\r\rUNABLE TO CONNECT\r\n>"
        assertEquals("UNABLETOCONNECT", Elm327Protocol.cleanResponse(searchingUnableToConnect))
        assertTrue(Elm327Protocol.isError(searchingUnableToConnect))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", searchingUnableToConnect))
        assertFalse(Elm327Protocol.hasSupportedPidsResponse(searchingUnableToConnect))

        // Multiline stream with SEARCHING... followed by CAN ERROR
        val searchingCanError = "SEARCHING...\r\rCAN ERROR\r\n>"
        assertEquals("CANERROR", Elm327Protocol.cleanResponse(searchingCanError))
        assertTrue(Elm327Protocol.isError(searchingCanError))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", searchingCanError))

        // Multiline stream with SEARCHING... followed by positive 0 RPM (car stopped in READY)
        val searchingReady0Rpm = "SEARCHING...\r\r41 0C 00 00\r\n>"
        assertEquals("410C0000", Elm327Protocol.cleanResponse(searchingReady0Rpm))
        assertFalse(Elm327Protocol.isError(searchingReady0Rpm))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010C", searchingReady0Rpm))

        // Multiline stream with SEARCHING... followed by positive 0 km/h
        val searchingReady0Speed = "SEARCHING...\r\r7E8 03 41 0D 00\r\n>"
        assertEquals("7E803410D00", Elm327Protocol.cleanResponse(searchingReady0Speed))
        assertFalse(Elm327Protocol.isError(searchingReady0Speed))
        assertTrue(Elm327Protocol.isStage1PositiveResponse("010D", searchingReady0Speed))
    }

    @Test
    fun testStrayPromptRejectionAndCleanResponse() {
        // Bare prompt '>' or whitespace with prompt
        val barePrompt = ">\r\n"
        assertEquals("", Elm327Protocol.cleanResponse(barePrompt))
        assertTrue(Elm327Protocol.isError(barePrompt))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010C", barePrompt))

        val strayDrainPrompt = "\r\n>"
        assertEquals("", Elm327Protocol.cleanResponse(strayDrainPrompt))
        assertTrue(Elm327Protocol.isError(strayDrainPrompt))
        assertFalse(Elm327Protocol.isStage1PositiveResponse("010D", strayDrainPrompt))

        // Content emptiness check for orphan prompt detection
        val contentOnlyPrompt = "\r\n>".replace(">", "").replace("\r", "").replace("\n", "").trim()
        assertTrue("Un prompt orfano privo di dati deve risultare vuoto", contentOnlyPrompt.isEmpty())

        val validPayloadPrompt = "41 0C 1F 40\r\n>".replace(">", "").replace("\r", "").replace("\n", "").trim()
        assertFalse("Una risposta valida con dati non deve risultare vuota", validPayloadPrompt.isEmpty())
    }

    @Test
    fun testMultilineCanResponseWithSecondaryEcuNoDataAndErrors() {
        // Multi-ECU response where engine responds positive on 010C but secondary ECU returns NO DATA
        val multiline010C = "7E8 04 41 0C 00 00\r\nNO DATA\r\n>"
        assertTrue("isStage1PositiveResponse deve accettare frame positivo anche in presenza di NO DATA",
            Elm327Protocol.isStage1PositiveResponse("010C", multiline010C))
        assertEquals(0, ToyotaYarisCommands.parseEngineRpm(multiline010C))
        assertTrue(Elm327Protocol.isValidCanResponse(multiline010C))

        // Multi-ECU response on 010D with secondary ECU CAN ERROR
        val multiline010D = "7E8 03 41 0D 32\r\nCAN ERROR\r\n>"
        assertTrue("isStage1PositiveResponse deve accettare velocità positiva anche con CAN ERROR",
            Elm327Protocol.isStage1PositiveResponse("010D", multiline010D))
        assertEquals(50, ToyotaYarisCommands.parseVehicleSpeed(multiline010D))
        assertTrue(Elm327Protocol.isValidCanResponse(multiline010D))

        // Broadcast 0100 with NO DATA from non-powertrain ECU
        val multiline0100 = "SEARCHING...\r\n7E8 06 41 00 BE 7F A8 11\r\nNO DATA\r\n>"
        assertTrue("hasSupportedPidsResponse deve trovare 4100 anche con SEARCHING e NO DATA",
            Elm327Protocol.hasSupportedPidsResponse(multiline0100))

        // isValidCanResponse where NO DATA is the first line
        val noDataFirst = "NO DATA\r\n7E8 04 41 0C 00 00\r\n>"
        assertTrue("isValidCanResponse deve riconoscere frame esadecimale valido anche se preceduto da NO DATA",
            Elm327Protocol.isValidCanResponse(noDataFirst))

        // Battery UDS multi-frame with stray NO DATA at end
        val batteryWithNoData = "7EA 10 17 62 28 C1 44 45 44 43 41 03\r\nNO DATA\r\n>"
        val parsedBattery = ToyotaYarisCommands.parseBatteryResponse(batteryWithNoData, false)
        assertNotNull("parseBatteryResponse non deve fallire se la risposta contiene NO DATA di coda", parsedBattery)
        assertEquals(28.0, parsedBattery!!.temp1, 0.1)

        assertTrue(Elm327Protocol.isUdsPositiveResponse(batteryWithNoData, "22"))
    }
}
