package com.yaris.sniffer.server

import android.util.Log
import com.yaris.sniffer.bluetooth.BridgeBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

sealed class BridgeServerState {
    object Stopped : BridgeServerState()
    data class Listening(val port: Int) : BridgeServerState()
    data class ClientConnected(val clientAddress: String, val port: Int) : BridgeServerState()
    data class Error(val message: String) : BridgeServerState()
}

class BridgeServer(
    private val bluetoothManager: BridgeBluetoothManager,
    private val logger: BridgeLogger
) {
    companion object {
        private const val TAG = "BridgeServer"
        const val DEFAULT_PORT = 35000

        fun formatElmResponse(response: String): String {
            val trimmed = response.trimEnd('>', '\r', '\n')
            return "$trimmed\r\r>"
        }
    }

    private val _serverState = MutableStateFlow<BridgeServerState>(BridgeServerState.Stopped)
    val serverState: StateFlow<BridgeServerState> = _serverState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null

    val isRunning: Boolean
        get() = _serverState.value is BridgeServerState.Listening || _serverState.value is BridgeServerState.ClientConnected

    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) return

        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = ss

                _serverState.value = BridgeServerState.Listening(port)
                logger.logSystem("Server Bridge TCP avviato su 127.0.0.1:$port (in ascolto per Dr. Prius / Car Scanner)")
                if (!logger.isRecording.value) {
                    logger.startRecording(bluetoothManager.connectedDeviceName)
                }

                while (isActive && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        client.tcpNoDelay = true
                        activeClientSocket = client
                        val remoteAddr = client.remoteSocketAddress.toString()
                        _serverState.value = BridgeServerState.ClientConnected(remoteAddr, port)
                        logger.logSystem("Nuova connessione TCP accettata da: $remoteAddr")

                        handleClient(client)

                        // Client disconnected
                        if (isActive && !ss.isClosed) {
                            _serverState.value = BridgeServerState.Listening(port)
                            logger.logSystem("Client disconnesso ($remoteAddr). In attesa di nuovo client...")
                        }
                    } catch (se: SocketException) {
                        if (!isActive || ss.isClosed) break
                        Log.w(TAG, "Socket exception during accept: ${se.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore client handling", e)
                    } finally {
                        try {
                            activeClientSocket?.close()
                        } catch (_: Exception) {}
                        activeClientSocket = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore avvio server TCP", e)
                _serverState.value = BridgeServerState.Error("Errore avvio server: ${e.message}")
                logger.logSystem("ERRORE SERVER: ${e.message}")
            } finally {
                stopInternal()
            }
        }
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
        val outputStream: OutputStream = client.getOutputStream()

        val cmdBuffer = StringBuilder()

        try {
            while (isActive && !client.isClosed && client.isConnected) {
                val charCode = reader.read()
                if (charCode == -1) break // End of stream

                val c = charCode.toChar()
                if (c == '\n') {
                    // ELM327 standard explicitly ignores line feeds (0x0A)
                    continue
                }

                if (c == '\r') {
                    val rawCmd = cmdBuffer.toString().trim()
                    cmdBuffer.setLength(0)

                    if (rawCmd.isNotEmpty()) {
                        processCommand(rawCmd, outputStream)
                    } else {
                        // Empty line / carriage return ping: respond with standard prompt
                        try {
                            outputStream.write(">\r".toByteArray(Charsets.US_ASCII))
                            outputStream.flush()
                        } catch (_: Exception) {}
                    }
                } else if (c == '\b' || charCode == 127) {
                    if (cmdBuffer.isNotEmpty()) {
                        cmdBuffer.setLength(cmdBuffer.length - 1)
                    }
                } else {
                    cmdBuffer.append(c)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Client loop terminated: ${e.message}")
        }
    }

    private suspend fun processCommand(command: String, output: OutputStream) {
        logger.logTx(command)

        val response = if (bluetoothManager.isConnected) {
            try {
                bluetoothManager.sendCommand(command)
            } catch (e: Exception) {
                logger.logSystem("Errore invio Bluetooth: ${e.message}")
                "ERROR"
            }
        } else {
            // Emulazione offline avanzata per test locale con Dr. Prius e Car Scanner
            val clean = command.replace(" ", "").uppercase()
            when {
                clean in listOf("ATZ", "ATWS", "ATD", "ATBD") -> "ELM327 v1.5"
                clean.startsWith("ATE") || clean.startsWith("ATH") || clean.startsWith("ATL") ||
                clean.startsWith("ATSP") || clean.startsWith("ATSH") || clean.startsWith("ATCRA") ||
                clean.startsWith("ATFCS") || clean.startsWith("ATAL") || clean.startsWith("ATCAF") ||
                clean.startsWith("ATST") || clean.startsWith("ATSW") || clean.startsWith("ATIB") ||
                clean.startsWith("ATPB") || clean.startsWith("ATCM") || clean.startsWith("ATCF") -> "OK"
                clean == "ATDPN" -> "6"
                clean == "ATDP" -> "ISO 15765-4 (CAN 11/500)"
                clean == "ATRV" -> "14.1V"
                clean == "0100" -> "41 00 BE 3F B8 11"
                clean == "0105" -> "41 05 5A"
                clean == "010C" -> "41 0C 0F A0"
                clean == "010D" -> "41 0D 28"
                clean.startsWith("2101") || clean.startsWith("2181") ->
                    "61 01 02 80 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 00 20 28 28 28 28 03"
                clean.startsWith("2228C1") -> "62 28 C1 03"
                else -> "NO DATA"
            }
        }

        logger.logRx(response)

        val formattedResponse = formatElmResponse(response)

        try {
            output.write(formattedResponse.toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Errore scrittura client TCP", e)
        }
    }

    fun stop() {
        stopInternal()
        _serverState.value = BridgeServerState.Stopped
        logger.pauseRecording()
        logger.logSystem("Server Bridge arrestato.")
    }

    private fun stopInternal() {
        try {
            activeClientSocket?.close()
            activeClientSocket = null
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
        } catch (e: Exception) {
            Log.w(TAG, "Errore chiusura server", e)
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }
}
