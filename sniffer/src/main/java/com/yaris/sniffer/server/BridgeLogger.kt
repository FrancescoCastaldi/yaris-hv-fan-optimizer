package com.yaris.sniffer.server

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BridgeLogger(
    private val logDirectory: File,
    private val context: Context? = null
) {
    constructor(context: Context) : this(File(context.cacheDir, "bridge_logs"), context)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _txCount = MutableStateFlow(0L)
    val txCount: StateFlow<Long> = _txCount.asStateFlow()

    private val _rxCount = MutableStateFlow(0L)
    val rxCount: StateFlow<Long> = _rxCount.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private var currentLogFile: File? = null
    private var fileWriter: PrintWriter? = null
    private val logLock = Any()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    companion object {
        private const val MAX_UI_LOGS = 120

        fun formatTxEntry(timestampMs: Long, command: String, dateFormat: SimpleDateFormat): String {
            val dateStr = dateFormat.format(Date(timestampMs))
            return "[$timestampMs] [$dateStr] TX >>> $command"
        }

        fun formatRxEntry(timestampMs: Long, response: String, dateFormat: SimpleDateFormat): String {
            val dateStr = dateFormat.format(Date(timestampMs))
            return "[$timestampMs] [$dateStr] RX <<< ${response.trim()}"
        }
    }

    fun startRecording(connectedDevice: String? = null) {
        synchronized(logLock) {
            if (_isRecording.value) return

            // Resume existing paused session file if present
            if (currentLogFile != null && fileWriter != null) {
                _isRecording.value = true
                val nowMs = System.currentTimeMillis()
                val resumeEntry = "[$nowMs] [${dateFormat.format(Date(nowMs))}] [SYS] REGISTRAZIONE RIPRESA"
                fileWriter?.println(resumeEntry)
                fileWriter?.flush()
                appendUiLog("▶ REGISTRAZIONE RIPRESA: ${currentLogFile?.name}")
                return
            }

            fileWriter?.close()
            val timeStamp = fileDateFormat.format(Date())
            logDirectory.mkdirs()
            val file = File(logDirectory, "obd_bridge_${timeStamp}.txt")
            currentLogFile = file
            fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

            val header = buildString {
                appendLine("================================================================")
                appendLine("  YARIS OBD BRIDGE & SNIFFER - LOG TRACE")
                appendLine("  Timestamp: ${dateFormat.format(Date())} (${System.currentTimeMillis()} ms)")
                appendLine("  Device: ${connectedDevice ?: "Unknown / None"}")
                appendLine("  TCP Port: 35000 (127.0.0.1)")
                appendLine("================================================================")
                appendLine()
            }
            fileWriter?.print(header)
            fileWriter?.flush()

            _isRecording.value = true
            appendUiLog("● REGISTRAZIONE AVVIATA: ${file.name}")
        }
    }

    fun pauseRecording() {
        synchronized(logLock) {
            if (!_isRecording.value) return
            _isRecording.value = false
            fileWriter?.flush()
            appendUiLog("⏸ REGISTRAZIONE IN PAUSA")
        }
    }

    fun logTx(command: String) {
        val nowMs = System.currentTimeMillis()
        val entry = formatTxEntry(nowMs, command, dateFormat)

        synchronized(logLock) {
            _txCount.value += 1
            if (_isRecording.value) {
                fileWriter?.println(entry)
                fileWriter?.flush()
            }
            appendUiLog(entry)
        }
    }

    fun logRx(response: String) {
        val nowMs = System.currentTimeMillis()
        val entry = formatRxEntry(nowMs, response, dateFormat)

        synchronized(logLock) {
            _rxCount.value += 1
            if (_isRecording.value) {
                fileWriter?.println(entry)
                fileWriter?.flush()
            }
            appendUiLog(entry)
        }
    }

    fun logSystem(message: String) {
        val nowMs = System.currentTimeMillis()
        val formattedDate = dateFormat.format(Date(nowMs))
        val entry = "[$nowMs] [$formattedDate] [SYS] $message"

        synchronized(logLock) {
            if (_isRecording.value) {
                fileWriter?.println(entry)
                fileWriter?.flush()
            }
            appendUiLog(entry)
        }
    }

    private fun appendUiLog(line: String) {
        val current = _recentLogs.value.toMutableList()
        if (current.size >= MAX_UI_LOGS) {
            current.removeAt(0)
        }
        current.add(line)
        _recentLogs.value = current
    }

    fun clearUiLogs() {
        _recentLogs.value = emptyList()
    }

    fun resetCounters() {
        _txCount.value = 0L
        _rxCount.value = 0L
    }

    fun getLogFile(): File? {
        synchronized(logLock) {
            fileWriter?.flush()
            return currentLogFile
        }
    }

    fun createShareIntent(): Intent? {
        val ctx = context ?: return null
        synchronized(logLock) {
            fileWriter?.flush()

            var file = currentLogFile
            if (file == null || !file.exists()) {
                val timeStamp = fileDateFormat.format(Date())
                logDirectory.mkdirs()
                file = File(logDirectory, "obd_bridge_${timeStamp}.txt")
                currentLogFile = file
                fileWriter?.close()
                fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

                val header = buildString {
                    appendLine("================================================================")
                    appendLine("  YARIS OBD BRIDGE & SNIFFER - LOG TRACE")
                    appendLine("  Timestamp: ${dateFormat.format(Date())} (${System.currentTimeMillis()} ms)")
                    appendLine("  TCP Port: 35000 (127.0.0.1)")
                    appendLine("================================================================")
                    appendLine()
                }
                fileWriter?.print(header)
                _recentLogs.value.forEach { line ->
                    fileWriter?.println(line)
                }
                fileWriter?.flush()
            }

            if (file.length() == 0L) {
                fileWriter?.println("Log chiuso il: ${dateFormat.format(Date())}")
                fileWriter?.flush()
            }

            return try {
                val uri: Uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Log OBD Bridge: ${file.name}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Traccia OBD registrata con Yaris OBD Bridge.\nTotale TX: ${_txCount.value} frame\nTotale RX: ${_rxCount.value} frame"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                logSystem("Errore generazione Share Intent: ${e.message}")
                null
            }
        }
    }

    fun resetSession() {
        synchronized(logLock) {
            _isRecording.value = false
            fileWriter?.flush()
            fileWriter?.close()
            fileWriter = null
            currentLogFile = null
            resetCounters()
            clearUiLogs()
        }
    }

    fun close() {
        synchronized(logLock) {
            _isRecording.value = false
            fileWriter?.flush()
            fileWriter?.close()
            fileWriter = null
        }
    }
}
