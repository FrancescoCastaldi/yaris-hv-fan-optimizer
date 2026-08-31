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
    val performanceStatus: EnginePerformanceStatus = EnginePerformanceStatus(),
    val accelerationState: AccelerationRunState = AccelerationRunState(),
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
    private var lastKnownCoolant = 0f
    private var lastKnownAmbient = 0f
    private var lastKnownRpm = 0
    private var lastKnownAdvance = 0f
    private var lastKnownLoad = 0f
    private var lastKnownThrottle = 0f
    private var lastKnownSpeed = 0

    // Acceleration Timer State Machine
    private var launchStartTimeMs = 0L
    private var isTimingInProgress = false
    private var isLaunchArmed = false
    private var run0to50Sec: Float? = null
    private var run0to100Sec: Float? = null
    private var best0to50Sec: Float? = null
    private var best0to100Sec: Float? = null

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

    private var currentCanHeader: String = ""

    private suspend fun ensureCanHeader(header: String, filter: String) {
        if (currentCanHeader != header) {
            bleManager.sendCommand("AT SH $header")
            bleManager.sendCommand("AT CRA $filter")
            currentCanHeader = header
            delay(40)
        }
    }

    private suspend fun executeFanControlCycle() {
        val currentState = _liveState.value
        cycleCounter++

        // Step A: Switch to Battery ECU (7E2 / 7EA) & Read Battery Temperatures (Mode 22 / Mode 21)
        ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)

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

        // Step B: Thermal threshold evaluation & Active Test Fan Control
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

        // Step C: Interleave Engine ECU (7E0 / 7E8) for REAL Coolant, Ambient, RPM, Speed & Performance
        var updatedWarmup = currentState.warmupStatus
        var updatedPerformance = currentState.performanceStatus
        var updatedAcceleration = currentState.accelerationState

        if (cycleCounter % 2 == 0) {
            ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU, ToyotaYarisCommands.FILTER_ENGINE_ECU)

            // Read Real Physical Vehicle Speed (km/h)
            val rawSpeed = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED)
            val parsedSpeed = ToyotaYarisCommands.parseVehicleSpeed(rawSpeed)
            if (parsedSpeed != null) {
                lastKnownSpeed = parsedSpeed
            }

            // Read Real Physical Coolant Temp (ECT)
            val rawCoolant = bleManager.sendCommand(ToyotaYarisCommands.PID_COOLANT_TEMP)
            val parsedCoolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)
            if (parsedCoolant != null) {
                lastKnownCoolant = parsedCoolant
            }

            // Read Real Physical Intake/Ambient Air Temp (IAT)
            val rawAmbient = bleManager.sendCommand(ToyotaYarisCommands.PID_INTAKE_AIR_TEMP)
            val parsedAmbient = ToyotaYarisCommands.parseIntakeAirTemp(rawAmbient)
            if (parsedAmbient != null) {
                lastKnownAmbient = parsedAmbient
            }

            // Read Real Physical Engine RPM
            val rawRpm = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM)
            val parsedRpm = ToyotaYarisCommands.parseEngineRpm(rawRpm)
            if (parsedRpm != null) {
                lastKnownRpm = parsedRpm
            }

            // Read Real Timing Advance (°BTDC)
            val rawAdvance = bleManager.sendCommand(ToyotaYarisCommands.PID_TIMING_ADVANCE)
            val parsedAdvance = ToyotaYarisCommands.parseTimingAdvance(rawAdvance)
            if (parsedAdvance != null) {
                lastKnownAdvance = parsedAdvance
            }

            // Read Engine Load (%)
            val rawLoad = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_LOAD)
            val parsedLoad = ToyotaYarisCommands.parseEngineLoad(rawLoad)
            if (parsedLoad != null) {
                lastKnownLoad = parsedLoad
            }

            // Read Throttle / Accelerator Position (%)
            val rawThrottle = bleManager.sendCommand(ToyotaYarisCommands.PID_THROTTLE_POS)
            val parsedThrottle = ToyotaYarisCommands.parseThrottlePos(rawThrottle)
            if (parsedThrottle != null) {
                lastKnownThrottle = parsedThrottle
            }

            if (parsedCoolant != null || lastKnownCoolant > 0f) {
                updatedWarmup = ToyotaYarisCommands.evaluateWarmupStatus(
                    coolantTemp = lastKnownCoolant,
                    ambientTemp = lastKnownAmbient,
                    rpm = lastKnownRpm
                )
            }

            val hasPerfData = parsedAdvance != null || parsedLoad != null || lastKnownLoad > 0f
            updatedPerformance = EnginePerformanceStatus(
                timingAdvance = lastKnownAdvance,
                engineLoadPercent = lastKnownLoad,
                throttlePercent = lastKnownThrottle,
                isOptimalAdvance = lastKnownAdvance >= 15.0f,
                isHighPowerReady = !updatedBattery.isThermalThrottled && updatedWarmup.stage == WarmupStage.S4,
                hasLiveData = hasPerfData
            )

            // --- Acceleration Sprint Timer Logic ---
            var elapsedRunMs = 0L
            if (lastKnownSpeed == 0) {
                isLaunchArmed = true
                if (isTimingInProgress) {
                    isTimingInProgress = false
                }
            } else if (isLaunchArmed && lastKnownSpeed > 0 && lastKnownThrottle > 15f) {
                // Launch started!
                isLaunchArmed = false
                isTimingInProgress = true
                launchStartTimeMs = System.currentTimeMillis()
                run0to50Sec = null
                run0to100Sec = null
                addLog("🏁 SCATTO AVVIATO! Rilevamento 0-50 / 0-100 km/h in corso...")
            }

            if (isTimingInProgress && launchStartTimeMs > 0L) {
                elapsedRunMs = System.currentTimeMillis() - launchStartTimeMs

                if (lastKnownSpeed >= 50 && run0to50Sec == null) {
                    run0to50Sec = elapsedRunMs / 1000.0f
                    if (best0to50Sec == null || run0to50Sec!! < best0to50Sec!!) {
                        best0to50Sec = run0to50Sec
                    }
                    addLog("⚡ TRAGUARDO 0-50 km/h: ${String.format("%.2f", run0to50Sec)}s (Record: ${String.format("%.2f", best0to50Sec)}s)")
                }

                if (lastKnownSpeed >= 100 && run0to100Sec == null) {
                    run0to100Sec = elapsedRunMs / 1000.0f
                    if (best0to100Sec == null || run0to100Sec!! < best0to100Sec!!) {
                        best0to100Sec = run0to100Sec
                    }
                    isTimingInProgress = false
                    addLog("🏆 TRAGUARDO 0-100 km/h: ${String.format("%.2f", run0to100Sec)}s (Record: ${String.format("%.2f", best0to100Sec)}s)")
                }
            }

            updatedAcceleration = AccelerationRunState(
                currentSpeedKmh = lastKnownSpeed,
                isLaunchReady = isLaunchArmed && lastKnownSpeed == 0,
                isTimingActive = isTimingInProgress,
                elapsedMs = elapsedRunMs,
                last0to50TimeSec = run0to50Sec,
                last0to100TimeSec = run0to100Sec,
                best0to50TimeSec = best0to50Sec,
                best0to100TimeSec = best0to100Sec,
                lastRunCompleted = run0to100Sec != null || (run0to50Sec != null && !isTimingInProgress)
            )

            addLog("GR Telemetry: ${lastKnownSpeed} km/h | Advance=${String.format("%.1f", lastKnownAdvance)}° | Load=${lastKnownLoad.toInt()}%")
        }

        _liveState.value = _liveState.value.copy(
            batteryStatus = updatedBattery.copy(isFanForced = shouldForceFan, fanSpeedLevel = if (shouldForceFan) 6 else updatedBattery.fanSpeedLevel),
            warmupStatus = updatedWarmup,
            performanceStatus = updatedPerformance,
            accelerationState = updatedAcceleration
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
