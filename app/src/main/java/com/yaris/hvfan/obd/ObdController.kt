package com.yaris.hvfan.obd

import android.util.Log
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.ble.BleManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ObdLiveState(
    val isInitialized: Boolean = false,
    val isLoopRunning: Boolean = false,
    val batteryStatus: HvBatteryStatus = HvBatteryStatus(),
    val warmupStatus: HybridWarmupStatus = HybridWarmupStatus(),
    val targetThreshold: Int = 20,
    val fanForcedMax: Boolean = true,
    val lastLogMessage: String = "In attesa di connessione...",
    val logs: List<String> = emptyList(),
    val errorCount: Int = 0
)

class ObdController(
    private val bleManager: BleManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ObdController"
        private const val POLL_INTERVAL_MS = 1500L
    }

    private val _liveState = MutableStateFlow(ObdLiveState())
    val liveState: StateFlow<ObdLiveState> = _liveState

    private var loopJob: Job? = null
    private var isProtocolInitialized = false
    private var cycleCounter = 0
    private var lastKnownCoolant = 20f
    private var lastKnownAmbient = 20f
    private var lastKnownRpm = 0

    fun startController() {
        scope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    is BleConnectionState.Ready -> {
                        addLog("Dispositivo BLE pronto. Avvio inizializzazione ECU Toyota Yaris...")
                        initializeAndStartLoop()
                    }
                    is BleConnectionState.Disconnected, is BleConnectionState.Error -> {
                        stopLoop()
                        isProtocolInitialized = false
                        _liveState.value = _liveState.value.copy(
                            isInitialized = false,
                            isLoopRunning = false,
                            lastLogMessage = if (state is BleConnectionState.Error) state.message else "Disconnesso"
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$timestamp] $message"
        Log.i(TAG, logLine)
        val currentLogs = _liveState.value.logs.takeLast(50).toMutableList()
        currentLogs.add(logLine)
        _liveState.value = _liveState.value.copy(
            lastLogMessage = message,
            logs = currentLogs
        )
    }

    private fun initializeAndStartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Send ELM327 Standard Inits
                for (cmd in Elm327Protocol.INIT_COMMANDS) {
                    addLog("CMD: $cmd")
                    val res = bleManager.sendCommand(cmd)
                    addLog("RES: ${Elm327Protocol.cleanResponse(res)}")
                    delay(80)
                }

                // 2. Set Toyota Yaris Denso HV Battery CAN Header
                addLog("CMD: ${ToyotaYarisCommands.CMD_SET_HEADER_BATTERY_ECU}")
                val resHeader = bleManager.sendCommand(ToyotaYarisCommands.CMD_SET_HEADER_BATTERY_ECU)
                addLog("RES: ${Elm327Protocol.cleanResponse(resHeader)}")
                delay(80)

                addLog("CMD: ${ToyotaYarisCommands.CMD_SET_RECEIVE_FILTER}")
                val resFilter = bleManager.sendCommand(ToyotaYarisCommands.CMD_SET_RECEIVE_FILTER)
                addLog("RES: ${Elm327Protocol.cleanResponse(resFilter)}")
                delay(80)

                isProtocolInitialized = true
                _liveState.value = _liveState.value.copy(
                    isInitialized = true,
                    isLoopRunning = true
                )
                addLog("Inizializzazione completata! Avvio loop monitoraggio e controllo ventola...")

                // 3. Main Loop
                while (isActive) {
                    executeFanControlCycle()
                    delay(POLL_INTERVAL_MS)
                }

            } catch (e: CancellationException) {
                addLog("Loop terminato.")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante ciclo OBD", e)
                addLog("Errore: ${e.localizedMessage}")
                _liveState.value = _liveState.value.copy(
                    errorCount = _liveState.value.errorCount + 1
                )
            }
        }
    }

    private suspend fun executeFanControlCycle() {
        val currentState = _liveState.value
        cycleCounter++

        // Step A: Set Battery ECU Header & Read Battery Temperatures (Mode 22 / Mode 21)
        var rawResponse = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA)
        if (Elm327Protocol.isError(rawResponse)) {
            rawResponse = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_LEGACY)
        }

        val parsedStatus = ToyotaYarisCommands.parseBatteryResponse(rawResponse, currentState.fanForcedMax)
        val updatedBattery = if (parsedStatus != null) {
            parsedStatus
        } else {
            currentState.batteryStatus.copy(timestamp = System.currentTimeMillis())
        }

        // Step B: Thermal threshold evaluation & Fan Control
        val shouldForceFan = currentState.fanForcedMax || (updatedBattery.maxTemp >= currentState.targetThreshold)
        if (shouldForceFan) {
            val fanCmdRes = bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_MAX_SPEED_UDS)
            val cleanFanRes = Elm327Protocol.cleanResponse(fanCmdRes)
            if (cleanFanRes.contains("7F30") || cleanFanRes.contains("ERROR")) {
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_MAX_SPEED_ALT)
            }
            addLog("Ventola HV MAX (L6) | Batt: ${String.format("%.1f", updatedBattery.maxTemp)}°C")
        } else {
            bleManager.sendCommand(ToyotaYarisCommands.CMD_TESTER_PRESENT)
            addLog("Batt: ${String.format("%.1f", updatedBattery.maxTemp)}°C (sotto soglia ${currentState.targetThreshold}°C)")
        }

        // Step C: Interleave Engine & Ambient sensors every 2 cycles for Warm-Up Stage analysis
        var updatedWarmup = currentState.warmupStatus
        if (cycleCounter % 2 == 0) {
            // Read Coolant Temp
            val rawCoolant = bleManager.sendCommand(ToyotaYarisCommands.PID_COOLANT_TEMP)
            val parsedCoolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)
            if (parsedCoolant != null) {
                lastKnownCoolant = parsedCoolant
            }

            // Read Intake/Ambient Temp
            val rawAmbient = bleManager.sendCommand(ToyotaYarisCommands.PID_INTAKE_AIR_TEMP)
            val parsedAmbient = ToyotaYarisCommands.parseIntakeAirTemp(rawAmbient)
            if (parsedAmbient != null) {
                lastKnownAmbient = parsedAmbient
            }

            // Read RPM
            val rawRpm = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM)
            val parsedRpm = ToyotaYarisCommands.parseEngineRpm(rawRpm)
            if (parsedRpm != null) {
                lastKnownRpm = parsedRpm
            }

            updatedWarmup = ToyotaYarisCommands.evaluateWarmupStatus(
                coolantTemp = lastKnownCoolant,
                ambientTemp = lastKnownAmbient,
                rpm = lastKnownRpm
            )
        }

        _liveState.value = _liveState.value.copy(
            batteryStatus = updatedBattery.copy(isFanForced = shouldForceFan, fanSpeedLevel = if (shouldForceFan) 6 else updatedBattery.fanSpeedLevel),
            warmupStatus = updatedWarmup
        )
    }

    fun setTargetThreshold(temp: Int) {
        _liveState.value = _liveState.value.copy(targetThreshold = temp)
        addLog("Soglia temperatura impostata a ${temp}°C")
    }

    fun setForcedFan(forced: Boolean) {
        _liveState.value = _liveState.value.copy(fanForcedMax = forced)
        addLog(if (forced) "Forzatura ventola 100% ABILITATA" else "Forzatura ventola DISABILITATA (solo soglia)")
    }

    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        _liveState.value = _liveState.value.copy(isLoopRunning = false)
    }
}
