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

    // Candump / SavvyCAN file & writer
    private var currentCanDumpFile: File? = null
    private var canDumpWriter: PrintWriter? = null
    private val canDumpLines = mutableListOf<String>()

    private val logLock = Any()

    // Stato sessione Reverse Engineering
    private var currentHeader: String = ""
    private var lastTxCommand: String = ""
    private val reverseEngineeringTracker = AutomotiveProtocolDecoder.ReverseEngineeringTracker()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    companion object {
        private const val MAX_UI_LOGS = 120

        fun formatTxEntry(
            timestampMs: Long,
            command: String,
            dateFormat: SimpleDateFormat,
            annotation: String = ""
        ): String {
            val dateStr = dateFormat.format(Date(timestampMs))
            val annotStr = if (annotation.isNotBlank()) "  $annotation" else ""
            return "[$timestampMs] [$dateStr] TX >>> $command$annotStr"
        }

        fun formatRxEntry(
            timestampMs: Long,
            response: String,
            dateFormat: SimpleDateFormat,
            annotation: String = ""
        ): String {
            val dateStr = dateFormat.format(Date(timestampMs))
            val annotStr = if (annotation.isNotBlank()) "  $annotation" else ""
            return "[$timestampMs] [$dateStr] RX <<< ${response.trim()}$annotStr"
        }
    }

    fun startRecording(connectedDevice: String? = null) {
        synchronized(logLock) {
            if (_isRecording.value) return

            // Riprendi sessione esistente se attiva
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
            canDumpWriter?.close()

            val timeStamp = fileDateFormat.format(Date())
            logDirectory.mkdirs()

            // File log testuale principale
            val file = File(logDirectory, "obd_bridge_${timeStamp}.txt")
            currentLogFile = file
            fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

            // File traccia CAN candump / SavvyCAN
            val cdFile = File(logDirectory, "obd_bridge_${timeStamp}.candump.log")
            currentCanDumpFile = cdFile
            canDumpWriter = PrintWriter(OutputStreamWriter(FileOutputStream(cdFile, true), Charsets.UTF_8))

            val header = buildString {
                appendLine("================================================================")
                appendLine("  YARIS OBD BRIDGE & SNIFFER - LOG TRACE & REVERSE ENGINEERING")
                appendLine("  Timestamp: ${dateFormat.format(Date())} (${System.currentTimeMillis()} ms)")
                appendLine("  Device: ${connectedDevice ?: "Unknown / None"}")
                appendLine("  TCP Port: 35000 (127.0.0.1)")
                appendLine("  Protocol: UDS ISO 14229-1 & ISO 15765-2 / CAN 11-bit 500k")
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

            // Appendi sommario aggiornato di reverse engineering nel file prima della pausa
            fileWriter?.println()
            fileWriter?.println(reverseEngineeringTracker.generateSummary())
            fileWriter?.flush()
            canDumpWriter?.flush()

            appendUiLog("⏸ REGISTRAZIONE IN PAUSA (Sommario Reverse Engineering salvato)")
        }
    }

    fun logTx(command: String) {
        val nowMs = System.currentTimeMillis()

        synchronized(logLock) {
            // Tracciamento automatico del cambio header CAN (AT SH <hdr>)
            val newHeader = AutomotiveProtocolDecoder.extractHeaderFromCommand(command)
            if (newHeader != null) {
                currentHeader = newHeader
            }

            // Aggiorna tracker di reverse engineering
            reverseEngineeringTracker.recordTx(command, currentHeader)

            // Genera annotazione semantica
            val annotation = AutomotiveProtocolDecoder.decodeTx(command, currentHeader)
            val entry = formatTxEntry(nowMs, command, dateFormat, annotation)

            // Genera traccia CAN standard candump
            val canDumpTx = AutomotiveProtocolDecoder.formatCanDumpTx(nowMs, command, currentHeader)
            if (canDumpTx != null) {
                canDumpLines.add(canDumpTx)
                if (_isRecording.value) {
                    canDumpWriter?.println(canDumpTx)
                    canDumpWriter?.flush()
                }
            }

            lastTxCommand = command
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

        synchronized(logLock) {
            // Aggiorna tracker di reverse engineering accoppiando risposta a comando precedente
            reverseEngineeringTracker.recordRx(response, currentHeader, lastTxCommand)

            // Genera annotazione semantica
            val annotation = AutomotiveProtocolDecoder.decodeRx(response, currentHeader, lastTxCommand)
            val entry = formatRxEntry(nowMs, response, dateFormat, annotation)

            // Genera righe candump CAN bus
            val canDumpRxList = AutomotiveProtocolDecoder.formatCanDumpRx(nowMs, response, currentHeader)
            for (cdLine in canDumpRxList) {
                canDumpLines.add(cdLine)
                if (_isRecording.value) {
                    canDumpWriter?.println(cdLine)
                    canDumpWriter?.flush()
                }
            }

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

    fun exportCanDumpFile(): File? {
        synchronized(logLock) {
            canDumpWriter?.flush()
            return currentCanDumpFile
        }
    }

    fun getReverseEngineeringSummary(): String {
        synchronized(logLock) {
            return reverseEngineeringTracker.generateSummary()
        }
    }

    fun getCurrentHeader(): String {
        synchronized(logLock) {
            return currentHeader
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
                    appendLine("  YARIS OBD BRIDGE & SNIFFER - LOG TRACE & REVERSE ENGINEERING")
                    appendLine("  Timestamp: ${dateFormat.format(Date())} (${System.currentTimeMillis()} ms)")
                    appendLine("  TCP Port: 35000 (127.0.0.1)")
                    appendLine("================================================================")
                    appendLine()
                }
                fileWriter?.print(header)
                _recentLogs.value.forEach { line ->
                    fileWriter?.println(line)
                }
            }

            // Includi sempre il sommario strutturato di Reverse Engineering nel log prima della condivisione
            fileWriter?.println()
            fileWriter?.println(reverseEngineeringTracker.generateSummary())
            fileWriter?.flush()

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
                        "Traccia OBD e Reverse Engineering registrata con Yaris OBD Bridge.\nTotale TX: ${_txCount.value} frame\nTotale RX: ${_rxCount.value} frame\nInclude sommario DIDs e comandi scoperti."
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                logSystem("Errore generazione Share Intent: ${e.message}")
                null
            }
        }
    }

    fun createCanDumpShareIntent(): Intent? {
        val ctx = context ?: return null
        synchronized(logLock) {
            canDumpWriter?.flush()

            var file = currentCanDumpFile
            if (file == null || !file.exists()) {
                val timeStamp = fileDateFormat.format(Date())
                logDirectory.mkdirs()
                file = File(logDirectory, "obd_bridge_${timeStamp}.candump.log")
                currentCanDumpFile = file
                canDumpWriter?.close()
                canDumpWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

                canDumpLines.forEach { line ->
                    canDumpWriter?.println(line)
                }
                canDumpWriter?.flush()
            }

            val validFile = currentCanDumpFile ?: return null
            return try {
                val uri: Uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    validFile
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "CAN Dump Trace: ${validFile.name}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Traccia CAN bus compatibile con candump / SavvyCAN registrata con Yaris OBD Bridge.\nTotale frame CAN: ${canDumpLines.size}"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                logSystem("Errore generazione Share Intent CAN dump: ${e.message}")
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

            canDumpWriter?.flush()
            canDumpWriter?.close()
            canDumpWriter = null
            currentCanDumpFile = null
            canDumpLines.clear()

            currentHeader = ""
            lastTxCommand = ""
            reverseEngineeringTracker.clear()

            resetCounters()
            clearUiLogs()
        }
    }

    fun close() {
        synchronized(logLock) {
            _isRecording.value = false

            if (fileWriter != null) {
                fileWriter?.println()
                fileWriter?.println(reverseEngineeringTracker.generateSummary())
                fileWriter?.flush()
                fileWriter?.close()
                fileWriter = null
            }

            canDumpWriter?.flush()
            canDumpWriter?.close()
            canDumpWriter = null
        }
    }
}
