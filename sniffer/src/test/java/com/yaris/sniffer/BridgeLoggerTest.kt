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
