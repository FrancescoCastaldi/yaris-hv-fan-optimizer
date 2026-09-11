package com.yaris.hvfan

import com.yaris.hvfan.data.AutomotiveProtocolDecoder
import com.yaris.hvfan.data.ObdLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdLoggerTest {

    @Test
    fun testHeaderExtractionAndEcuMapping() {
        assertEquals("7E0", AutomotiveProtocolDecoder.extractHeaderFromCommand("AT SH 7E0"))
        assertEquals("7E2", AutomotiveProtocolDecoder.extractHeaderFromCommand("ATSH7E2"))
        assertEquals("7C0", AutomotiveProtocolDecoder.extractHeaderFromCommand("at sh 7c0"))

        assertEquals("Engine / Hybrid Powertrain ECU", AutomotiveProtocolDecoder.getEcuName("7E0"))
        assertEquals("HV Battery Management ECU (Denso)", AutomotiveProtocolDecoder.getEcuName("7E2"))
        assertEquals("Combination Meter ECU (Cluster)", AutomotiveProtocolDecoder.getEcuName("7C0"))
        assertEquals("Main Body / Gateway ECU", AutomotiveProtocolDecoder.getEcuName("750"))
        assertEquals("Air Conditioning ECU", AutomotiveProtocolDecoder.getEcuName("7C4"))
        assertEquals("ADAS / TSS 2.5 Driving Assist / EPS", AutomotiveProtocolDecoder.getEcuName("7A0"))

        assertEquals("7E8", AutomotiveProtocolDecoder.getResponseIdForHeader("7E0"))
        assertEquals("7EA", AutomotiveProtocolDecoder.getResponseIdForHeader("7E2"))
        assertEquals("7C8", AutomotiveProtocolDecoder.getResponseIdForHeader("7C0"))
        assertEquals("758", AutomotiveProtocolDecoder.getResponseIdForHeader("750"))
        assertEquals("7CC", AutomotiveProtocolDecoder.getResponseIdForHeader("7C4"))
        assertEquals("7A8", AutomotiveProtocolDecoder.getResponseIdForHeader("7A0"))
    }

    @Test
    fun testDecodeTxCommands() {
        // AT Commands
        val atz = AutomotiveProtocolDecoder.decodeTx("AT Z")
        assertTrue(atz.contains("Reset chip ELM327"))

        val atsp = AutomotiveProtocolDecoder.decodeTx("AT SP 6")
        assertTrue(atsp.contains("ISO 15765-4 CAN"))

        val atsh = AutomotiveProtocolDecoder.decodeTx("AT SH 7E2")
        assertTrue(atsh.contains("HV Battery Management ECU"))

        // Wake
        val wake = AutomotiveProtocolDecoder.decodeTx("\\r\\r (WAKE)")
        assertTrue(wake.contains("Sequenza di risveglio"))

        // Mode 01 PID
        val speed = AutomotiveProtocolDecoder.decodeTx("010D")
        assertTrue(speed.contains("Vehicle Speed"))

        val rpmAndSpeed = AutomotiveProtocolDecoder.decodeTx("01 0D 0C")
        assertTrue(rpmAndSpeed.contains("Vehicle Speed"))
        assertTrue(rpmAndSpeed.contains("Engine RPM"))

        // UDS 10 Diagnostic Session
        val sessExt = AutomotiveProtocolDecoder.decodeTx("10 03")
        assertTrue(sessExt.contains("Extended Diagnostic Session"))

        // UDS 22 Read DID
        val readDid = AutomotiveProtocolDecoder.decodeTx("22 A0 01")
        assertTrue(readDid.contains("ReadDID: 0xA001"))
        assertTrue(readDid.contains("Reverse Buzzer"))

        // UDS 2E Write DID
        val writeDid = AutomotiveProtocolDecoder.decodeTx("2E A0 01 00")
        assertTrue(writeDid.contains("WriteDID: 0xA001"))
        assertTrue(writeDid.contains("Singolo Bip"))

        // UDS 2F IOControl
        val ioControl = AutomotiveProtocolDecoder.decodeTx("2F A0 01 03")
        assertTrue(ioControl.contains("IOControl: 0xA001"))

        // UDS 31 RoutineControl
        val routine = AutomotiveProtocolDecoder.decodeTx("31 01 02 01")
        assertTrue(routine.contains("RoutineControl: Start Routine"))

        // UDS 3E TesterPresent
        val tester = AutomotiveProtocolDecoder.decodeTx("3E 00")
        assertTrue(tester.contains("TesterPresent"))

        // Mode 21 Toyota
        val m21 = AutomotiveProtocolDecoder.decodeTx("21 01")
        assertTrue(m21.contains("Hybrid Powertrain Live Telemetry"))

        // Mode 30 Active Test
        val m30 = AutomotiveProtocolDecoder.decodeTx("30 08 06")
        assertTrue(m30.contains("Active Test HV Battery Fan Control, Level 6"))
    }

    @Test
    fun testDecodeRxResponses() {
        // Positive ACK
        val sessAck = AutomotiveProtocolDecoder.decodeRx("50 03")
        assertTrue(sessAck.contains("SessionControl ACK: Extended Session (0x03)"))

        val readAck = AutomotiveProtocolDecoder.decodeRx("62 A0 01 00")
        assertTrue(readAck.contains("ReadDID ACK: DID 0xA001"))
        assertTrue(readAck.contains("Singolo Bip"))

        val writeAck = AutomotiveProtocolDecoder.decodeRx("6E A0 01")
        assertTrue(writeAck.contains("WriteDID ACK: DID 0xA001"))

        val testerAck = AutomotiveProtocolDecoder.decodeRx("7E 00")
        assertTrue(testerAck.contains("TesterPresent ACK"))

        // Mode 01 ACK
        val speedAck = AutomotiveProtocolDecoder.decodeRx("41 0D 28")
        assertTrue(speedAck.contains("Speed = 40 km/h"))

        val rpmAck = AutomotiveProtocolDecoder.decodeRx("41 0C 0F A0")
        assertTrue(rpmAck.contains("RPM = 1000 rpm"))

        val multiAck = AutomotiveProtocolDecoder.decodeRx("41 0D 28 0C 0F A0")
        assertTrue(multiAck.contains("Speed = 40 km/h"))
        assertTrue(multiAck.contains("RPM = 1000 rpm"))

        // NRC Decoded
        val nrc31 = AutomotiveProtocolDecoder.decodeRx("7F 22 31")
        assertTrue(nrc31.contains("NRC 0x31: requestOutOfRange"))
        assertTrue(nrc31.contains("ReadDataByIdentifier"))

        val nrc22 = AutomotiveProtocolDecoder.decodeRx("7F 10 22")
        assertTrue(nrc22.contains("NRC 0x22: conditionsNotCorrect"))
        assertTrue(nrc22.contains("DiagnosticSessionControl"))

        val nrc11 = AutomotiveProtocolDecoder.decodeRx("7F 2E 11")
        assertTrue(nrc11.contains("NRC 0x11: serviceNotSupported"))
        assertTrue(nrc11.contains("WriteDataByIdentifier"))

        val nrc78 = AutomotiveProtocolDecoder.decodeRx("7F 22 78")
        assertTrue(nrc78.contains("NRC 0x78: requestCorrectlyReceived-ResponsePending"))

        // ISO-TP Framing
        val fc = AutomotiveProtocolDecoder.decodeRx("30 00 00")
        assertTrue(fc.contains("Flow Control: CTS"))

        val ff = AutomotiveProtocolDecoder.decodeRx("10 28 61 01 02 80 00 20")
        assertTrue(ff.contains("ISO-TP First Frame: totalLen=40 bytes"))

        val cf = AutomotiveProtocolDecoder.decodeRx("21 00 20 00 20 00 20 00")
        assertTrue(cf.contains("ISO-TP Consecutive Frame: seq=1"))
    }

    @Test
    fun testReverseEngineeringTracker() {
        val tracker = AutomotiveProtocolDecoder.ReverseEngineeringTracker()
        tracker.recordTx("AT SH 7C0", "7C0")

        // Read DID A001
        tracker.recordTx("22 A0 01", "7C0")
        tracker.recordRx("62 A0 01 00", "7C0", "22 A0 01")

        // Write DID A001 with 01
        tracker.recordTx("2E A0 01 01", "7C0")
        tracker.recordRx("6E A0 01", "7C0", "2E A0 01 01")

        // Reject DID A099
        tracker.recordTx("22 A0 99", "7C0")
        tracker.recordRx("7F 22 31", "7C0", "22 A0 99")

        val summary = tracker.generateSummary()
        assertTrue(summary.contains("=== REVERSE ENGINEERING SUMMARY: DISCOVERED DIDs & COMMANDS ==="))
        assertTrue(summary.contains("[ECU 7C0 - Combination Meter ECU (Cluster)]"))
        assertTrue(summary.contains("DID 0xA001 (Combination Meter Reverse Buzzer): Payload=00 [Singolo Bip (Comfort)]"))
        assertTrue(summary.contains("WRITTEN DIDs (0x2E -> 0x6E ACK):"))
        assertTrue(summary.contains("Written Payload=01 [Continuo (Standard)] -> Command: 2E A0 01 01"))
        assertTrue(summary.contains("REJECTED SERVICES (0x7F NRC):"))
        assertTrue(summary.contains("ReadDataByIdentifier (DID/Param 0xA099): [NRC 0x31: requestOutOfRange]"))
    }

    @Test
    fun testCanDumpFormatting() {
        val ts = 1750000000123L
        val txDump = AutomotiveProtocolDecoder.formatCanDumpTx(ts, "010D", "7E0")
        assertNotNull(txDump)
        assertTrue(txDump!!.startsWith("(1750000000.123000) can0 7E0#"))
        assertTrue(txDump.endsWith("02010D0000000000"))

        val rxDumpList = AutomotiveProtocolDecoder.formatCanDumpRx(ts, "41 0D 28", "7E0")
        assertEquals(1, rxDumpList.size)
        assertTrue(rxDumpList[0].startsWith("(1750000000.123000) can0 7E8#"))
        assertTrue(rxDumpList[0].endsWith("03410D2800000000"))
    }

    @Test
    fun testObdLoggerHeaderTracking() {
        ObdLogger.setActiveHeader("7E2")
        assertEquals("7E2", ObdLogger.getActiveHeader())

        ObdLogger.logTx("AT SH 750")
        assertEquals("750", ObdLogger.getActiveHeader())
    }
}
