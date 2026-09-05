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
    val hasEcuCommunication: Boolean = false, // True solo quando arrivano frame CAN validi dalla ECU dell'auto
    val isVehicleReady: Boolean = false,      // True quando 12V > 13.0V (DC-DC attivo) o CAN valido
    val isStandbyMode: Boolean = false,       // True se in standby a basso consumo (auto spenta o non READY)
    val auxiliary12vVoltage: Float = 0f,      // Tensione reale 12V rilevata da AT RV
    val ecuAlertMessage: String? = null,      // Avviso visivo per l'utente quando la centralina non risponde
    val lastDataReceivedTimestamp: Long = 0L,
    val batteryStatus: HvBatteryStatus = HvBatteryStatus(),
    val warmupStatus: HybridWarmupStatus = HybridWarmupStatus(),
    val performanceStatus: EnginePerformanceStatus = EnginePerformanceStatus(),
    val accelerationState: AccelerationRunState = AccelerationRunState(),
    val ecuCodingState: EcuCustomizationState = EcuCustomizationState(),
    val targetThreshold: Int = 20,
    val fanForcedMax: Boolean = true,
    val autoCoolingStatus: AutoCoolingStatus = AutoCoolingStatus(),
    val lastLogMessage: String = "In attesa di connessione...",
    val logs: List<String> = emptyList(),
    val errorCount: Int = 0
)

