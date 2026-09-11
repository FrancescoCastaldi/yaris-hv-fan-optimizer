package com.yaris.hvfan.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ObdLogger {
    private const val TAG = "ObdLogger"
    private const val MAX_LOG_FILES = 10

    private var logDirectory: File? = null
    private var contextRef: Context? = null
    private var currentLogFile: File? = null
    private var fileWriter: PrintWriter? = null

    // Candump / SavvyCAN file & writer
    private var canDumpFile: File? = null
    private var canDumpWriter: PrintWriter? = null

    private val logLock = Any()

    // Stato sessione Reverse Engineering
    private var activeHeader: String = ""
    private var lastTxCommand: String = ""
    private val reverseEngineeringTracker = AutomotiveProtocolDecoder.ReverseEngineeringTracker()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun init(context: Context) {
        synchronized(logLock) {
            contextRef = context.applicationContext
            val dir = File(context.cacheDir, "ecu_logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logDirectory = dir
            rotateLogsIfNeeded()
            createNewSessionFile("APP_STARTUP")
        }
    }

    fun setActiveHeader(header: String) {
        synchronized(logLock) {
            activeHeader = header.trim().uppercase()
        }
    }

    fun getActiveHeader(): String {
        synchronized(logLock) {
            return activeHeader
        }
    }

    fun getReverseEngineeringSummary(): String {
        synchronized(logLock) {
            return reverseEngineeringTracker.generateSummary()
        }
    }

    fun log(message: String) {
        val now = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(now))
        val logLine = "[$timeStr] $message"
        Log.i(TAG, logLine)

        synchronized(logLock) {
            ensureWriterOpen()
            fileWriter?.println(logLine)
            fileWriter?.flush()
        }
    }

    fun logTx(command: String, header: String = "") {
        val now = System.currentTimeMillis()
        val targetHeader: String
        val annotation: String

        synchronized(logLock) {
            if (header.isNotBlank()) {
                activeHeader = header.trim().uppercase()
            }
            val extracted = AutomotiveProtocolDecoder.extractHeaderFromCommand(command)
            if (extracted != null) {
                activeHeader = extracted
            }

            targetHeader = activeHeader
            lastTxCommand = command

            reverseEngineeringTracker.recordTx(command, targetHeader)
            annotation = AutomotiveProtocolDecoder.decodeTx(command, targetHeader)

            // Scrittura frame CAN standard candump
            val cdTx = AutomotiveProtocolDecoder.formatCanDumpTx(now, command, targetHeader)
            if (cdTx != null) {
                ensureWriterOpen()
                canDumpWriter?.println(cdTx)
                canDumpWriter?.flush()
            }
        }

        val target = if (targetHeader.isNotBlank()) " [HEADER: $targetHeader]" else ""
        val annot = if (annotation.isNotBlank()) "  $annotation" else ""
        log("TX >>> $command$target$annot")
    }

    fun logRx(response: String, durationMs: Long? = null) {
        val now = System.currentTimeMillis()
        val currentHdr: String
        val currentLastTx: String
        val annotation: String

        synchronized(logLock) {
            currentHdr = activeHeader
            currentLastTx = lastTxCommand

            reverseEngineeringTracker.recordRx(response, currentHdr, currentLastTx)
            annotation = AutomotiveProtocolDecoder.decodeRx(response, currentHdr, currentLastTx)

            // Scrittura frame CAN standard candump
            val cdRxList = AutomotiveProtocolDecoder.formatCanDumpRx(now, response, currentHdr)
            if (cdRxList.isNotEmpty()) {
                ensureWriterOpen()
                for (cdRx in cdRxList) {
                    canDumpWriter?.println(cdRx)
                }
                canDumpWriter?.flush()
            }
        }

        val durationStr = if (durationMs != null) " (${durationMs}ms)" else ""
        val annot = if (annotation.isNotBlank()) "  $annotation" else ""
        log("RX <<< ${response.trim()}$durationStr$annot")
    }

    fun createNewSessionFile(reason: String = "NEW_SESSION") {
        synchronized(logLock) {
            val dir = logDirectory ?: return
            try {
                fileWriter?.flush()
                fileWriter?.close()
                fileWriter = null

                canDumpWriter?.flush()
                canDumpWriter?.close()
                canDumpWriter = null

                val timeStamp = fileDateFormat.format(Date())
                val file = File(dir, "yaris_ecu_log_${timeStamp}.txt")
                currentLogFile = file
                fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

                val cdFile = File(dir, "yaris_ecu_log_${timeStamp}.candump.log")
                canDumpFile = cdFile
                canDumpWriter = PrintWriter(OutputStreamWriter(FileOutputStream(cdFile, true), Charsets.UTF_8))

                val header = buildString {
                    appendLine("================================================================")
                    appendLine("  TOYOTA YARIS HYBRID XP210 - DIAGNOSTIC ECU TRACE & REVERSE ENG")
                    appendLine("  Timestamp: ${fullDateFormat.format(Date())}")
                    appendLine("  Session Reason: $reason")
                    appendLine("  Target: Denso HV Battery (7E2), Engine (7E0), Meter (7C0), Body (750)")
                    appendLine("  Protocol: UDS ISO 14229-1 & ISO 15765-2 / CAN 11-bit 500k")
                    appendLine("================================================================")
                    appendLine()
                }
                fileWriter?.print(header)
                fileWriter?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Errore creazione file di log sessione", e)
            }
        }
    }

    fun getCurrentLogFile(): File? {
        synchronized(logLock) {
            fileWriter?.flush()
            return currentLogFile
        }
    }

    fun exportCanDumpFile(): File? {
        synchronized(logLock) {
            canDumpWriter?.flush()
            return canDumpFile
        }
    }

    fun createShareIntent(): Intent? {
        val ctx = contextRef ?: return null
        synchronized(logLock) {
            ensureWriterOpen()

            val file = currentLogFile
            if (file == null || !file.exists() || file.length() == 0L) {
                log("Creazione log diagnostico istantaneo per esportazione...")
            }

            // Includi sempre il sommario di Reverse Engineering prima della condivisione
            fileWriter?.println()
            fileWriter?.println(reverseEngineeringTracker.generateSummary())
            fileWriter?.flush()

            val validFile = currentLogFile ?: return null

            return try {
                val uri: Uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    validFile
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Log Diagnostica Yaris ECU: ${validFile.name}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Log diagnostico e Reverse Engineering ECU Toyota Yaris XP210.\nFile: ${validFile.name}\nDimensione: ${validFile.length()} bytes"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore generazione Share Intent log ECU", e)
                null
            }
        }
    }

    fun createCanDumpShareIntent(): Intent? {
        val ctx = contextRef ?: return null
        synchronized(logLock) {
            ensureWriterOpen()
            canDumpWriter?.flush()

            val file = canDumpFile ?: return null
            if (!file.exists()) return null

            return try {
                val uri: Uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Traccia CAN candump: ${file.name}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Traccia CAN bus Toyota Yaris XP210 in formato standard candump / SavvyCAN.\nFile: ${file.name}\nDimensione: ${file.length()} bytes"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore generazione Share Intent CAN dump", e)
                null
            }
        }
    }

    fun clearLogs() {
        synchronized(logLock) {
            try {
                fileWriter?.flush()
                fileWriter?.close()
                fileWriter = null
                currentLogFile = null

                canDumpWriter?.flush()
                canDumpWriter?.close()
                canDumpWriter = null
                canDumpFile = null

                activeHeader = ""
                lastTxCommand = ""
                reverseEngineeringTracker.clear()

                logDirectory?.listFiles()?.forEach { it.delete() }
                createNewSessionFile("CLEARED")
            } catch (e: Exception) {
                Log.e(TAG, "Errore pulizia log", e)
            }
        }
    }

    private fun ensureWriterOpen() {
        if (fileWriter == null || canDumpWriter == null) {
            val dir = logDirectory
            if (dir != null) {
                val timeStamp = fileDateFormat.format(Date())
                if (fileWriter == null) {
                    val file = File(dir, "yaris_ecu_log_${timeStamp}.txt")
                    currentLogFile = file
                    fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
                }
                if (canDumpWriter == null) {
                    val cdFile = File(dir, "yaris_ecu_log_${timeStamp}.candump.log")
                    canDumpFile = cdFile
                    canDumpWriter = PrintWriter(OutputStreamWriter(FileOutputStream(cdFile, true), Charsets.UTF_8))
                }
            }
        }
    }

    private fun rotateLogsIfNeeded() {
        val dir = logDirectory ?: return
        val files = dir.listFiles { f -> f.extension == "txt" || f.extension == "log" }?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_LOG_FILES * 2) {
            val toDelete = files.take(files.size - (MAX_LOG_FILES * 2))
            toDelete.forEach { it.delete() }
        }
    }
}
