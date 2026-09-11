package com.yaris.sniffer

import com.yaris.sniffer.server.BridgeLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BridgeLoggerTest {

    private lateinit var tempDir: File
    private lateinit var logger: BridgeLogger

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "sniffer_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        logger = BridgeLogger(tempDir)
    }

    @After
    fun tearDown() {
        logger.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitialState() {
        assertFalse(logger.isRecording.value)
        assertEquals(0L, logger.txCount.value)
        assertEquals(0L, logger.rxCount.value)
        assertTrue(logger.recentLogs.value.isEmpty())
    }

    @Test
    fun testTxAndRxCounting() {
        logger.logTx("0100")
        logger.logTx("2228C1")
        logger.logRx("41 00 BE 3F B8 11")

        assertEquals(2L, logger.txCount.value)
        assertEquals(1L, logger.rxCount.value)
        assertEquals(3, logger.recentLogs.value.size)
        assertTrue(logger.recentLogs.value[0].contains("TX >>> 0100"))
        assertTrue(logger.recentLogs.value[1].contains("TX >>> 2228C1"))
        assertTrue(logger.recentLogs.value[2].contains("RX <<< 41 00 BE 3F B8 11"))
    }

    @Test
    fun testRecordingToFile() {
        logger.startRecording("Vgate iCar Pro (00:11:22:33:44:55)")
        assertTrue(logger.isRecording.value)

        logger.logTx("ATZ")
        logger.logRx("ELM327 v1.5")
        logger.logTx("ATSP6")
        logger.logRx("OK")

        val file = logger.getLogFile()
        assertNotNull(file)
        assertTrue(file!!.exists())

        logger.pauseRecording()
        assertFalse(logger.isRecording.value)

        val content = file.readText()
        assertTrue(content.contains("YARIS OBD BRIDGE & SNIFFER"))
        assertTrue(content.contains("Vgate iCar Pro"))
        assertTrue(content.contains("TX >>> ATZ"))
        assertTrue(content.contains("RX <<< ELM327 v1.5"))
        assertTrue(content.contains("TX >>> ATSP6"))
        assertTrue(content.contains("RX <<< OK"))
    }

    @Test
    fun testFormatTxAndRxEntry() {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val ts = 1750000000000L
        val tx = BridgeLogger.formatTxEntry(ts, "300806", df)
        assertTrue(tx.startsWith("[$ts]"))
        assertTrue(tx.endsWith("TX >>> 300806"))

        val rx = BridgeLogger.formatRxEntry(ts, "7F 30 12", df)
        assertTrue(rx.startsWith("[$ts]"))
        assertTrue(rx.endsWith("RX <<< 7F 30 12"))
    }

    @Test
    fun testClearUiLogsAndReset() {
        logger.logTx("010C")
        logger.logRx("41 0C 0F A0")
        assertEquals(2, logger.recentLogs.value.size)

        logger.clearUiLogs()
        assertTrue(logger.recentLogs.value.isEmpty())

        logger.resetCounters()
        assertEquals(0L, logger.txCount.value)
        assertEquals(0L, logger.rxCount.value)
    }

    @Test
    fun testPauseAndResumeContinuity() {
        logger.startRecording("TestDongle")
        val file1 = logger.getLogFile()
        assertNotNull(file1)

        logger.logTx("ATZ")
        logger.pauseRecording()
        assertFalse(logger.isRecording.value)

        // Resuming must not create a new file or wipe the existing file
        logger.startRecording("TestDongle")
        assertTrue(logger.isRecording.value)
        val file2 = logger.getLogFile()
        assertEquals(file1?.absolutePath, file2?.absolutePath)

        logger.logTx("0100")
        logger.close()

        val content = file2!!.readText()
        assertTrue(content.contains("TX >>> ATZ"))
        assertTrue(content.contains("REGISTRAZIONE RIPRESA"))
        assertTrue(content.contains("TX >>> 0100"))
    }

    @Test
    fun testResetSession() {
        logger.startRecording("TestDongle")
        logger.logTx("ATZ")
        logger.logRx("OK")
        assertEquals(1L, logger.txCount.value)
        assertEquals(1L, logger.rxCount.value)

        logger.resetSession()
        assertFalse(logger.isRecording.value)
        assertEquals(0L, logger.txCount.value)
        assertEquals(0L, logger.rxCount.value)
        assertTrue(logger.recentLogs.value.isEmpty())
    }

    @Test
    fun testSemanticDecodingAndHeaderTracking() {
        logger.logTx("AT SH 7C0")
        assertEquals("7C0", logger.getCurrentHeader())
        assertTrue(logger.recentLogs.value.last().contains("Combination Meter ECU"))

        // Mode 01 PID speed
        logger.logTx("010D")
        assertTrue(logger.recentLogs.value.last().contains("Vehicle Speed"))
        logger.logRx("41 0D 28")
        assertTrue(logger.recentLogs.value.last().contains("Speed = 40 km/h"))

        // UDS 10 Diagnostic Session Control
        logger.logTx("1003")
        assertTrue(logger.recentLogs.value.last().contains("Extended Diagnostic Session"))
        logger.logRx("50 03")
        assertTrue(logger.recentLogs.value.last().contains("Extended Session (0x03)"))

        // UDS 22 Read DID A001
        logger.logTx("22 A0 01")
        assertTrue(logger.recentLogs.value.last().contains("Reverse Buzzer"))
        logger.logRx("62 A0 01 00")
        assertTrue(logger.recentLogs.value.last().contains("Reverse Buzzer"))
        assertTrue(logger.recentLogs.value.last().contains("Singolo Bip"))

        // UDS 2E Write DID A001 with 01
        logger.logTx("2E A0 01 01")
        assertTrue(logger.recentLogs.value.last().contains("WriteDID: 0xA001"))
        logger.logRx("6E A0 01")
        assertTrue(logger.recentLogs.value.last().contains("WriteDID ACK: DID 0xA001"))

        // UDS 3E TesterPresent
        logger.logTx("3E 00")
        assertTrue(logger.recentLogs.value.last().contains("TesterPresent"))
        logger.logRx("7E 00")
        assertTrue(logger.recentLogs.value.last().contains("TesterPresent ACK"))

        // UDS 2F IOControl
        logger.logTx("2F A0 01 03")
        assertTrue(logger.recentLogs.value.last().contains("IOControl: 0xA001"))

        // UDS 31 RoutineControl
        logger.logTx("31 01 02 01")
        assertTrue(logger.recentLogs.value.last().contains("Start Routine"))
    }

    @Test
    fun testNrcNegativeResponseDecoding() {
        logger.logTx("AT SH 750")
        assertEquals("750", logger.getCurrentHeader())

        // Negative Response 7F 22 31 (requestOutOfRange)
        logger.logTx("22 B0 99")
        logger.logRx("7F 22 31")
        val rxLog1 = logger.recentLogs.value.last()
        assertTrue(rxLog1.contains("NRC 0x31: requestOutOfRange"))
        assertTrue(rxLog1.contains("ReadDataByIdentifier"))

        // Negative Response 7F 10 22 (conditionsNotCorrect)
        logger.logTx("10 02")
        logger.logRx("7F 10 22")
        val rxLog2 = logger.recentLogs.value.last()
        assertTrue(rxLog2.contains("NRC 0x22: conditionsNotCorrect"))
        assertTrue(rxLog2.contains("DiagnosticSessionControl"))

        // Negative Response 7F 2E 11 (serviceNotSupported)
        logger.logTx("2E B0 04 09")
        logger.logRx("7F 2E 11")
        val rxLog3 = logger.recentLogs.value.last()
        assertTrue(rxLog3.contains("NRC 0x11: serviceNotSupported"))

        // Negative Response 7F 22 78 (responsePending)
        logger.logTx("22 28 C1")
        logger.logRx("7F 22 78")
        val rxLog4 = logger.recentLogs.value.last()
        assertTrue(rxLog4.contains("NRC 0x78: requestCorrectlyReceived-ResponsePending"))
    }

    @Test
    fun testIsoTpFramingDecoding() {
        // Flow control
        logger.logTx("30 00 00")
        assertTrue(logger.recentLogs.value.last().contains("Flow Control: CTS"))

        // First frame
        logger.logRx("10 28 61 01 02 80 00 20")
        assertTrue(logger.recentLogs.value.last().contains("ISO-TP First Frame: totalLen=40 bytes"))

        // Consecutive frame
        logger.logRx("21 00 20 00 20 00 20 00")
        assertTrue(logger.recentLogs.value.last().contains("ISO-TP Consecutive Frame: seq=1"))
    }

    @Test
    fun testReverseEngineeringSummaryAggregation() {
        logger.logTx("AT SH 7C0")
        logger.logTx("22 A0 01")
        logger.logRx("62 A0 01 00")

        logger.logTx("2E A0 01 01")
        logger.logRx("6E A0 01")

        logger.logTx("22 A0 99")
        logger.logRx("7F 22 31")

        val summary = logger.getReverseEngineeringSummary()
        assertTrue(summary.contains("=== REVERSE ENGINEERING SUMMARY: DISCOVERED DIDs & COMMANDS ==="))
        assertTrue(summary.contains("[ECU 7C0 - Combination Meter ECU (Cluster)]"))
        assertTrue(summary.contains("DID 0xA001 (Combination Meter Reverse Buzzer): Payload=00"))
        assertTrue(summary.contains("Singolo Bip"))
        assertTrue(summary.contains("WRITTEN DIDs (0x2E -> 0x6E ACK):"))
        assertTrue(summary.contains("Written Payload=01"))
        assertTrue(summary.contains("Command: 2E A0 01 01"))
        assertTrue(summary.contains("REJECTED SERVICES (0x7F NRC):"))
        assertTrue(summary.contains("NRC 0x31: requestOutOfRange"))
    }

    @Test
    fun testCanDumpExportFormat() {
        logger.startRecording("TestCanDump")
        logger.logTx("AT SH 7E0")
        logger.logTx("010D")
        logger.logRx("41 0D 28")

        val cdFile = logger.exportCanDumpFile()
        assertNotNull(cdFile)
        assertTrue(cdFile!!.exists())

        val text = cdFile.readText()
        // Format: (timestamp) can0 ID#PAYLOAD
        assertTrue(text.contains("can0 7E0#"))
        assertTrue(text.contains("can0 7E8#"))
        assertTrue(text.contains("410D28"))
    }

    @Test
    fun testUtf8EncodingIntegrity() {
        logger.startRecording("TestDongle_🔗_OBD")
        val unicodeMessage = "🔗 Connessione Bluetooth attiva: Vgate iCar Pro ⚠️ Attenzione ❌ Errore ⚡ READY"
        logger.logSystem(unicodeMessage)

        val file = logger.getLogFile()
        assertNotNull(file)
        logger.pauseRecording()

        val content = file!!.readText(Charsets.UTF_8)
        assertTrue("Il log deve contenere i caratteri Unicode intatti senza corruzione: $content", content.contains(unicodeMessage))
        assertFalse("Il log non deve contenere caratteri di sostituzione '?' per simboli Unicode", content.contains("???"))
    }
}
