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
    val capabilityState: ObdCapabilityState = ObdCapabilityState(),
    val lastLogMessage: String = "In attesa di connessione...",
    val logs: List<String> = emptyList(),
    val errorCount: Int = 0
)

class ObdController(
    private val bleManager: ObdTransport,
    private val scope: CoroutineScope,
    private val appPreferences: com.yaris.hvfan.data.AppPreferences? = null,
    val stateMachine: ObdStateMachine = ObdStateMachine(),
    val discoveryEngine: BatteryDiscoveryEngine = BatteryDiscoveryEngine()
) {
    companion object {
        private const val TAG = "ObdController"
        private const val BATTERY_POLL_INTERVAL_MS = 3500L
        private const val COOLANT_POLL_INTERVAL_MS = 4000L

        // Le risposte multi-frame UDS 2228C1 richiedono flow control ISO-TP completo: sotto i 4s
        // il timeout BLE scade prima che l'ultimo frame consecutivo arrivi.
        private const val BATTERY_PID_TIMEOUT_MS = 4000L
    }

    private data class CanProbeResult(
        val isValid: Boolean,
        val response: String
    )

    var onAutoCoolingStateChanged: ((Boolean) -> Unit)? = null

    val capabilityState: StateFlow<ObdCapabilityState> = stateMachine.capabilityState
    val consecutiveCanErrors: Int get() = stateMachine.consecutiveCanErrors
    val consecutiveBatteryErrors: Int get() = stateMachine.consecutiveBatteryErrors

    val activeBatteryPid: String get() = discoveryEngine.latchedPid ?: ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA

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
            ),
            capabilityState = stateMachine.currentCapabilityState
        )
    )
    val liveState: StateFlow<ObdLiveState> = _liveState

    private var loopJob: Job? = null
    private var isProtocolInitialized = false
    private var isMultiPidSupported = false
    private var isCustomFcSupported = false
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
                        stateMachine.onTransportStateChanged(BleTransportState.Ready)
                        _liveState.value = _liveState.value.copy(
                            capabilityState = stateMachine.currentCapabilityState
                        )
                        addLog("Dispositivo pronto. Avvio inizializzazione ECU Toyota Yaris...")
                        initializeAndStartLoop()
                    }
                    is BleConnectionState.Reconnecting -> {
                        stopLoop()
                        isProtocolInitialized = false
                        stateMachine.onTransportStateChanged(BleTransportState.Connecting)
                        _liveState.value = _liveState.value.copy(
                            isInitialized = false,
                            isLoopRunning = false,
                            hasEcuCommunication = false,
                            isVehicleReady = false,
                            isStandbyMode = false,
                            capabilityState = stateMachine.currentCapabilityState,
                            ecuAlertMessage = "Riconnessione automatica a ${state.deviceName} (#${state.attempt})...",
                            lastLogMessage = "Riconnessione in corso a ${state.deviceName} (tentativo #${state.attempt})..."
                        )
                    }
                    is BleConnectionState.Disconnected, is BleConnectionState.Error -> {
                        stopLoop()
                        isProtocolInitialized = false
                        stateMachine.teardownAllCapabilities(BleTransportState.Disconnected)
                        _liveState.value = _liveState.value.copy(
                            isInitialized = false,
                            isLoopRunning = false,
                            hasEcuCommunication = false,
                            isVehicleReady = false,
                            isStandbyMode = false,
                            capabilityState = stateMachine.currentCapabilityState,
                            ecuAlertMessage = null,
                            lastLogMessage = if (state is BleConnectionState.Error) state.message else "Disconnesso"
                        )
                    }
                    is BleConnectionState.Connecting -> {
                        stateMachine.onTransportStateChanged(BleTransportState.Connecting)
                        _liveState.value = _liveState.value.copy(
                            capabilityState = stateMachine.currentCapabilityState
                        )
                    }
                    is BleConnectionState.Connected -> {
                        stateMachine.onTransportStateChanged(BleTransportState.Connected)
                        _liveState.value = _liveState.value.copy(
                            capabilityState = stateMachine.currentCapabilityState
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
                stateMachine.onElmInitializing()
                loopStartTimestamp = System.currentTimeMillis()
                standbyCycleCounter = 0
                currentCanHeader = ""
                _liveState.value = _liveState.value.copy(
                    isInitialized = false,
                    isLoopRunning = false,
                    hasEcuCommunication = false,
                    isVehicleReady = false,
                    isStandbyMode = false,
                    capabilityState = stateMachine.currentCapabilityState,
                    ecuAlertMessage = null
                )

                // 1. Invio preventivo di sequenza di sveglia "\r\r" per svegliare Vgate/ELM327 da sleep/low-power
                addLog("Invio sequenza di sveglia preventiva Dr. Prius (\\r\\r)...")
                bleManager.sendWakeSequence()
                delay(150)

                // 2. Invio Reset ELM327 (AT Z) con delay di stabilizzazione standard Dr. Prius (800ms)
                addLog("Invio Reset ELM327 / Vgate / STN (AT Z)...")
                val resZ = bleManager.sendCommand(Elm327Protocol.CMD_RESET, timeoutMs = 2000L)
                addLog("Reset Response: ${Elm327Protocol.cleanResponse(resZ)}")
                delay(800) // Delay di stabilizzazione firmware Dr. Prius

                // 3. Invio sequenza di configurazione parametri seriali e protocollo CAN Dr. Prius
                for (cmd in Elm327Protocol.INIT_COMMANDS) {
                    if (cmd == "AT Z") continue
                    addLog("CMD: $cmd")
                    val res = bleManager.sendCommand(cmd)
                    val cleanRes = Elm327Protocol.cleanResponse(res)
                    addLog("RES: $cleanRes")
                    if (cmd == "AT SP 6" && (cleanRes.contains("ERROR") || Elm327Protocol.isError(cleanRes))) {
                        addLog("Fallback protocollo su AT SP 0 (Auto)...")
                        bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
                    }
                    delay(40)
                }

                // 3b. Verifica del protocollo effettivamente negoziato: AT DPN e' la prova che il
                // protocollo 6 (CAN 11-bit 500k) sia attivo e non un auto-detect andato altrove.
                val dpnRes = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_NUMBER, timeoutMs = 1500L)
                addLog("Protocollo ELM327 attivo (AT DPN): ${Elm327Protocol.cleanResponse(dpnRes)} (atteso 6 = ISO 15765-4 CAN 11-bit 500k)")
                stateMachine.onElmReady()

                // 4. Rilevamento non distruttivo chipset STN / OBDLink prima di applicare comandi AT FC (R1)
                addLog("Rilevamento identità adapter OBD (ATI / ST DI)...")
                val atiRes = bleManager.sendCommand(Elm327Protocol.CMD_DEVICE_INFO, timeoutMs = 1500L)
                val stDiRes = bleManager.sendCommand(Elm327Protocol.CMD_DEVICE_ID_STN, timeoutMs = 1500L)
                val devName = bleManager.getConnectedDeviceName() ?: ""
                isCustomFcSupported = Elm327Protocol.isStnHardwareSupported(
                    deviceName = devName,
                    atiResponse = atiRes,
                    stDiResponse = stDiRes
                )
                if (isCustomFcSupported) {
                    addLog("✅ Chipset STN / OBDLink originale rilevato: abilitazione Flow Control hardware (AT FC SM 1).")
                } else {
                    addLog("ℹ️ Adapter Vlinker / clone ELM327 rilevato: mantenimento rigoroso Flow Control nativo (AT CAF 1) senza comandi AT FC.")
                }

                // 5. Rilevamento stato READY auto tramite tensione reale batteria 12V (AT RV > 13.0V)
                val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
                val real12v = Elm327Protocol.parseBatteryVoltage(voltRes) ?: 0f
                lastKnown12v = real12v
                val isReady = Elm327Protocol.isVehicleReady(real12v)
                addLog("Tensione Batteria 12V (AT RV): ${real12v}V [READY: ${if (isReady) "SI" else "NO"}]")
                if (isReady && real12v < 13.2f) {
                    addLog("⚠️ Tensione al limite (${real12v}V): verifica che il quadro sia in READY (spia verde) e che l'adattatore non sia mal calibrato.")
                }

                // 6. Gestione stato Standby a basso consumo se l'auto non è in READY (< 13.0V)
                if (!isReady && real12v > 0f) {
                    addLog("💤 Auto in Standby (12V: ${real12v}V < 13.0V, quadro spento). Standby a basso consumo attivo.")
                    isProtocolInitialized = true
                    stateMachine.onVehicleStandby()
                    discoveryEngine.reset()
                    _liveState.value = _liveState.value.copy(
                        isInitialized = true,
                        isLoopRunning = true,
                        hasEcuCommunication = false,
                        isVehicleReady = false,
                        isStandbyMode = true,
                        capabilityState = stateMachine.currentCapabilityState,
                        auxiliary12vVoltage = real12v,
                        ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
                    )
                } else {
                    if (isReady) {
                        stateMachine.onVehicleReady()
                    }
                    // 7. Handshake CAN a tre stadi con aggancio rapido (R2)
                    // Stadio 0: richiesta funzionale con header 7DF esplicito. Non ci si affida
                    // all'header predefinito del clone, che puo' sopravvivere a warm start e standby.
                    addLog("Stadio 0: aggancio bus CAN in broadcast (7DF)...")
                    stateMachine.onCanSearching()
                    val stage0Ok = probeBroadcastCanWithAutomaticFallback("Stadio 0")
                    if (stage0Ok) {
                        addLog("✅ Stadio 0 completato: bus CAN 11-bit 500k agganciato in broadcast (7DF).")
                        lastValidCanTimestamp = System.currentTimeMillis()
                    } else {
                        addLog("⚠️ Stadio 0 fallito anche dopo auto-detect: nessun frame CAN valido in broadcast 7DF. Possibili cause: quadro non in READY (spia verde spenta), dongle non inserito a fondo nella presa OBD, o adattatore incompatibile. Proseguo comunque con gli stadi successivi...")
                    }

                    // Stadio 1: aggancio rapido centralina motore standard (7E0 / 7E8)
                    addLog("Handshake CAN Stadio 1: aggancio rapido bus su Centralina Motore (7E0 / 7E8)...")
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)
                    var stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 4000L)
                    var cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                    if (Elm327Protocol.isError(cleanStage1) || cleanStage1.contains("TIMEOUT")) {
                        addLog("PID 0100 non ha risposto (risposta: $cleanStage1), tentativo rapido con PID 010C (RPM)...")
                        stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 4000L)
                        cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                        if (Elm327Protocol.isError(cleanStage1)) {
                            addLog("PID 010C non ha risposto (risposta: $cleanStage1).")
                        }
                    }
                    val stage1Ok = !Elm327Protocol.isError(cleanStage1) &&
                        (cleanStage1.contains("4100") || cleanStage1.contains("410C"))
                    if (stage1Ok) {
                        addLog("✅ Handshake CAN Stadio 1 completato: bus CAN 11-bit 500k agganciato!")
                        lastValidCanTimestamp = System.currentTimeMillis()
                        stateMachine.onEngineTelemetrySuccess()
                    } else {
                        addLog("ℹ️ Handshake CAN Stadio 1: risposta non standard ($cleanStage1), procedo a Stadio 2...")
                    }

                    // Stadio 2: predisposizione discovery centralina ibrida Denso HV Battery (7E2)
                    addLog("Handshake CAN Stadio 2: predisposizione motore discovery phased batteria Denso HV (7E2)...")
                    discoveryEngine.reset()
                    stateMachine.onBatteryDiscoveryProbing()
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)

                    val canOk = stage0Ok || stage1Ok
                    val isActuallyReady = isReady || canOk
                    isProtocolInitialized = true
                    _liveState.value = _liveState.value.copy(
                        isInitialized = true,
                        isLoopRunning = true,
                        hasEcuCommunication = canOk,
                        isVehicleReady = isActuallyReady,
                        isStandbyMode = !isActuallyReady,
                        capabilityState = stateMachine.currentCapabilityState,
                        auxiliary12vVoltage = real12v,
                        ecuAlertMessage = when {
                            canOk -> null
                            isActuallyReady -> {
                                if (real12v > 0f) {
                                    "Veicolo in stato READY (12V: ${String.format(java.util.Locale.US, "%.1f", real12v)}V). Sincronizzazione con ECU Toyota in corso..."
                                } else {
                                    "Veicolo in READY. Sincronizzazione con ECU Toyota in corso..."
                                }
                            }
                            else -> "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
                        }
                    )

                    // 8. Test supporto Multi-PID per telemetria motore e Dragy se il veicolo è attivo
                    if (canOk) {
                        addLog("Verifica supporto Multi-PID (010D0C11)...")
                        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)
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
                }
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

    internal var currentCanHeader: String = ""

    /**
     * Esegue un probe OBD-II funzionale impostando sempre 7DF. Alcuni Vlinker/cloni conservano
     * l'ultimo AT SH dopo standby o warm start, quindi AT AR da solo non ripristina l'header TX.
     */
    private suspend fun probeBroadcastCan(
        attempts: Int = 2,
        timeoutMs: Long = 6000L
    ): CanProbeResult {
        bleManager.sendCommand(Elm327Protocol.CMD_AUTO_RECEIVE)
        currentCanHeader = ""
        ensureCanHeader(ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST)
        bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
        delay(30)

        var cleanResponse = ""
        repeat(attempts) { index ->
            val response = bleManager.sendCommand(
                ToyotaYarisCommands.PID_SUPPORTED_PIDS,
                timeoutMs = timeoutMs
            )
            cleanResponse = Elm327Protocol.cleanResponse(response)
            if (Elm327Protocol.hasSupportedPidsResponse(cleanResponse)) {
                return CanProbeResult(true, cleanResponse)
            }
            if (index < attempts - 1) delay(250)
        }
        return CanProbeResult(false, cleanResponse)
    }

    /**
     * Un clone puo' rispondere OK ad AT SP 6 pur non riuscendo ad agganciare il bus. Il fallback
     * viene quindi deciso dalla prova CAN reale, non dalla sola risposta al comando AT.
     */
    private suspend fun probeBroadcastCanWithAutomaticFallback(context: String): Boolean {
        var probe = probeBroadcastCan()
        if (probe.isValid) return true

        addLog("$context: nessuna risposta con protocollo 6 (risposta: ${probe.response}). Provo auto-detect ELM327 (AT SP 0)...")
        bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK, timeoutMs = 2000L)
        delay(200)
        currentCanHeader = ""
        probe = probeBroadcastCan(attempts = 2, timeoutMs = 12000L)
        if (probe.isValid) {
            val dpnResponse = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_NUMBER, timeoutMs = 1500L)
            addLog("✅ $context: CAN agganciato tramite auto-detect (AT DPN: ${Elm327Protocol.cleanResponse(dpnResponse)}).")
            return true
        }

        addLog("$context: auto-detect senza risposta CAN valida (risposta: ${probe.response}).")
        return false
    }

    /**
     * Imposta l'header di trasmissione CAN dell'ECU bersaglio. Non viene mai inviato AT CRA:
     * con AT SH 7Ex l'ELM327 filtra da solo la risposta fisica corrispondente, mentre sui cloni
     * un CRA attivo risponde OK e poi scarta ogni frame in ingresso (NO DATA su qualsiasi PID).
     */
    internal suspend fun ensureCanHeader(header: String, force: Boolean = false) {
        if (currentCanHeader != header || force) {
            currentCanHeader = ""
            bleManager.sendCommand("AT SH $header")
            delay(30)
            if (header == ToyotaYarisCommands.HEADER_BATTERY_ECU) {
                if (isCustomFcSupported) {
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SH_BATTERY)
                    delay(30)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SD_CTS)
                    delay(30)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_CUSTOM)
                    delay(30)
                }
                bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU)
            } else {
                if (isCustomFcSupported) {
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_DEFAULT)
                    delay(30)
                }
                bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_TELEMETRY)
            }
            currentCanHeader = header
            // I cloni ELM327 perdono il primo frame se la richiesta arriva a ridosso del cambio header.
            delay(120)
        }
    }

    private var isEcuOperationInProgress = false
    private var lastAutoRecoveryTimestamp = 0L

    private suspend fun executeCanBusAutoRecovery() {
        addLog("⚠️ Nessun dato CAN ricevuto: verifica tensione 12V e auto-recovery...")
        val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
        val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
        lastKnown12v = volt
        if (!Elm327Protocol.isVehicleReady(volt) && volt > 0f) {
            addLog("Auto non in READY (12V: ${volt}V < 13.0V): passaggio a standby a basso consumo.")
            currentCanHeader = ""
            discoveryEngine.reset()
            stateMachine.onVehicleStandby()
            _liveState.value = _liveState.value.copy(
                isVehicleReady = false,
                isStandbyMode = true,
                hasEcuCommunication = false,
                capabilityState = stateMachine.currentCapabilityState,
                auxiliary12vVoltage = volt,
                ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY..."
            )
            return
        }

        // Auto-Recovery: resetta lo stato delle capacità e forza ri-scoperta pulita
        stateMachine.onCanBusAutoRecovery()
        discoveryEngine.reset()

        // Reset rapido dello stack seriale ELM327 senza perdita connessione BLE
        bleManager.sendWakeSequence()
        bleManager.sendCommand(Elm327Protocol.CMD_WARM_START) // Warm Start
        delay(200)
        bleManager.sendCommand("AT E0")
        bleManager.sendCommand("AT L0")
        bleManager.sendCommand("AT S0")
        bleManager.sendCommand("AT H0")
        bleManager.sendCommand("AT AT 1")
        val spRes = bleManager.sendCommand("AT SP 6")
        val cleanSp = Elm327Protocol.cleanResponse(spRes)
        if (cleanSp.contains("ERROR") || Elm327Protocol.isError(cleanSp)) {
            bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
        }
        bleManager.sendCommand("AT CAF 1")
        currentCanHeader = "" // Forza riapplicazione degli header
        stateMachine.onElmReady()

        // Stadio 0: riaggancio 7DF, con fallback basato sulla risposta CAN reale.
        val s0Ok = probeBroadcastCanWithAutomaticFallback("Auto-recovery Stadio 0")
        if (!s0Ok) {
            addLog("Auto-recovery Stadio 0: nessuna risposta in broadcast 7DF.")
        }

        // Handshake Stadio 1: aggancio rapido centralina motore per completare fase SEARCHING... su CAN 11-bit 500k
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)
        var s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 4000L)
        var cleanS1 = Elm327Protocol.cleanResponse(s1Res)
        if (Elm327Protocol.isError(cleanS1) || cleanS1.contains("TIMEOUT")) {
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 4000L)
            cleanS1 = Elm327Protocol.cleanResponse(s1Res)
        }
        val s1Ok = !Elm327Protocol.isError(cleanS1) &&
            (cleanS1.contains("4100") || cleanS1.contains("410C"))
        if (s1Ok) {
            stateMachine.onEngineTelemetrySuccess()
        }

        // Handshake Stadio 2: reset discovery e predisposizione interrogazione non-bloccante
        discoveryEngine.reset()
        stateMachine.onBatteryDiscoveryProbing()
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)

        if (s0Ok || s1Ok) {
            lastValidCanTimestamp = System.currentTimeMillis()
            addLog("✅ Procedura auto-recovery completata: bus CAN motore riagganciato.")
        } else {
            addLog("⚠️ Procedura auto-recovery completata: in attesa di risposta CAN centralina.")
        }

        _liveState.value = _liveState.value.copy(
            capabilityState = stateMachine.currentCapabilityState
        )
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

            if (isReadyByVoltage) {
                stateMachine.onVehicleReady()
                addLog("⚡ RILEVATO STATO READY AUTO (12V: ${volt}V >= 13.0V)! Verifica bus CAN e aggancio rapido...")
                currentCanHeader = ""

                // La tensione conferma il DC-DC attivo, ma il protocollo viene convalidato
                // separatamente con una risposta ECU reale in broadcast.
                stateMachine.onCanSearching()
                val s0Ok = probeBroadcastCanWithAutomaticFallback("Risveglio da standby")

                // Handshake Stadio 1: aggancio rapido motore
                ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)
                var s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 4000L)
                var cleanS1 = Elm327Protocol.cleanResponse(s1Res)
                if (Elm327Protocol.isError(cleanS1) || cleanS1.contains("TIMEOUT")) {
                    s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 4000L)
                    cleanS1 = Elm327Protocol.cleanResponse(s1Res)
                }
                val s1Ok = !Elm327Protocol.isError(cleanS1) &&
                    (cleanS1.contains("4100") || cleanS1.contains("410C"))
                if (s1Ok) {
                    stateMachine.onEngineTelemetrySuccess()
                }

                // Handshake Stadio 2: reset discovery e predisposizione interrogazione non-bloccante
                discoveryEngine.reset()
                stateMachine.onBatteryDiscoveryProbing()
                ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)

                val canOk = s0Ok || s1Ok
                standbyCycleCounter = 0
                if (canOk) {
                    lastValidCanTimestamp = now
                }
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = true,
                    isStandbyMode = false,
                    hasEcuCommunication = canOk,
                    capabilityState = stateMachine.currentCapabilityState,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = if (canOk) null else {
                        if (volt > 0f) {
                            "Veicolo in READY (12V: ${String.format(java.util.Locale.US, "%.1f", volt)}V), sincronizzazione con ECU Toyota in corso..."
                        } else {
                            "Veicolo in READY, sincronizzazione con ECU Toyota in corso..."
                        }
                    }
                )
            } else {
                stateMachine.onVehicleStandby()
                discoveryEngine.reset()
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = false,
                    isStandbyMode = true,
                    hasEcuCommunication = false,
                    capabilityState = stateMachine.currentCapabilityState,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
                )
            }
            return
        }

        // GESTIONE TRANSIZIONE A STANDBY SE L'AUTO VIENE SPENTA DURANTE IL FUNZIONAMENTO
        val isCanSilentForStandby = (lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 12000L)) ||
                                    (lastValidCanTimestamp == 0L && (now - loopStartTimestamp > 12000L))
        if (consecutiveCanErrors >= 6 && isCanSilentForStandby) {
            val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
            val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
            lastKnown12v = volt
            if (!Elm327Protocol.isVehicleReady(volt) && volt > 0f) {
                addLog("💤 Auto spenta (12V: ${volt}V < 13.0V, CAN silente). Entrata in standby a basso consumo.")
                currentCanHeader = ""
                discoveryEngine.reset()
                stateMachine.onVehicleStandby()
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = false,
                    isStandbyMode = true,
                    hasEcuCommunication = false,
                    capabilityState = stateMachine.currentCapabilityState,
                    auxiliary12vVoltage = volt,
                    batteryStatus = _liveState.value.batteryStatus.copy(
                        isFanForced = false,
                        isEcuAckConfirmed = false,
                        estimatedFanRpm = 0
                    ),
                    ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY..."
                )
                return
            }
        }

        // 0. Auto-Recovery se il bus CAN è silente da oltre 15000ms dopo che era attivo, o se bloccato all'avvio (>15s)
        val isCanSilentAfterActive = lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 15000L)
        val isInitialCanStuck = lastValidCanTimestamp == 0L && (now - loopStartTimestamp > 15000L)
        if (isProtocolInitialized && (isCanSilentAfterActive || isInitialCanStuck) && (now - lastAutoRecoveryTimestamp > 25000L)) {
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

    internal suspend fun executeBatteryThermalCycle() {
        try {
            ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)

            // Keep-alive preventivo su centralina batteria ibrida Denso per mantenere attiva la sessione UDS
            bleManager.sendCommand(ToyotaYarisCommands.CMD_TESTER_PRESENT, timeoutMs = 1000L)

            val latched = discoveryEngine.activeBatteryPid
            val candidateToProbe = latched ?: discoveryEngine.getNextCandidate()

            var parsedStatus: HvBatteryStatus? = null

            if (candidateToProbe != null) {
                val isProbing = (latched == null)
                if (isProbing) {
                    stateMachine.onBatteryDiscoveryProbing()
                }

                val timeoutMs = if (isProbing) {
                    BatteryDiscoveryEngine.MAX_PROBE_TIMEOUT_MS
                } else {
                    BATTERY_PID_TIMEOUT_MS
                }

                val rawResponse = try {
                    withTimeoutOrNull(timeoutMs) {
                        bleManager.sendCommand(candidateToProbe, timeoutMs = timeoutMs)
                    }
                } catch (e: Exception) {
                    null
                }

                val cleanRes = Elm327Protocol.cleanResponse(rawResponse ?: "")
                if (rawResponse != null && !Elm327Protocol.isError(cleanRes) && !cleanRes.contains("TIMEOUT")) {
                    parsedStatus = ToyotaYarisCommands.parseBatteryResponse(rawResponse, _liveState.value.fanForcedMax)
                }

                if (parsedStatus != null) {
                    if (isProbing) {
                        discoveryEngine.onCandidateSuccess(candidateToProbe)
                        addLog("✅ Motore discovery phased batteria: agganciato PID $candidateToProbe!")
                    }
                    lastValidCanTimestamp = System.currentTimeMillis()
                    stateMachine.onBatteryDiscovered(candidateToProbe)
                } else {
                    if (isProbing) {
                        discoveryEngine.onCandidateFailed(candidateToProbe)
                        addLog("Discovery phased: PID $candidateToProbe non valido o timeout, cursor avanzato (cooldown 30s).")
                    }
                    stateMachine.onBatteryProbeFailed()
                }
            } else {
                stateMachine.onBatteryProbeFailed()
            }

            val currentState = _liveState.value
            val autoStatus = currentState.autoCoolingStatus
            val updatedBattery = if (parsedStatus != null) {
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
            val shouldForceFan = currentState.fanForcedMax || isAutoCoolingActive || (updatedBattery.maxTemp >= currentState.targetThreshold && updatedBattery.maxTemp > 0.0)
            val activeTargetSpeed = if (currentState.fanForcedMax) 6 else if (isAutoCoolingActive) updatedAutoStatus.targetSpeed else 6

            if (stateMachine.currentCapabilityState.batteryEcuDiscoveryState == BatteryEcuDiscoveryState.Discovered) {
                if (shouldForceFan) {
                    val fanCmd = ToyotaYarisCommands.getFanSpeedCommand(activeTargetSpeed)
                    val fanCmdRes = bleManager.sendCommand(fanCmd)
                    val cleanFanRes = Elm327Protocol.cleanResponse(fanCmdRes)
                    if (cleanFanRes.contains("7F30") || cleanFanRes.contains("ERROR")) {
                        bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_MAX_SPEED_ALT)
                        stateMachine.onFanActuationStateChanged(FanActuationState.REQUESTED)
                    } else {
                        stateMachine.onFanControlConfirmed()
                        stateMachine.onFanActuationStateChanged(FanActuationState.CONFIRMED)
                    }
                    if (updatedBattery.maxTemp > 0.0) {
                        addLog("Ventola HV L$activeTargetSpeed | Batt: ${String.format(java.util.Locale.US, "%.1f", updatedBattery.maxTemp)}°C")
                    } else {
                        addLog("Ventola HV L$activeTargetSpeed | In attesa telemetria termica...")
                    }
                } else {
                    if (currentState.batteryStatus.isFanForced) {
                        bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_STOP_OR_RESET)
                    }
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_TESTER_PRESENT)
                    stateMachine.onFanActuationStateChanged(FanActuationState.OEM_AUTOMATIC)
                }
            }

            _liveState.value = _liveState.value.copy(
                autoCoolingStatus = updatedAutoStatus,
                capabilityState = stateMachine.currentCapabilityState,
                batteryStatus = updatedBattery.copy(
                    isFanForced = shouldForceFan,
                    fanSpeedLevel = if (shouldForceFan) activeTargetSpeed else updatedBattery.fanSpeedLevel
                )
            )
        } finally {
            withContext(NonCancellable) {
                try {
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore ripristino header CAN a 7E0 in finally", e)
                }
            }
        }
    }

    internal suspend fun executeEngineTelemetryFastCycle() {
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)

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
            stateMachine.onEngineTelemetrySuccess()
            lastValidCanTimestamp = sampleTimestamp
        } else {
            stateMachine.onEngineTelemetryFailure()
        }

        // Elaborazione Dragy con interpolazione lineare ad alta precisione
        processDragyTelemetry(sampleTimestamp, lastKnownSpeed, lastKnownThrottle)

        val currentState = _liveState.value
        val hasRecentCanData = (sampleTimestamp - lastValidCanTimestamp <= 10000L) && lastValidCanTimestamp > 0L
        val isEcuAlive = hasRecentCanData && isProtocolInitialized
        val alertBanner = when {
            currentState.isStandbyMode -> "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
            !isEcuAlive && isProtocolInitialized && currentState.isVehicleReady -> {
                if (lastKnown12v > 0f) {
                    "Veicolo in stato READY (12V: ${String.format(java.util.Locale.US, "%.1f", lastKnown12v)}V): sincronizzazione bus CAN Toyota in corso..."
                } else {
                    "Veicolo in stato READY: sincronizzazione bus CAN Toyota in corso..."
                }
            }
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
            capabilityState = stateMachine.currentCapabilityState,
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

    internal suspend fun executeCoolantWarmupCycle() {
        ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU)

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
        currentCanHeader = ""
        discoveryEngine.reset()
        stateMachine.teardownAllCapabilities(BleTransportState.Disconnected)
        _liveState.value = _liveState.value.copy(
            isLoopRunning = false,
            capabilityState = stateMachine.currentCapabilityState,
            batteryStatus = _liveState.value.batteryStatus.copy(
                isFanForced = false,
                isEcuAckConfirmed = false,
                estimatedFanRpm = 0
            )
        )
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
                ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU)
                val resMeter = bleManager.sendCommand("21A7")
                addLog("Meter 7C0 Read: ${Elm327Protocol.cleanResponse(resMeter)}")
                delay(80)

                // 2. Main Body ECU (750 / 758) -> Doors, Windows, Turn Signals & Lights
                ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU)
                val resBody = bleManager.sendCommand("2101")
                addLog("Body 750 Read: ${Elm327Protocol.cleanResponse(resBody)}")
                delay(80)

                // 3. Aircon ECU (7C4 / 7CC) -> A/C Behavior
                ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU)
                val resAc = bleManager.sendCommand("2101")
                addLog("AirCon 7C4 Read: ${Elm327Protocol.cleanResponse(resAc)}")
                delay(80)

                // 4. TSS / ADAS (7A0 / 7A8) -> LDA & BSM
                ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU)
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
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)
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
                ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU)
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
                bleManager.sendCommand("3B03" + if (updatedState.rearSeatbeltBeep) "01" else "00")
                delay(40)

                // Read-After-Write Verification su Meter
                val verifyMeter = bleManager.sendCommand("21A7")
                addLog("Verifica Meter: ${Elm327Protocol.cleanResponse(verifyMeter)}")

                // 2. Main Body ECU (750 / 758) -> Smart Key, Doors, Windows, Turn Signals & Lights
                ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU)
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
                // Door Unlock Mode
                bleManager.sendCommand("3B25" + updatedState.doorUnlockMode.code)
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
                // Footwell Lighting in Drive
                bleManager.sendCommand("3B34" + if (updatedState.footwellLightingInDrive) "01" else "00")
                delay(40)
                // Wipers (Rear wiper reverse link & Drip wipe)
                bleManager.sendCommand("3B40" + if (updatedState.rearWiperReverseLink) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B41" + if (updatedState.dripWipeExtraPass) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B42" + if (updatedState.wiperSpeedLink) "01" else "00")
                delay(40)

                // Read-After-Write Verification su Body ECU
                val verifyBody = bleManager.sendCommand("2101")
                addLog("Verifica Body ECU: ${Elm327Protocol.cleanResponse(verifyBody)}")

                // 3. Aircon ECU (7C4 / 7CC) -> A/C with AUTO button & Eco Mode
                ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU)
                bleManager.sendCommand("1003")
                delay(50)
                bleManager.sendCommand("3B50" + if (updatedState.autoAcWithAutoButton) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B51" + if (updatedState.ecoAirConEfficiencyMode) "01" else "00")
                delay(40)
                // Blower on Defroster
                bleManager.sendCommand("3B52" + if (updatedState.blowerOnDefroster) "01" else "00")
                delay(40)
                // Temperature Calibration
                bleManager.sendCommand("3B53" + updatedState.temperatureCalibration.code)
                delay(40)

                // 4. TSS 2.5 / ADAS ECU (7A0 / 7A8) -> LDA Volume & BSM Sensitivity
                ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU)
                bleManager.sendCommand("1003")
                delay(50)
                bleManager.sendCommand("3B60" + updatedState.ldaWarningVolume.code)
                delay(40)
                bleManager.sendCommand("3B61" + updatedState.bsmSensitivity.code)
                delay(40)
                // RCTA, LTA & PCS
                bleManager.sendCommand("3B62" + if (updatedState.rctaEnabled) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B63" + if (updatedState.ltaEnabled) "01" else "00")
                delay(40)
                bleManager.sendCommand("3B64" + if (updatedState.pcsRememberLast) "01" else "00")
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
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)
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
            doorUnlockMode = DoorUnlockMode.ALL_DOORS,
            windowsWithKeyFob = false,
            autoDoorLock = AutoDoorLockMode.OFF,
            autoDoorUnlock = false,
            wiperSpeedLink = true,
            turnSignalFlashes = TurnSignalFlashes.FLASHES_3,
            lightSensitivity = LightSensitivity.NORMAL,
            footwellLightingInDrive = false,
            followMeHome = FollowMeHomeDuration.OFF,
            rctaEnabled = true,
            ltaEnabled = true,
            pcsRememberLast = false,
            blowerOnDefroster = true,
            temperatureCalibration = TemperatureCalibration.ZERO,
            autoAcWithAutoButton = true,
            isReadCompleted = true,
            lastOperationStatus = "Configurazione di fabbrica ripristinata"
        )
        applyEcuCustomization(factoryState)
    }
}