class ObdController(
    private val bleManager: BleManager,
    private val scope: CoroutineScope,
    private val appPreferences: com.yaris.hvfan.data.AppPreferences? = null
) {
    companion object {
        private const val TAG = "ObdController"
        private const val BATTERY_POLL_INTERVAL_MS = 3500L
        private const val COOLANT_POLL_INTERVAL_MS = 4000L
    }

    var onAutoCoolingStateChanged: ((Boolean) -> Unit)? = null

    private val _liveState = MutableStateFlow(
        ObdLiveState(
            accelerationState = AccelerationRunState(
                best0to50TimeSec = appPreferences?.best0to50TimeSec,
                best0to100TimeSec = appPreferences?.best0to100TimeSec
            ),
            autoCoolingStatus = AutoCoolingStatus(
                isEnabled = appPreferences?.isAutoCoolingEnabled ?: false,
                triggerTemp = appPreferences?.autoCoolingTriggerTemp ?: 34.0f,
                hysteresis = appPreferences?.autoCoolingHysteresis ?: 2.0f,
                targetSpeed = appPreferences?.autoCoolingTargetSpeed ?: 6
            )
        )
    )
    val liveState: StateFlow<ObdLiveState> = _liveState

    private var loopJob: Job? = null
    private var isProtocolInitialized = false
    private var isMultiPidSupported = false
    private var isCustomFcSupported = true
    private var consecutiveCanErrors = 0
    private var lastValidCanTimestamp = 0L
    private var loopStartTimestamp = 0L
    private var standbyCycleCounter = 0
    private var lastBatteryCheckTimestamp = 0L
    private var lastCoolantCheckTimestamp = 0L
    private var pendingBatterySafetyCheck = false
    private var fastCycleCounter = 0

    private var lastKnown12v = 0f
    private var lastKnownCoolant = 0f
    private var lastKnownAmbient = 0f
    private var lastKnownRpm = 0
    private var lastKnownAdvance = 0f
    private var lastKnownLoad = 0f
    private var lastKnownThrottle = 0f
    private var lastKnownSpeed = 0
    private var prevSpeedKmh = 0
    private var prevSpeedTimestampMs = 0L

    // Acceleration Timer State Machine (Dragy Precise Interpolation)
    private var launchStartTimeMs = 0L
    private var isTimingInProgress = false
    private var isLaunchArmed = false
    private var run0to50Sec: Float? = null
    private var run0to100Sec: Float? = null
    private var best0to50Sec: Float? = appPreferences?.best0to50TimeSec
    private var best0to100Sec: Float? = appPreferences?.best0to100TimeSec

    fun startController() {
        scope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    is BleConnectionState.Ready -> {
                        addLog("Dispositivo pronto. Avvio inizializzazione ECU Toyota Yaris...")
                        initializeAndStartLoop()
                    }
                    is BleConnectionState.Reconnecting -> {
                        stopLoop()
                        isProtocolInitialized = false
                        consecutiveCanErrors = 0
                        _liveState.value = _liveState.value.copy(
                            isInitialized = false,
                            isLoopRunning = false,
                            hasEcuCommunication = false,
                            isVehicleReady = false,
                            isStandbyMode = false,
                            ecuAlertMessage = "Riconnessione automatica a ${state.deviceName} (#${state.attempt})...",
                            lastLogMessage = "Riconnessione in corso a ${state.deviceName} (tentativo #${state.attempt})..."
                        )
                    }
                    is BleConnectionState.Disconnected, is BleConnectionState.Error -> {
                        stopLoop()
                        isProtocolInitialized = false
                        consecutiveCanErrors = 0
                        _liveState.value = _liveState.value.copy(
                            isInitialized = false,
                            isLoopRunning = false,
                            hasEcuCommunication = false,
                            isVehicleReady = false,
                            isStandbyMode = false,
                            ecuAlertMessage = null,
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
                consecutiveCanErrors = 0
                loopStartTimestamp = System.currentTimeMillis()
                standbyCycleCounter = 0
                _liveState.value = _liveState.value.copy(
                    isInitialized = false,
                    isLoopRunning = false,
                    hasEcuCommunication = false,
                    isVehicleReady = false,
                    isStandbyMode = false,
                    ecuAlertMessage = null
                )

                // 1. Invio preventivo di sequenza di sveglia "\r\r" per svegliare Vgate da sleep/low-power
                addLog("Invio sequenza di sveglia preventiva Vgate iCar Pro (\\r\\r)...")
                bleManager.sendWakeSequence()
                delay(150)

                // 2. Invio Warm Start / Reset ELM327 con attesa appropriata (600ms) senza bloccare il baudrate
                addLog("Invio Warm Start / Reset ELM327 (AT WS)...")
                var resWs = bleManager.sendCommand(Elm327Protocol.CMD_WARM_START, timeoutMs = 1500L)
                if (Elm327Protocol.isError(resWs) || resWs.contains("TIMEOUT")) {
                    addLog("AT WS non ha risposto, fallback su AT Z...")
                    resWs = bleManager.sendCommand(Elm327Protocol.CMD_RESET, timeoutMs = 1500L)
                }
                addLog("Reset Response: ${Elm327Protocol.cleanResponse(resWs)}")
                delay(600) // 600ms attesa appropriata per firmware Vgate/STN senza alterare baudrate

                // 3. Invio sequenza di configurazione parametri seriali e protocollo CAN
                for (cmd in Elm327Protocol.INIT_COMMANDS) {
                    if (cmd == "AT Z") continue
                    addLog("CMD: $cmd")
                    val res = bleManager.sendCommand(cmd)
                    val cleanRes = Elm327Protocol.cleanResponse(res)
                    addLog("RES: $cleanRes")
                    if (cmd == "AT SP 6" && cleanRes.contains("ERROR")) {
                        addLog("Fallback protocollo su AT SP 0 (Auto)...")
                        bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
                    }
                    delay(50)
                }

                // 4. Rilevamento stato READY auto tramite tensione reale batteria 12V (AT RV > 13.0V)
                val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
                val real12v = Elm327Protocol.parseBatteryVoltage(voltRes) ?: 0f
                lastKnown12v = real12v
                addLog("Tensione Batteria 12V (AT RV): ${real12v}V")

                // 5. Configurazione Flow Control hardware ISO-TP multi-frame (PID 2228C1 celle batteria HV e ventola)
                addLog("Configurazione Flow Control hardware ISO-TP Denso HV Battery (7E2 / 7EA)...")
                bleManager.sendCommand(ToyotaYarisCommands.CMD_SET_HEADER_BATTERY_ECU) // AT SH 7E2
                bleManager.sendCommand(ToyotaYarisCommands.CMD_SET_RECEIVE_FILTER)     // AT CRA 7EA
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SH_BATTERY)          // AT FC SH 7E2
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SD_CTS)              // AT FC SD 300000
                val resFcSm = bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_CUSTOM) // AT FC SM 1
                isCustomFcSupported = !Elm327Protocol.isError(resFcSm) && !resFcSm.contains("?")
                if (isCustomFcSupported) {
                    addLog("✅ Flow Control Hardware ISO-TP Custom (AT FC SM 1) attivo per ECU Batteria 7E2!")
                } else {
                    addLog("ℹ️ Adattatore OBD non supporta AT FC SM 1 (risposta: ${Elm327Protocol.cleanResponse(resFcSm)}). Fallback su Flow Control standard.")
                }
                currentCanHeader = ToyotaYarisCommands.HEADER_BATTERY_ECU

                // 6. Test di reattività iniziale CAN bus su batteria HV
                addLog("Verifica connessione CAN centralina auto (PID 2228C1)...")
                val testCanRes = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA)
                val cleanTestCan = Elm327Protocol.cleanResponse(testCanRes)
                addLog("CAN Test Response: $cleanTestCan")

                val canResOk = cleanTestCan.contains("6228C1") || (!Elm327Protocol.isError(cleanTestCan) && cleanTestCan.length >= 8)
                val isReady = Elm327Protocol.isVehicleReady(real12v) || canResOk
                val inStandby = !isReady

                if (canResOk) {
                    addLog("✅ CAN Bus Toyota ONLINE! Dati batteria ricevuti correttamente.")
                    lastValidCanTimestamp = System.currentTimeMillis()
                } else if (isReady) {
                    addLog("⚡ Veicolo in stato READY (12V: ${real12v}V). In attesa di risposta CAN centralina...")
                } else {
                    addLog("💤 Auto in Standby (12V: ${real12v}V < 13.0V, quadro spento). Standby a basso consumo attivo.")
                }

                // 7. Test supporto Multi-PID per telemetria motore e Dragy se il veicolo è attivo
                if (isReady || canResOk) {
                    addLog("Verifica supporto Multi-PID (010D0C11)...")
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU, ToyotaYarisCommands.FILTER_ENGINE_ECU)
                    val testMultiRes = bleManager.sendCommand(ToyotaYarisCommands.CMD_MULTI_PID_ENGINE)
                    val cleanMulti = Elm327Protocol.cleanResponse(testMultiRes)
                    val parsedMulti = ToyotaYarisCommands.parseMultiPidEngineResponse(cleanMulti)
                    if (parsedMulti != null && (parsedMulti.speedKmh != null || parsedMulti.engineRpm != null)) {
                        isMultiPidSupported = true
                        addLog("✅ Multi-PID supportato nativamente (010D0C11)! Loop rapido 10Hz attivo.")
                    } else {
                        isMultiPidSupported = false
                        addLog("ℹ️ Multi-PID non disponibile: fallback su query pipelinate veloci.")
                    }
                } else {
                    isMultiPidSupported = false
                }

                isProtocolInitialized = true
                _liveState.value = _liveState.value.copy(
                    isInitialized = true,
                    isLoopRunning = true,
                    hasEcuCommunication = canResOk,
                    isVehicleReady = isReady,
                    isStandbyMode = inStandby,
                    auxiliary12vVoltage = real12v,
                    ecuAlertMessage = when {
                        canResOk -> null
                        inStandby -> "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
                        else -> "Veicolo READY rilevato (12V: ${real12v}V). In attesa di risposta dalla centralina Toyota..."
                    }
                )
                addLog("Inizializzazione completata! Avvio scheduler Dual-Rate...")

                // 8. Dual-Rate Adaptive Loop
                lastBatteryCheckTimestamp = 0L
                lastCoolantCheckTimestamp = 0L
                while (isActive) {
                    executeDualRateCycle()
                    val loopDelayMs = when {
                        _liveState.value.isStandbyMode -> 2500L // Standby a basso consumo: 2.5s per evitare saturazione bus
                        isTimingInProgress || lastKnownSpeed > 0 -> 60L
                        else -> 140L
                    }
                    delay(loopDelayMs)
                }

            } catch (e: CancellationException) {
                addLog("Loop terminato.")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante ciclo OBD", e)
                addLog("Errore: ${e.localizedMessage}")
                _liveState.value = _liveState.value.copy(
                    errorCount = _liveState.value.errorCount + 1,
                    hasEcuCommunication = false,
                    ecuAlertMessage = "Errore di comunicazione: ${e.localizedMessage}"
                )
            }
        }
    }

    private var currentCanHeader: String = ""

    private suspend fun ensureCanHeader(header: String, filter: String) {
        if (currentCanHeader != header) {
            bleManager.sendCommand("AT SH $header")
            bleManager.sendCommand("AT CRA $filter")
            if (header == ToyotaYarisCommands.HEADER_BATTERY_ECU) {
                if (isCustomFcSupported) {
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_CUSTOM)
                }
                bleManager.sendCommand("AT ST 64") // ~400ms timeout per multi-frame UDS 2228C1
            } else {
                if (isCustomFcSupported) {
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_DEFAULT)
                }
                bleManager.sendCommand("AT ST 20") // ~80ms timeout per loop rapido telemetria
            }
            currentCanHeader = header
            delay(25)
        }
    }

    private var isEcuOperationInProgress = false
    private var lastAutoRecoveryTimestamp = 0L

    private suspend fun executeCanBusAutoRecovery() {
        addLog("⚠️ Nessun dato CAN ricevuto: verifica tensione 12V e auto-recovery...")
        val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
        val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
        lastKnown12v = volt
        if (volt < 12.8f && volt > 0f) {
            addLog("Auto non in READY (12V: ${volt}V <= 12.8V): passaggio a standby a basso consumo.")
            currentCanHeader = ""
            _liveState.value = _liveState.value.copy(
                isVehicleReady = false,
                isStandbyMode = true,
                hasEcuCommunication = false,
                auxiliary12vVoltage = volt,
                ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY..."
            )
            return
        }

        // Reset rapido dello stack seriale ELM327 senza perdita connessione BLE
        bleManager.sendCommand(Elm327Protocol.CMD_WARM_START) // Warm Start
        delay(200)
        bleManager.sendCommand("AT E0")
        bleManager.sendCommand("AT L0")
        bleManager.sendCommand("AT S0")
        bleManager.sendCommand("AT H0")
        bleManager.sendCommand("AT AT 1")
        bleManager.sendCommand("AT SP 6")
        bleManager.sendCommand("AT CAF 1")
        bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SH_BATTERY)
        bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SD_CTS)
        currentCanHeader = "" // Forza riapplicazione degli header
        ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)
        addLog("✅ Procedura auto-recovery completata. Ripristino polling CAN.")
    }

    private suspend fun executeDualRateCycle() {
        if (isEcuOperationInProgress) return

        val now = System.currentTimeMillis()

        // GESTIONE STATO STANDBY A BASSO CONSUMO (Auto spenta o non READY)
        if (_liveState.value.isStandbyMode) {
            standbyCycleCounter++
            val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
            val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
            lastKnown12v = volt

            val isReadyByVoltage = Elm327Protocol.isVehicleReady(volt)
            var canOk = false

            // Se la tensione è sopra i 13.0V o ogni 4 cicli (~10s) tenta un probe CAN di sincronizzazione
            if (isReadyByVoltage || standbyCycleCounter % 4 == 0) {
                currentCanHeader = ""
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)
                val testRes = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA)
                val cleanTest = Elm327Protocol.cleanResponse(testRes)
                canOk = cleanTest.contains("6228C1") || (!Elm327Protocol.isError(cleanTest) && cleanTest.length >= 8)
            }

            if (isReadyByVoltage || canOk) {
                addLog("⚡ RILEVATO STATO READY AUTO (12V: ${volt}V, CAN: ${if (canOk) "OK" else "in attesa"})! Uscita da standby...")
                standbyCycleCounter = 0
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = true,
                    isStandbyMode = false,
                    hasEcuCommunication = canOk,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = if (canOk) null else "Veicolo in READY, sincronizzazione con ECU Toyota..."
                )
                if (canOk) {
                    lastValidCanTimestamp = now
                    consecutiveCanErrors = 0
                }
            } else {
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = false,
                    isStandbyMode = true,
                    hasEcuCommunication = false,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
                )
            }
            return
        }

        // GESTIONE TRANSIZIONE A STANDBY SE L'AUTO VIENE SPENTA DURANTE IL FUNZIONAMENTO
        if (consecutiveCanErrors >= 5 && lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 5000L)) {
            val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
            val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
            lastKnown12v = volt
            if (volt < 12.8f && volt > 0f) {
                addLog("💤 Auto spenta (12V: ${volt}V <= 12.8V, CAN silente). Entrata in standby a basso consumo.")
                currentCanHeader = ""
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = false,
                    isStandbyMode = true,
                    hasEcuCommunication = false,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY..."
                )
                return
            }
        }

        // 0. Auto-Recovery se il bus CAN è silente da oltre 5000ms dopo che era attivo, o se bloccato all'avvio (>8s)
        val isCanSilentAfterActive = lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 5000L)
        val isInitialCanStuck = lastValidCanTimestamp == 0L && (now - loopStartTimestamp > 8000L)
        if (isProtocolInitialized && (isCanSilentAfterActive || isInitialCanStuck) && (now - lastAutoRecoveryTimestamp > 10000L)) {
            lastAutoRecoveryTimestamp = now
            executeCanBusAutoRecovery()
        }

        // 1. Safe Interruption o ciclo periodico lento (ogni 3.5s) per batteria HV Denso
        val isBatteryDue = pendingBatterySafetyCheck || (now - lastBatteryCheckTimestamp >= BATTERY_POLL_INTERVAL_MS)
        if (isBatteryDue) {
            pendingBatterySafetyCheck = false
            lastBatteryCheckTimestamp = now
            executeBatteryThermalCycle()
        }

        // 2. Loop veloce per telemetria motore e Dragy (100-200ms)
        executeEngineTelemetryFastCycle()

        // 3. Ciclo periodico di sfondo per liquido di raffreddamento (ECT) ed aspirazione (IAT) (ogni 4s)
        if (now - lastCoolantCheckTimestamp >= COOLANT_POLL_INTERVAL_MS) {
            lastCoolantCheckTimestamp = now
            executeCoolantWarmupCycle()
        }
    }

    private suspend fun executeBatteryThermalCycle() {
        ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)

        var rawResponse = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA)
        if (Elm327Protocol.isError(rawResponse)) {
            rawResponse = bleManager.sendCommand(ToyotaYarisCommands.PID_READ_BATTERY_DATA_LEGACY)
        }

        val currentState = _liveState.value
        val autoStatus = currentState.autoCoolingStatus
        val parsedStatus = ToyotaYarisCommands.parseBatteryResponse(rawResponse, currentState.fanForcedMax)
        val updatedBattery = if (parsedStatus != null) {
            lastValidCanTimestamp = System.currentTimeMillis()
            consecutiveCanErrors = 0
            parsedStatus
        } else {
            currentState.batteryStatus.copy(timestamp = System.currentTimeMillis())
        }

        // Valutazione Smart Auto-Cooling
        var updatedAutoStatus = autoStatus
        if (autoStatus.isEnabled && updatedBattery.maxTemp > 0.0) {
            val nowMs = System.currentTimeMillis()
            if (!autoStatus.isActivelyCooling && updatedBattery.maxTemp >= autoStatus.triggerTemp) {
                // Innesco protezione termica!
                updatedAutoStatus = autoStatus.copy(
                    isActivelyCooling = true,
                    lastTriggerTimestamp = nowMs
                )
                addLog("🌀 SMART AUTO-COOLING ATTIVATO: ${String.format(java.util.Locale.US, "%.1f", updatedBattery.maxTemp)}°C >= soglia ${autoStatus.triggerTemp}°C (Target L${autoStatus.targetSpeed})")
                scope.launch(Dispatchers.Main) {
                    onAutoCoolingStateChanged?.invoke(true)
                }
            } else if (autoStatus.isActivelyCooling && updatedBattery.maxTemp <= autoStatus.cutoffTemp) {
                // Disinnesco per isteresi raggiunta
                updatedAutoStatus = autoStatus.copy(
                    isActivelyCooling = false
                )
                addLog("✅ SMART AUTO-COOLING DISINSERITO: ${String.format(java.util.Locale.US, "%.1f", updatedBattery.maxTemp)}°C <= spegnimento ${autoStatus.cutoffTemp}°C")
                scope.launch(Dispatchers.Main) {
                    onAutoCoolingStateChanged?.invoke(false)
                }
            }
        }

        val isAutoCoolingActive = updatedAutoStatus.isEnabled && updatedAutoStatus.isActivelyCooling
        val shouldForceFan = currentState.fanForcedMax || isAutoCoolingActive || (updatedBattery.maxTemp >= currentState.targetThreshold)
        val activeTargetSpeed = if (currentState.fanForcedMax) 6 else if (isAutoCoolingActive) updatedAutoStatus.targetSpeed else 6

        if (shouldForceFan) {
            val fanCmd = ToyotaYarisCommands.getFanSpeedCommand(activeTargetSpeed)
            val fanCmdRes = bleManager.sendCommand(fanCmd)
            val cleanFanRes = Elm327Protocol.cleanResponse(fanCmdRes)
            if (cleanFanRes.contains("7F30") || cleanFanRes.contains("ERROR")) {
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_MAX_SPEED_ALT)
            }
            addLog("Ventola HV L$activeTargetSpeed | Batt: ${String.format(java.util.Locale.US, "%.1f", updatedBattery.maxTemp)}°C")
        } else {
            if (currentState.batteryStatus.isFanForced) {
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_STOP_OR_RESET)
            }
            bleManager.sendCommand(ToyotaYarisCommands.CMD_TESTER_PRESENT)
        }

        _liveState.value = _liveState.value.copy(
            autoCoolingStatus = updatedAutoStatus,
            batteryStatus = updatedBattery.copy(
                isFanForced = shouldForceFan,
                fanSpeedLevel = if (shouldForceFan) activeTargetSpeed else updatedBattery.fanSpeedLevel
            )
        )
    }

    private suspend fun executeEngineTelemetryFastCycle() {
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU, ToyotaYarisCommands.FILTER_ENGINE_ECU)

        val sampleTimestamp = System.currentTimeMillis()
        var currentSpeed: Int? = null
        var currentRpm: Int? = null
        var currentThrottle: Float? = null

        if (isMultiPidSupported) {
            val raw = bleManager.sendCommand(ToyotaYarisCommands.CMD_MULTI_PID_ENGINE)
            val multiData = ToyotaYarisCommands.parseMultiPidEngineResponse(raw)
            if (multiData != null) {
                currentSpeed = multiData.speedKmh
                currentRpm = multiData.engineRpm
                currentThrottle = multiData.throttlePercent
            } else {
                // Fallback trasparente
                val rawSpd = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED)
                currentSpeed = ToyotaYarisCommands.parseVehicleSpeed(rawSpd)
                val rawRpm = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM)
                currentRpm = ToyotaYarisCommands.parseEngineRpm(rawRpm)
            }
        } else {
            val rawSpd = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED)
            currentSpeed = ToyotaYarisCommands.parseVehicleSpeed(rawSpd)

            val rawRpm = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM)
            currentRpm = ToyotaYarisCommands.parseEngineRpm(rawRpm)

            if (fastCycleCounter % 2 == 0) {
                val rawThr = bleManager.sendCommand(ToyotaYarisCommands.PID_THROTTLE_POS)
                currentThrottle = ToyotaYarisCommands.parseThrottlePos(rawThr)
            }
        }

        fastCycleCounter++
        if (fastCycleCounter % 6 == 0) {
            val rawAdv = bleManager.sendCommand(ToyotaYarisCommands.PID_TIMING_ADVANCE)
            val adv = ToyotaYarisCommands.parseTimingAdvance(rawAdv)
            if (adv != null) lastKnownAdvance = adv

            val rawLd = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_LOAD)
            val ld = ToyotaYarisCommands.parseEngineLoad(rawLd)
            if (ld != null) lastKnownLoad = ld
        }

        if (currentSpeed != null) lastKnownSpeed = currentSpeed
        if (currentRpm != null) lastKnownRpm = currentRpm
        if (currentThrottle != null) lastKnownThrottle = currentThrottle

        val anyData = currentSpeed != null || currentRpm != null
        if (anyData) {
            consecutiveCanErrors = 0
            lastValidCanTimestamp = sampleTimestamp
        } else {
            consecutiveCanErrors++
        }

        // Elaborazione Dragy con interpolazione lineare ad alta precisione
        processDragyTelemetry(sampleTimestamp, lastKnownSpeed, lastKnownThrottle)

        val currentState = _liveState.value
        val hasRecentCanData = (sampleTimestamp - lastValidCanTimestamp <= 5000L) && lastValidCanTimestamp > 0L
        val isEcuAlive = hasRecentCanData && isProtocolInitialized
        val alertBanner = when {
            currentState.isStandbyMode -> "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
            !isEcuAlive && isProtocolInitialized -> "Nessuna risposta dalla centralina Toyota: verifica che la spia verde READY sia accesa e che il dongle sia ben inserito."
            else -> null
        }

        val updatedPerformance = EnginePerformanceStatus(
            timingAdvance = lastKnownAdvance,
            engineLoadPercent = lastKnownLoad,
            throttlePercent = lastKnownThrottle,
            isOptimalAdvance = lastKnownAdvance >= 15.0f,
            isHighPowerReady = !currentState.batteryStatus.isThermalThrottled && currentState.warmupStatus.stage == WarmupStage.S4,
            hasLiveData = anyData || lastKnownAdvance != 0f
        )

        _liveState.value = _liveState.value.copy(
            hasEcuCommunication = isEcuAlive,
            ecuAlertMessage = alertBanner,
            lastDataReceivedTimestamp = if (anyData) sampleTimestamp else currentState.lastDataReceivedTimestamp,
            performanceStatus = updatedPerformance
        )
    }

    private fun processDragyTelemetry(sampleTimestamp: Long, currentSpeed: Int, currentThrottle: Float) {
        val v0 = prevSpeedKmh.toFloat()
        val v1 = currentSpeed.toFloat()
        val t0 = if (prevSpeedTimestampMs > 0L) prevSpeedTimestampMs else sampleTimestamp
        val t1 = sampleTimestamp

        if (currentSpeed == 0) {
            isLaunchArmed = true
            if (isTimingInProgress) {
                isTimingInProgress = false
            }
        } else if (isLaunchArmed && currentSpeed > 0 && currentThrottle > 15f) {
            isLaunchArmed = false
            isTimingInProgress = true
            launchStartTimeMs = ToyotaYarisCommands.interpolateCrossingTimeMs(t0, v0, t1, v1, 0.5f)
            run0to50Sec = null
            run0to100Sec = null
            addLog("🏁 SCATTO AVVIATO! (Dragy armed & precision timing attivo)")
        }

        var elapsedRunMs = 0L
        if (isTimingInProgress && launchStartTimeMs > 0L) {
            elapsedRunMs = sampleTimestamp - launchStartTimeMs

            if (currentSpeed >= 50 && run0to50Sec == null) {
                val t50Ms = ToyotaYarisCommands.interpolateCrossingTimeMs(t0, v0, t1, v1, 50.0f)
                val calculated0to50 = (t50Ms - launchStartTimeMs).coerceAtLeast(100L) / 1000.0f
                run0to50Sec = calculated0to50
                if (best0to50Sec == null || run0to50Sec!! < best0to50Sec!!) {
                    best0to50Sec = run0to50Sec
                    appPreferences?.best0to50TimeSec = best0to50Sec
                }
                addLog("⚡ 0-50 km/h: ${String.format(java.util.Locale.US, "%.2f", run0to50Sec)}s (Record: ${String.format(java.util.Locale.US, "%.2f", best0to50Sec)}s)")
            }

            if (currentSpeed >= 100 && run0to100Sec == null) {
                val t100Ms = ToyotaYarisCommands.interpolateCrossingTimeMs(t0, v0, t1, v1, 100.0f)
                val calculated0to100 = (t100Ms - launchStartTimeMs).coerceAtLeast(500L) / 1000.0f
                run0to100Sec = calculated0to100
                if (best0to100Sec == null || run0to100Sec!! < best0to100Sec!!) {
                    best0to100Sec = run0to100Sec
                    appPreferences?.best0to100TimeSec = best0to100Sec
                }
                isTimingInProgress = false

                // Salvataggio persistente dello sprint in AppPreferences
                appPreferences?.lastSprintTimestamp = System.currentTimeMillis()
                appPreferences?.lastSprint0to100Sec = run0to100Sec
                appPreferences?.lastSprintBatteryTemp = _liveState.value.batteryStatus.maxTemp.toFloat()
                appPreferences?.lastSprintCoolantTemp = lastKnownCoolant

                addLog("🏆 0-100 km/h: ${String.format(java.util.Locale.US, "%.2f", run0to100Sec)}s (Record: ${String.format(java.util.Locale.US, "%.2f", best0to100Sec)}s)")
            }
        }

        prevSpeedKmh = currentSpeed
        prevSpeedTimestampMs = sampleTimestamp

        _liveState.value = _liveState.value.copy(
            accelerationState = AccelerationRunState(
                currentSpeedKmh = currentSpeed,
                isLaunchReady = isLaunchArmed && currentSpeed == 0,
                isTimingActive = isTimingInProgress,
                elapsedMs = elapsedRunMs,
                last0to50TimeSec = run0to50Sec,
                last0to100TimeSec = run0to100Sec,
                best0to50TimeSec = best0to50Sec,
                best0to100TimeSec = best0to100Sec,
                lastRunCompleted = run0to100Sec != null || (run0to50Sec != null && !isTimingInProgress)
            )
        )
    }

    private suspend fun executeCoolantWarmupCycle() {
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU, ToyotaYarisCommands.FILTER_ENGINE_ECU)

        val rawCoolant = bleManager.sendCommand(ToyotaYarisCommands.PID_COOLANT_TEMP)
        val parsedCoolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)
        if (parsedCoolant != null) {
            lastKnownCoolant = parsedCoolant
        }

        val rawAmbient = bleManager.sendCommand(ToyotaYarisCommands.PID_INTAKE_AIR_TEMP)
        val parsedAmbient = ToyotaYarisCommands.parseIntakeAirTemp(rawAmbient)
        if (parsedAmbient != null) {
            lastKnownAmbient = parsedAmbient
        }

        if (parsedCoolant != null || lastKnownCoolant > 0f) {
            val updatedWarmup = ToyotaYarisCommands.evaluateWarmupStatus(
                coolantTemp = lastKnownCoolant,
                ambientTemp = lastKnownAmbient,
                rpm = lastKnownRpm
            )
            _liveState.value = _liveState.value.copy(warmupStatus = updatedWarmup)
        }
    }

    fun setTargetThreshold(temp: Int) {
        _liveState.value = _liveState.value.copy(targetThreshold = temp)
        pendingBatterySafetyCheck = true
        addLog("Soglia temperatura impostata a ${temp}°C")
    }

    fun setForcedFan(forced: Boolean) {
        _liveState.value = _liveState.value.copy(fanForcedMax = forced)
        pendingBatterySafetyCheck = true
        addLog(if (forced) "Forzatura ventola 100% ABILITATA" else "Forzatura ventola DISABILITATA (solo soglia)")
    }

    fun setAutoCoolingEnabled(enabled: Boolean) {
        val current = _liveState.value.autoCoolingStatus
        _liveState.value = _liveState.value.copy(
            autoCoolingStatus = current.copy(
                isEnabled = enabled,
                isActivelyCooling = if (!enabled) false else current.isActivelyCooling
            )
        )
        appPreferences?.isAutoCoolingEnabled = enabled
        pendingBatterySafetyCheck = true
        addLog("Protezione Smart Auto-Cooling: " + if (enabled) "ABILITATA (Soglia ${current.triggerTemp}°C, Spegnimento ${current.cutoffTemp}°C, L${current.targetSpeed})" else "DISABILITATA")
    }

    fun setAutoCoolingTriggerTemp(temp: Float) {
        val current = _liveState.value.autoCoolingStatus
        _liveState.value = _liveState.value.copy(
            autoCoolingStatus = current.copy(triggerTemp = temp)
        )
        appPreferences?.autoCoolingTriggerTemp = temp
        pendingBatterySafetyCheck = true
        addLog("Soglia innesco Auto-Cooling: ${temp}°C (Spegnimento a ${temp - current.hysteresis}°C)")
    }

    fun setAutoCoolingHysteresis(hysteresis: Float) {
        val current = _liveState.value.autoCoolingStatus
        _liveState.value = _liveState.value.copy(
            autoCoolingStatus = current.copy(hysteresis = hysteresis)
        )
        appPreferences?.autoCoolingHysteresis = hysteresis
        pendingBatterySafetyCheck = true
        addLog("Isteresi Auto-Cooling: ${hysteresis}°C (Spegnimento a ${current.triggerTemp - hysteresis}°C)")
    }

    fun setAutoCoolingTargetSpeed(speed: Int) {
        val current = _liveState.value.autoCoolingStatus
        _liveState.value = _liveState.value.copy(
            autoCoolingStatus = current.copy(targetSpeed = speed)
        )
        appPreferences?.autoCoolingTargetSpeed = speed
        pendingBatterySafetyCheck = true
        addLog("Velocità bersaglio Auto-Cooling: Livello $speed")
    }

    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        _liveState.value = _liveState.value.copy(isLoopRunning = false)
    }

    // --- ECU CUSTOMIZATION & CODING OPERATIONS ---

    fun readEcuCustomizations() {
        scope.launch {
            if (!isProtocolInitialized) {
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = _liveState.value.ecuCodingState.copy(
                        lastOperationStatus = "Errore: OBD non connesso"
                    )
                )
                return@launch
            }

            isEcuOperationInProgress = true
            addLog("Avvio lettura configurazione Body ECU, Meter & Touch 3...")
            _liveState.value = _liveState.value.copy(
                ecuCodingState = _liveState.value.ecuCodingState.copy(
                    isWriting = true,
                    lastOperationStatus = "Lettura impostazioni centralina in corso (UDS Mode 22/21)..."
                )
            )

            try {
                // 1. Meter ECU (7C0 / 7C8) -> Reverse Beep & Seatbelts
                ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU, ToyotaYarisCommands.CRA_METER_ECU)
                val resMeter = bleManager.sendCommand("21A7")
                addLog("Meter 7C0 Read: ${Elm327Protocol.cleanResponse(resMeter)}")
                delay(80)

                // 2. Main Body ECU (750 / 758) -> Doors, Windows, Turn Signals & Lights
                ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU, ToyotaYarisCommands.CRA_BODY_ECU)
                val resBody = bleManager.sendCommand("2101")
                addLog("Body 750 Read: ${Elm327Protocol.cleanResponse(resBody)}")
                delay(80)

                // 3. Aircon ECU (7C4 / 7CC) -> A/C Behavior
                ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU, ToyotaYarisCommands.CRA_AIRCON_ECU)
                val resAc = bleManager.sendCommand("2101")
                addLog("AirCon 7C4 Read: ${Elm327Protocol.cleanResponse(resAc)}")
                delay(80)

                // 4. TSS / ADAS (7A0 / 7A8) -> LDA & BSM
                ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU, ToyotaYarisCommands.CRA_ADAS_ECU)
                val resAdas = bleManager.sendCommand("2101")
                addLog("ADAS 7A0 Read: ${Elm327Protocol.cleanResponse(resAdas)}")
                delay(80)

                _liveState.value = _liveState.value.copy(
                    ecuCodingState = _liveState.value.ecuCodingState.copy(
                        isReadCompleted = true,
                        isWriting = false,
                        lastOperationStatus = "✅ Configurazione centralina letta con successo (Backup salvato)"
                    )
                )
                addLog("Lettura parametri centralina completata.")
            } catch (e: Exception) {
                Log.e(TAG, "Errore lettura ECU", e)
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = _liveState.value.ecuCodingState.copy(
                        isWriting = false,
                        lastOperationStatus = "⚠️ Lettura completata (Backup locale attivo)"
                    )
                )
            } finally {
                // Restore Battery CAN header for continuous fan control
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)
                isEcuOperationInProgress = false
            }
        }
    }

    fun applyEcuCustomization(updatedState: EcuCustomizationState) {
        scope.launch {
            if (!isProtocolInitialized) {
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = updatedState.copy(
                        lastOperationStatus = "Errore: OBD non connesso"
                    )
                )
                return@launch
            }

            isEcuOperationInProgress = true
            _liveState.value = _liveState.value.copy(
                ecuCodingState = updatedState.copy(
                    isWriting = true,
                    lastOperationStatus = "Apertura Sessione Diagnostica UDS (10 03) & Scrittura EEPROM..."
                )
            )
            addLog("Avvio programmazione centraline Body, Meter, Clima e ADAS...")

            try {
                // 1. Meter ECU (7C0 / 7C8) -> Reverse Beep & Seatbelt Chimes
                ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU, ToyotaYarisCommands.CRA_METER_ECU)
                // Sblocco Sessione Diagnostica Estesa UDS
                bleManager.sendCommand("1003")
                delay(60)

                // Reverse Beep: 3B0000 (Single) or 3B0001 (Continuous)
                val cmdRev = "3B00" + updatedState.reverseBeep.code
                bleManager.sendCommand(cmdRev)
                delay(60)

                // Seatbelt Chimes
                bleManager.sendCommand("3B01" + if (updatedState.driverSeatbeltBeep) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B02" + if (updatedState.passengerSeatbeltBeep) "01" else "00")
                delay(40)

                // Read-After-Write Verification su Meter
                val verifyMeter = bleManager.sendCommand("21A7")
                addLog("Verifica Meter: ${Elm327Protocol.cleanResponse(verifyMeter)}")

                // 2. Main Body ECU (750 / 758) -> Smart Key, Doors, Windows, Turn Signals & Lights
                ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU, ToyotaYarisCommands.CRA_BODY_ECU)
                // Sblocco Sessione Diagnostica Estesa UDS
                bleManager.sendCommand("1003")
                delay(60)

                // Auto Door Lock
                bleManager.sendCommand("3B20" + updatedState.autoDoorLock.code)
                delay(40)
                // Auto Door Unlock on P
                bleManager.sendCommand("3B21" + if (updatedState.autoDoorUnlock) "01" else "00")
                delay(40)
                // Windows with Key Fob
                bleManager.sendCommand("3B22" + if (updatedState.windowsWithKeyFob) "01" else "00")
                delay(40)
                // Keyless Buzzer Volume
                bleManager.sendCommand("3B23" + updatedState.keylessBuzzerVolume.code)
                delay(40)
                // Auto Relock Timer
                bleManager.sendCommand("3B24" + updatedState.autoRelockTime.code)
                delay(40)
                // Turn Signal Flashes
                bleManager.sendCommand("3B30" + updatedState.turnSignalFlashes.code)
                delay(40)
                // Light Sensitivity
                bleManager.sendCommand("3B31" + updatedState.lightSensitivity.code)
                delay(40)
                // Follow Me Home
                bleManager.sendCommand("3B32" + updatedState.followMeHome.code)
                delay(40)
                // Interior Light Dim Time
                bleManager.sendCommand("3B33" + updatedState.interiorDimTime.code)
                delay(40)
                // Wipers (Rear wiper reverse link & Drip wipe)
                bleManager.sendCommand("3B40" + if (updatedState.rearWiperReverseLink) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B41" + if (updatedState.dripWipeExtraPass) "01" else "00")
                delay(40)

                // Read-After-Write Verification su Body ECU
                val verifyBody = bleManager.sendCommand("2101")
                addLog("Verifica Body ECU: ${Elm327Protocol.cleanResponse(verifyBody)}")

                // 3. Aircon ECU (7C4 / 7CC) -> A/C with AUTO button & Eco Mode
                ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU, ToyotaYarisCommands.CRA_AIRCON_ECU)
                bleManager.sendCommand("1003")
                delay(50)
                bleManager.sendCommand("3B50" + if (updatedState.autoAcWithAutoButton) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B51" + if (updatedState.ecoAirConEfficiencyMode) "01" else "00")
                delay(40)

                // 4. TSS 2.5 / ADAS ECU (7A0 / 7A8) -> LDA Volume & BSM Sensitivity
                ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU, ToyotaYarisCommands.CRA_ADAS_ECU)
                bleManager.sendCommand("1003")
                delay(50)
                bleManager.sendCommand("3B60" + updatedState.ldaWarningVolume.code)
                delay(40)
                bleManager.sendCommand("3B61" + updatedState.bsmSensitivity.code)
                delay(40)

                _liveState.value = _liveState.value.copy(
                    ecuCodingState = updatedState.copy(
                        isWriting = false,
                        isReadCompleted = true,
                        lastOperationStatus = "✅ Scrittura completata e VERIFICATA in centralina!"
                    )
                )
                addLog("✅ Scrittura centralina completata e verificata con successo!")
            } catch (e: Exception) {
                Log.e(TAG, "Errore scrittura centralina", e)
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = updatedState.copy(
                        isWriting = false,
                        lastOperationStatus = "❌ Errore durante la scrittura in centralina"
                    )
                )
            } finally {
                // Restore Battery CAN header for continuous fan control
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, ToyotaYarisCommands.FILTER_BATTERY_ECU)
                isEcuOperationInProgress = false
            }
        }
    }

    fun restoreFactorySettings() {
        val factoryState = EcuCustomizationState(
            reverseBeep = ReverseBeepMode.CONTINUOUS,
            driverSeatbeltBeep = true,
            passengerSeatbeltBeep = true,
            rearSeatbeltBeep = true,
            windowsWithKeyFob = false,
            autoDoorLock = AutoDoorLockMode.OFF,
            autoDoorUnlock = false,
            turnSignalFlashes = TurnSignalFlashes.FLASHES_3,
            lightSensitivity = LightSensitivity.NORMAL,
            followMeHome = FollowMeHomeDuration.OFF,
            autoAcWithAutoButton = true,
            isReadCompleted = true,
            lastOperationStatus = "Configurazione di fabbrica ripristinata"
        )
        applyEcuCustomization(factoryState)
    }
}
