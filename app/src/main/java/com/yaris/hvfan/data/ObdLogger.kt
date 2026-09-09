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
    private val logLock = Any()

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
        val target = if (header.isNotBlank()) " [HEADER: $header]" else ""
        log("TX >>> $command$target")
    }

    fun logRx(response: String, durationMs: Long? = null) {
        val durationStr = if (durationMs != null) " (${durationMs}ms)" else ""
        log("RX <<< ${response.trim()}$durationStr")
    }

    fun createNewSessionFile(reason: String = "NEW_SESSION") {
        synchronized(logLock) {
            val dir = logDirectory ?: return
            try {
                fileWriter?.flush()
                fileWriter?.close()
                fileWriter = null

                val timeStamp = fileDateFormat.format(Date())
                val file = File(dir, "yaris_ecu_log_${timeStamp}.txt")
                currentLogFile = file
                fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

                val header = buildString {
                    appendLine("================================================================")
                    appendLine("  TOYOTA YARIS HYBRID XP210 - DIAGNOSTIC ECU TRACE LOG")
                    appendLine("  Timestamp: ${fullDateFormat.format(Date())}")
                    appendLine("  Session Reason: $reason")
                    appendLine("  Target: Denso HV Battery (7E2) & Engine ECU (7E0)")
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

    fun createShareIntent(): Intent? {
        val ctx = contextRef ?: return null
        synchronized(logLock) {
            fileWriter?.flush()
            val file = currentLogFile
            if (file == null || !file.exists() || file.length() == 0L) {
                log("Creazione log diagnostico istantaneo per esportazione...")
                fileWriter?.flush()
            }

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
                        "Log diagnostico ECU Toyota Yaris generato con Yaris HV Fan Optimizer.\nFile: ${validFile.name}\nDimensione: ${validFile.length()} bytes"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore generazione Share Intent log ECU", e)
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
                logDirectory?.listFiles()?.forEach { it.delete() }
                createNewSessionFile("CLEARED")
            } catch (e: Exception) {
                Log.e(TAG, "Errore pulizia log", e)
            }
        }
    }

    private fun ensureWriterOpen() {
        if (fileWriter == null) {
            val dir = logDirectory
            if (dir != null) {
                val timeStamp = fileDateFormat.format(Date())
                val file = File(dir, "yaris_ecu_log_${timeStamp}.txt")
                currentLogFile = file
                fileWriter = PrintWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
            }
        }
    }

    private fun rotateLogsIfNeeded() {
        val dir = logDirectory ?: return
        val files = dir.listFiles { f -> f.extension == "txt" }?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_LOG_FILES) {
            val toDelete = files.take(files.size - MAX_LOG_FILES)
            toDelete.forEach { it.delete() }
        }
    }
}

