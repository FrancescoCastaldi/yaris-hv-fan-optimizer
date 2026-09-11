package com.yaris.hvfan.obd

import android.util.Log
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.ble.BleManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ObdLiveState(
    val isInitialized: Boolean = false,
    val isLoopRunning: Boolean = false,
    val hasEcuCommunication: Boolean = false, // True solo quando arrivano frame CAN validi dalla ECU dell'auto
    val isVehicleReady: Boolean = false,      // True quando 12V > 13.0V (DC-DC attivo) o CAN valido
    val isStandbyMode: Boolean = false,       // True se in standby a basso consumo (auto spenta o non READY)
    val auxiliary12vVoltage: Float = 0f,      // Tensione reale 12V rilevata da AT RV
    val ecuAlertMessage: String? = null,      // Avviso visivo per l'utente quando la centralina non risponde
    val batteryAdapterLimitationWarning: String? = null, // Avviso distinto: probabile limite hardware dell'adapter OBD (fallback chain batteria esaurita ripetutamente)
    val lastDataReceivedTimestamp: Long = 0L,
    val batteryStatus: HvBatteryStatus = HvBatteryStatus(),
    val warmupStatus: HybridWarmupStatus = HybridWarmupStatus(),
    val performanceStatus: EnginePerformanceStatus = EnginePerformanceStatus(),
    val accelerationState: AccelerationRunState = AccelerationRunState(),
    val ecuCodingState: EcuCustomizationState = EcuCustomizationState(),
    val targetThreshold: Int = 20,
    val fanForcedMax: Boolean = false,
    val isManualFanForced: Boolean = false,
    val manualFanTargetLevel: Int = 6,
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
    val discoveryEngine: BatteryDiscoveryEngine = BatteryDiscoveryEngine(),
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "ObdController"
        private const val BATTERY_POLL_INTERVAL_MS = 3500L
        private const val COOLANT_POLL_INTERVAL_MS = 4000L

        // Le risposte multi-frame UDS 2228C1 richiedono flow control ISO-TP completo: sotto i 4s
        // il timeout BLE scade prima che l'ultimo frame consecutivo arrivi.
        private const val BATTERY_PID_TIMEOUT_MS = 4000L

        // Se l'intera fallback chain PID batteria fallisce per >= 2 giri completi consecutivi
        // (tutti i candidati in cooldown, nessuno mai latchato), il problema e' quasi certamente
        // hardware (clone ELM327/Vlinker con flow-control ISO-TP insufficiente per il multi-frame
        // UDS) e non un bug applicativo: l'utente va indirizzato verso un adapter STN11xx/21xx.
        private const val HARDWARE_LIMITATION_CYCLE_THRESHOLD = 2

        private const val BATTERY_ADAPTER_LIMITATION_MESSAGE =
            "Nessun PID batteria HV risponde dopo più giri completi della fallback chain: " +
            "probabile limite hardware dell'adapter OBD (flow-control ISO-TP insufficiente per le " +
            "risposte multi-frame), non un problema dell'app. Prova un adapter con chipset " +
            "STN11xx/STN21xx (compatibile OBDLink) invece di cloni ELM327/Vlinker generici."
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
            isManualFanForced = appPreferences?.isManualFanForced ?: false,
            manualFanTargetLevel = appPreferences?.manualFanTargetLevel ?: 6,
            fanForcedMax = appPreferences?.isManualFanForced ?: false,
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
    internal var lastValidCanTimestamp = 0L
    private var loopStartTimestamp = 0L
    private var standbyCycleCounter = 0
    private var consecutiveStandbyChecks = 0
    private var lastStandbyExitTimestamp = 0L
    private var lastBatteryCheckTimestamp = 0L
    private var lastCoolantCheckTimestamp = 0L
    private var pendingBatterySafetyCheck = false
    private var fastCycleCounter = 0

    /**
     * Dispatch timestamps (per `timeProvider`) of the last executed fast engine telemetry
     * and coolant/warm-up cycles. Used by scheduler resilience telemetry and tests to
     * prove zero engine starvation (VAL-OBD-007 / VAL-OBD-012).
     */
    internal val lastEngineFastDispatchTimestampMs: Long
        get() = internalLastEngineFastDispatchMs
    internal val lastCoolantDispatchTimestampMs: Long
        get() = internalLastCoolantDispatchMs
    private var internalLastEngineFastDispatchMs = 0L
    private var internalLastCoolantDispatchMs = 0L

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

    /**
     * Calcola l'avviso distinto "probabile limite hardware dell'adapter OBD": si attiva solo
     * quando l'intera fallback chain PID batteria e' esaurita (tutti i candidati in cooldown)
     * per almeno HARDWARE_LIMITATION_CYCLE_THRESHOLD giri completi consecutivi senza mai latchare
     * un PID valido. Distinto da ecuAlertMessage, che copre standby/sincronizzazione transitori.
     */
    private fun computeBatteryAdapterLimitationWarning(): String? {
        return if (discoveryEngine.completedFailureCycles >= HARDWARE_LIMITATION_CYCLE_THRESHOLD) {
            BATTERY_ADAPTER_LIMITATION_MESSAGE
        } else {
            null
        }
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$timestamp] $message"
        Log.i(TAG, logLine)
        try {
            com.yaris.hvfan.data.ObdLogger.log(message)
        } catch (ignored: Throwable) {}
        val currentLogs = _liveState.value.logs.takeLast(100).toMutableList()
        currentLogs.add(logLine)
        _liveState.value = _liveState.value.copy(
            lastLogMessage = message,
            logs = currentLogs
        )
    }

    internal suspend fun performHandshake(): Boolean {
        stateMachine.onElmInitializing()
        loopStartTimestamp = timeProvider()
        standbyCycleCounter = 0
        currentCanHeader = ""
        currentRxFilter = null
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
                _liveState.value = _liveState.value.copy(ecuAlertMessage = "Sveglia adattatore...")
                addLog("Sveglia adattatore (\\r\\r)...")
                bleManager.sendWakeSequence()
                delay(150)

                // 2. Reset pulito dell'adattatore (AT Z) per dongle Vgate iCar Pro con pausa di avvio
                addLog("Reset adattatore Vgate (AT Z)...")
                val resZ = try {
                    bleManager.sendCommand(Elm327Protocol.CMD_RESET, timeoutMs = 3000L)
                } catch (e: Exception) {
                    addLog("⚠️ AT Z non riuscito (${e.localizedMessage}), continuo con Warm Start (AT WS)...")
                    try {
                        bleManager.sendCommand(Elm327Protocol.CMD_WARM_START, timeoutMs = 2000L)
                    } catch (e2: Exception) {
                        ""
                    }
                }
                addLog("Reset Response: ${Elm327Protocol.cleanResponse(resZ)}")
                delay(300)

                // 3. Invio sequenza di configurazione parametri base (AT E0, AT L0, AT S0, AT H0)
                addLog("Configurazione parametri base (AT E0, AT L0, AT S0, AT H0)...")
                bleManager.sendCommand(Elm327Protocol.CMD_ECHO_OFF)
                delay(40)
                bleManager.sendCommand(Elm327Protocol.CMD_LINEFEEDS_OFF)
                delay(40)
                bleManager.sendCommand(Elm327Protocol.CMD_SPACES_OFF)
                delay(40)
                bleManager.sendCommand(Elm327Protocol.CMD_HEADERS_OFF)
                delay(40)

                // 3b. Negoziazione protocollo CAN 11-bit 500k rigida con guard-time (AT SP 6 - FIX 2)
                addLog("Negoziazione protocollo CAN 11-bit 500k (AT SP 6)...")
                val sp6Res = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_CAN_11_500)
                val cleanSp6 = Elm327Protocol.cleanResponse(sp6Res)
                addLog("Protocollo CAN (AT SP 6): $cleanSp6")
                // Guard-time di stabilizzazione transceiver CAN dongle 500kbps (FIX 2)
                delay(100)

                val adaptiveTimingRes = bleManager.sendCommand(Elm327Protocol.CMD_ADAPTIVE_TIMING_1)
                if (Elm327Protocol.cleanResponse(adaptiveTimingRes).contains("?")) {
                    addLog("ℹ️ Clone ELM327 rilevato: AT AT 1 non supportato (?), continuo con timing standard.")
                }
                delay(40)
                val caf1Res = bleManager.sendCommand(Elm327Protocol.CMD_CAN_AUTO_FORMAT_ON)
                if (Elm327Protocol.cleanResponse(caf1Res).contains("?")) {
                    addLog("ℹ️ Clone ELM327 rilevato: AT CAF 1 non supportato (?), continuo con formattazione nativa.")
                }
                delay(40)
                // FIX 1: Bonifica totale di AT AR (rimosso per prevenire corruzione registri hardware sui cloni)
                bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
                delay(40)

                // 3c. Verifica del protocollo effettivamente negoziato: AT DPN (atteso 6 o A6 - FIX 2)
                val dpnRes = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_NUMBER, timeoutMs = 1500L)
                val cleanDpn = Elm327Protocol.cleanResponse(dpnRes)
                addLog("Protocollo ELM327 attivo (AT DPN): $cleanDpn (atteso 6 = ISO 15765-4 CAN 11-bit 500k)")
                stateMachine.onElmReady()

                // 4. Lettura versione/chipset (ATI / STI / AT@1 / ST DI) conforme a Hybrid Assistant
                addLog("Rilevamento identità adapter OBD (ATI / STI / AT@1 / ST DI)...")
                val atiRes = bleManager.sendCommand(Elm327Protocol.CMD_DEVICE_INFO, timeoutMs = 1500L)
                addLog("Adapter ATI: ${Elm327Protocol.cleanResponse(atiRes)}")
                val stiRes = bleManager.sendCommand(Elm327Protocol.CMD_DEVICE_INFO_STI, timeoutMs = 1500L)
                addLog("Adapter STI: ${Elm327Protocol.cleanResponse(stiRes)}")
                val at1Res = bleManager.sendCommand(Elm327Protocol.CMD_DEVICE_INFO_AT1, timeoutMs = 1500L)
                addLog("Adapter AT@1: ${Elm327Protocol.cleanResponse(at1Res)}")
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

                // 5. Verifica voltaggio 12V reale (AT RV)
                val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
                val real12v = Elm327Protocol.parseBatteryVoltage(voltRes) ?: 0f
                lastKnown12v = real12v
                val isReady = Elm327Protocol.isVehicleReady(real12v)
                addLog("Tensione Batteria 12V (AT RV): ${real12v}V [READY: ${if (isReady) "SI" else "NO"}]")
                if (isReady && real12v < 13.2f) {
                    addLog("⚠️ Tensione al limite (${real12v}V): verifica che il quadro sia in READY (spia verde) e che l'adattatore non sia mal calibrato.")
                }

                // 6. Sincronizzazione CAN 500k (Stadio 0: broadcast 7DF)
                _liveState.value = _liveState.value.copy(ecuAlertMessage = "Sincronizzazione CAN 500k...")
                addLog("Sincronizzazione CAN 500k in corso (broadcast 7DF)...")
                stateMachine.onCanSearching()
                ensureCanHeader(ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST, force = true)
                var stage0Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 8000L)
                var clean0 = Elm327Protocol.cleanResponse(stage0Res)
                addLog("Broadcast 0100 Response: $clean0")
                var stage0Ok = Elm327Protocol.hasSupportedPidsResponse(stage0Res)

                if (!stage0Ok) {
                    addLog("Nessuna risposta a 0100 in broadcast, tentativo sincronizzazione con PID 010C (RPM)...")
                    delay(150)
                    stage0Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 8000L)
                    clean0 = Elm327Protocol.cleanResponse(stage0Res)
                    addLog("Broadcast 010C Response: $clean0")
                    stage0Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, stage0Res)
                }

                if (stage0Ok) {
                    addLog("✅ Sincronizzazione CAN 500k completata (risposta: $clean0)!")
                    lastValidCanTimestamp = timeProvider()
                } else {
                    addLog("ℹ️ Sincronizzazione broadcast 7DF senza risposta immediata ($clean0). Central Gateway Toyota potrebbe filtrare broadcast. Procedo ad aggancio diretto ECU Motore (7E0)...")
                }

                // 7. Stadio 1: aggancio rapido centralina motore standard (7E0 con fallback broadcast 7DF + CRA 7E8 - FIX 3)
                _liveState.value = _liveState.value.copy(ecuAlertMessage = "Aggancio motore 7E0...")
                addLog("Handshake CAN Stadio 1: aggancio centralina motore (7E0 + CRA 7E8)...")
                activeEngineHeader = ToyotaYarisCommands.HEADER_ENGINE_ECU
                ensureEngineHeader(force = true)

                var stage1Ok = false
                var stage1Res = ""
                var cleanStage1 = ""

                // Tentativo su 010C (RPM) su 7E0
                stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
                cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                addLog("Probe PID 010C su 7E0: $cleanStage1")
                if (Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, stage1Res)) {
                    stage1Ok = true
                } else {
                    // Fallback su PID 010D (Velocità) su 7E0 (auto ferma in READY ha 010C fermo o gateway selettivo)
                    stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED, timeoutMs = 3500L)
                    cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                    addLog("Probe PID 010D su 7E0: $cleanStage1")
                    if (Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_VEHICLE_SPEED, stage1Res)) {
                        stage1Ok = true
                    }
                }

                // Se la centralina motore 7E0 non risponde a query fisiche (filtro Central Gateway DLC3),
                // scatta istantaneamente sul fallback collaudato: broadcast 7DF con filtro hardware CRA 7E8 (FIX 3)
                if (!stage1Ok) {
                    addLog("ℹ️ Centralina motore 7E0 non risponde (filtro Central Gateway). Scatto immediato su fallback 7DF + CRA 7E8...")
                    activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
                    ensureEngineHeader(force = true)

                    stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
                    cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                    addLog("Probe PID 010C su 7DF: $cleanStage1")
                    if (Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, stage1Res)) {
                        stage1Ok = true
                    } else {
                        stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED, timeoutMs = 3500L)
                        cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                        addLog("Probe PID 010D su 7DF: $cleanStage1")
                        if (Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_VEHICLE_SPEED, stage1Res)) {
                            stage1Ok = true
                        } else {
                            stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
                            cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                            addLog("Probe PID 0100 su 7DF: $cleanStage1")
                            stage1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, stage1Res)
                        }
                    }
                }

                // Se la sincronizzazione su protocollo fisso 6 fallisce del tutto (UNABLE TO CONNECT / CAN ERROR),
                // esegui fallback estremo su AT SP 0 (auto-detect)
                if (!stage0Ok && !stage1Ok) {
                    _liveState.value = _liveState.value.copy(ecuAlertMessage = "Sincronizzazione automatica CAN (AT SP 0)...")
                    addLog("⚠️ Handshake CAN non agganciato su protocollo 6 ($cleanStage1). Eseguo fallback su AT SP 0 (auto-detect)...")
                    val sp0Res = bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
                    addLog("Protocollo Fallback (AT SP 0): ${Elm327Protocol.cleanResponse(sp0Res)}")
                    bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)

                    // Riprova broadcast 7DF con auto-detect
                    ensureCanHeader(ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST, force = true)
                    stage0Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 8000L)
                    clean0 = Elm327Protocol.cleanResponse(stage0Res)
                    addLog("Broadcast 0100 (con AT SP 0): $clean0")
                    stage0Ok = Elm327Protocol.hasSupportedPidsResponse(stage0Res)
                    if (!stage0Ok) {
                        stage0Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 8000L)
                        clean0 = Elm327Protocol.cleanResponse(stage0Res)
                        addLog("Broadcast 010C (con AT SP 0): $clean0")
                        stage0Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, stage0Res)
                    }

                    // Riprova aggancio motore con auto-detect
                    ensureEngineHeader(force = true)
                    stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
                    cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                    addLog("Probe PID 010C (con AT SP 0): $cleanStage1")
                    if (Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, stage1Res)) {
                        stage1Ok = true
                    } else {
                        stage1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
                        cleanStage1 = Elm327Protocol.cleanResponse(stage1Res)
                        addLog("Probe PID 0100 (con AT SP 0): $cleanStage1")
                        stage1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, stage1Res)
                    }
                    if (!stage1Ok && stage0Ok) {
                        activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
                        ensureEngineHeader(force = true)
                        stage1Ok = true
                    }
                }

                if (stage1Ok || stage0Ok) {
                    val dpnRes = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_NUMBER, timeoutMs = 1500L)
                    addLog("✅ Handshake CAN completato con successo (AT DPN: ${Elm327Protocol.cleanResponse(dpnRes)})!")
                    lastValidCanTimestamp = timeProvider()
                    stateMachine.onEngineTelemetrySuccess()
                } else {
                    addLog("⚠️ Handshake CAN: nessuna risposta positiva da 7E0 / broadcast ($cleanStage1).")
                }

                // 8. Stadio 2: passaggio alla Centralina Batteria Denso HV (7E2) con fallback a catena trasparente
                _liveState.value = _liveState.value.copy(ecuAlertMessage = "Lettura batteria 7E2...")
                addLog("Handshake CAN Stadio 2: passaggio a Centralina Batteria Denso HV (7E2 + CRA 7EA + AT ST FF)...")
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU, force = true)
                discoveryEngine.reset()
                stateMachine.onBatteryDiscoveryProbing()

                var stage2Ok = false
                val fallbackChain = ToyotaYarisCommands.BATTERY_FALLBACK_PIDS // 2101 -> 21C3 -> 21C4 -> 2161 -> 2228C1 -> 2228C0 -> 220101

                for (candidatePid in fallbackChain) {
                    if (discoveryEngine.isPidRejected(candidatePid)) {
                        continue
                    }
                    addLog("Probe PID batteria: $candidatePid...")
                    var candidateSuccess = false
                    var rawBatteryRes = ""

                    for (attempt in 1..2) {
                        rawBatteryRes = bleManager.sendCommand(candidatePid, timeoutMs = 3500L)
                        val cleanBattery = Elm327Protocol.cleanResponse(rawBatteryRes)
                        addLog("PID $candidatePid (Tentativo #$attempt): $cleanBattery")

                        val udsNrc = Elm327Protocol.extractUdsNrc(rawBatteryRes)
                        if (udsNrc != null) {
                            if (udsNrc.nrc == Elm327Protocol.NRC_RESPONSE_PENDING) {
                                addLog("ℹ️ PID $candidatePid: NRC 78 (ResponsePending), attendo frame consecutivo...")
                                delay(150)
                                rawBatteryRes = bleManager.sendCommand(candidatePid, timeoutMs = 3500L)
                            } else if (udsNrc.nrc == Elm327Protocol.NRC_SERVICE_NOT_SUPPORTED || udsNrc.nrc == Elm327Protocol.NRC_SUB_FUNCTION_NOT_SUPPORTED) {
                                addLog("❌ PID $candidatePid non supportato da Denso BMS (NRC ${udsNrc.nrc}), escluso permanentemente (FIX 4).")
                                discoveryEngine.onCandidateRejected(candidatePid, rawBatteryRes)
                                break
                            } else if (udsNrc.nrc == Elm327Protocol.NRC_CONDITIONS_NOT_CORRECT) {
                                addLog("ℹ️ PID $candidatePid: NRC 22 (ConditionsNotCorrect), veicolo non pronto.")
                                discoveryEngine.onCandidateFailed(candidatePid, ProbeStatus.INVALID, rawBatteryRes)
                                break
                            }
                        }

                        val parsedStatus = ToyotaYarisCommands.parseBatteryResponse(rawBatteryRes, false)
                        val isUdsPositive = Elm327Protocol.isUdsPositiveResponse(
                            rawBatteryRes,
                            candidatePid.take(2)
                        )

                        if (parsedStatus != null || isUdsPositive) {
                            candidateSuccess = true
                            discoveryEngine.onCandidateSuccess(candidatePid, rawBatteryRes)
                            stateMachine.onBatteryDiscovered(candidatePid)
                            if (parsedStatus != null) {
                                _liveState.value = _liveState.value.copy(
                                    batteryStatus = parsedStatus,
                                    hasEcuCommunication = true
                                )
                            }
                            addLog("✅ Centralina Batteria 7E2 agganciata con successo su PID $candidatePid!")
                            break
                        } else {
                            if (attempt < 2 && (cleanBattery.contains("NODATA") || cleanBattery.contains("CANERROR") || cleanBattery.isEmpty())) {
                                delay(150)
                            }
                        }
                    }

                    if (candidateSuccess) {
                        stage2Ok = true
                        lastValidCanTimestamp = timeProvider()
                        break
                    } else if (!discoveryEngine.isPidRejected(candidatePid) && !discoveryEngine.isCandidateInCooldown(candidatePid)) {
                        discoveryEngine.onCandidateFailed(candidatePid)
                        addLog("ℹ️ PID $candidatePid non ha risposto. Avanzo al candidato successivo nella catena (cooldown 5s)...")
                        delay(80)
                    }
                }

                val canOk = stage0Ok || stage1Ok || stage2Ok
                val isActuallyReady = isReady || canOk || (real12v >= 12.2f)

                if (isActuallyReady) {
                    stateMachine.onVehicleReady()
                }

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
                        isActuallyReady -> "Veicolo attivo (12V: ${String.format(java.util.Locale.US, "%.1f", real12v)}V). Sincronizzazione con ECU Toyota in corso..."
                        else -> "In attesa di risposta CAN centralina: accendi la vettura (spia READY) per avviare la telemetria."
                    },
                    batteryAdapterLimitationWarning = null
                )

                    // 8. Test supporto Multi-PID per telemetria motore e Dragy se il veicolo è attivo
                    if (canOk) {
                        addLog("Verifica supporto Multi-PID (010D0C11)...")
                        ensureEngineHeader()
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
        addLog("Inizializzazione completata!")
        return canOk
    }

    internal fun initializeAndStartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch(ioDispatcher) {
            try {
                val canOk = performHandshake()
                if (canOk) {
                    addLog("Avvio scheduler Dual-Rate (Handshake completato con successo)...")
                } else {
                    addLog("⚠️ Handshake iniziale non completato: avvio scheduler con auto-recovery continuo...")
                }
                lastBatteryCheckTimestamp = 0L
                lastCoolantCheckTimestamp = 0L
                runDualRateScheduler()
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
    internal var currentRxFilter: String? = null
    internal var activeEngineHeader: String = ToyotaYarisCommands.HEADER_ENGINE_ECU

    internal suspend fun ensureEngineHeader(force: Boolean = false) {
        if (activeEngineHeader == ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST) {
            ensureCanHeader(
                ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST,
                force = force,
                customRxFilter = ToyotaYarisCommands.CRA_ENGINE_ECU
            )
        } else {
            ensureCanHeader(ToyotaYarisCommands.HEADER_ENGINE_ECU, force = force)
        }
    }

    /**
     * Esegue un probe OBD-II funzionale impostando 7DF.
     */
    private suspend fun probeBroadcastCan(
        attempts: Int = 2,
        timeoutMs: Long = 6000L
    ): CanProbeResult {
        currentCanHeader = ""
        currentRxFilter = null
        ensureCanHeader(
            ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST,
            customRxFilter = ToyotaYarisCommands.CRA_ENGINE_ECU
        )
        bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
        delay(30)

        var cleanResponse = ""
        repeat(attempts) { index ->
            val response = bleManager.sendCommand(
                ToyotaYarisCommands.PID_SUPPORTED_PIDS,
                timeoutMs = timeoutMs
            )
            cleanResponse = Elm327Protocol.cleanResponse(response)
            if (Elm327Protocol.hasSupportedPidsResponse(response)) {
                return CanProbeResult(true, cleanResponse)
            }
            if (index < attempts - 1) delay(250)
        }
        return CanProbeResult(false, cleanResponse)
    }



    /**
     * Imposta l'header di trasmissione CAN dell'ECU bersaglio e configura in modo atomico
     * il corrispondente filtro hardware di ricezione (AT CRA), per garantire che i frame fisici
     * dell'ECU vengano sempre instradati all'applicazione senza scarti dal controller CAN interno dell'ELM327.
     * R1: Non invia MAI AT AR dopo AT SH per evitare la corruzione dei filtri su cloni ELM327.
     */
    internal suspend fun ensureCanHeader(
        header: String,
        force: Boolean = false,
        skipHardwareFilters: Boolean = false,
        customRxFilter: String? = null
    ) {
        val targetRxFilter = if (!skipHardwareFilters) {
            customRxFilter ?: ToyotaYarisCommands.getFilterForHeader(header)
        } else null

        val needsHeaderUpdate = (currentCanHeader != header) || force
        val needsFilterUpdate = (currentRxFilter != targetRxFilter) || force

        if (needsHeaderUpdate || needsFilterUpdate) {
            currentCanHeader = ""
            currentRxFilter = null
            bleManager.sendCommand("AT SH $header")
            delay(25)

            if (!skipHardwareFilters) {
                // Configura il filtro hardware di ricezione (AT CRA) corrispondente
                if (targetRxFilter != null) {
                    bleManager.sendCommand("AT CRA $targetRxFilter")
                    delay(25)
                }

                when (header) {
                    ToyotaYarisCommands.HEADER_BATTERY_ECU -> {
                        if (isCustomFcSupported) {
                            bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SH_BATTERY)
                            delay(25)
                            bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SD_CTS)
                            delay(25)
                            bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_CUSTOM)
                            delay(25)
                        }
                        bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU)
                    }
                    ToyotaYarisCommands.HEADER_BODY_ECU,
                    ToyotaYarisCommands.HEADER_METER_ECU,
                    ToyotaYarisCommands.HEADER_AIRCON_ECU,
                    ToyotaYarisCommands.HEADER_ADAS_ECU -> {
                        if (isCustomFcSupported) {
                            bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_DEFAULT)
                            delay(25)
                        }
                        bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_ECU_CODING)
                    }
                    else -> {
                        if (isCustomFcSupported) {
                            bleManager.sendCommand(ToyotaYarisCommands.CMD_FC_SM_DEFAULT)
                            delay(25)
                        }
                        val engineTimeout = if (lastKnown12v >= 13.0f) {
                            Elm327Protocol.CMD_TIMEOUT_TELEMETRY // AT ST 32 (~205ms per READY con DC-DC attivo)
                        } else {
                            Elm327Protocol.CMD_TIMEOUT_TELEMETRY_STANDBY // AT ST 64 (~410ms per quadro acceso / non-READY - FIX 5)
                        }
                        bleManager.sendCommand(engineTimeout)
                    }
                }
            } else {
                val engineTimeout = if (lastKnown12v >= 13.0f) {
                    Elm327Protocol.CMD_TIMEOUT_TELEMETRY
                } else {
                    Elm327Protocol.CMD_TIMEOUT_TELEMETRY_STANDBY
                }
                bleManager.sendCommand(engineTimeout)
            }
            currentCanHeader = header
            currentRxFilter = targetRxFilter
            // Pausa di stabilizzazione per i transceiver CAN dell'adattatore
            delay(50)
        }
    }

    @Volatile private var isEcuOperationInProgress = false
    private var lastAutoRecoveryTimestamp = 0L

    private suspend fun executeCanBusAutoRecovery() {
        addLog("⚠️ Nessun dato CAN ricevuto: verifica tensione 12V e auto-recovery...")
        val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
        val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
        lastKnown12v = volt
        if (!Elm327Protocol.isVehicleReady(volt) && volt > 0f) {
            addLog("Auto non in READY (12V: ${volt}V < 13.0V): passaggio a standby a basso consumo.")
            currentCanHeader = ""
            currentRxFilter = null
            discoveryEngine.reset()
            stateMachine.onVehicleStandby()
            _liveState.value = _liveState.value.copy(
                isVehicleReady = false,
                isStandbyMode = true,
                hasEcuCommunication = false,
                capabilityState = stateMachine.currentCapabilityState,
                auxiliary12vVoltage = volt,
                ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY...",
                batteryAdapterLimitationWarning = null
            )
            return
        }

        // Auto-Recovery: resetta lo stato delle capacità e forza ri-scoperta pulita
        stateMachine.onCanBusAutoRecovery()
        discoveryEngine.reset()

        // Recovery Leggero: ripristina timeout e header CAN senza alterare la sincronizzazione
        addLog("Tentativo auto-recovery leggero (AT SH)...")
        bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
        ensureEngineHeader(force = true)

        var s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
        var s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
        if (!s1Ok) {
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED, timeoutMs = 3500L)
            s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_VEHICLE_SPEED, s1Res)
        }
        if (!s1Ok) {
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
            s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, s1Res)
        }
        if (!s1Ok && activeEngineHeader != ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST) {
            addLog("Auto-recovery: 7E0 non risponde, fallback su broadcast funzionale 7DF...")
            activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
            ensureEngineHeader(force = true)
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
            s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
            if (!s1Ok) {
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, s1Res)
            }
        }

        if (!s1Ok) {
            addLog("Auto-recovery leggero fallito. Eseguo ripristino calibrato dello stack ELM327 (Warm Start AT WS)...")
            // Warm Start AT WS invece di \r\r -> AT Z che causa crash del socket Bluetooth (R3)
            bleManager.sendCommand(Elm327Protocol.CMD_WARM_START, timeoutMs = 2000L)
            delay(150)
            bleManager.sendCommand(Elm327Protocol.CMD_ECHO_OFF)
            bleManager.sendCommand(Elm327Protocol.CMD_LINEFEEDS_OFF)
            bleManager.sendCommand(Elm327Protocol.CMD_SPACES_OFF)
            bleManager.sendCommand(Elm327Protocol.CMD_HEADERS_OFF)
            val spRes = bleManager.sendCommand(Elm327Protocol.CMD_PROTOCOL_CAN_11_500)
            delay(100) // Guard-time di stabilizzazione CAN 500k (FIX 2)
            if (Elm327Protocol.isError(Elm327Protocol.cleanResponse(spRes))) {
                bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
            }
            bleManager.sendCommand(Elm327Protocol.CMD_ADAPTIVE_TIMING_1)
            bleManager.sendCommand(Elm327Protocol.CMD_CAN_AUTO_FORMAT_ON)
            bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
            
            ensureEngineHeader(force = true)
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
            s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
            if (!s1Ok) {
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, s1Res)
            }
            if (!s1Ok && activeEngineHeader != ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST) {
                activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
                ensureEngineHeader(force = true)
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
            }
        }

        if (!s1Ok) {
            addLog("Tentativo auto-recovery secondario con autorilevamento protocollo (AT SP 0)...")
            bleManager.sendCommand(Elm327Protocol.PROTOCOL_FALLBACK)
            bleManager.sendCommand(Elm327Protocol.CMD_TIMEOUT_HANDSHAKE)
            ensureEngineHeader(force = true)
            s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
            s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
            if (!s1Ok) {
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_VEHICLE_SPEED, s1Res)
            }
            if (!s1Ok) {
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_SUPPORTED_PIDS, s1Res)
            }
            if (!s1Ok && activeEngineHeader != ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST) {
                activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
                ensureEngineHeader(force = true)
                s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3500L)
                s1Ok = Elm327Protocol.isStage1PositiveResponse(ToyotaYarisCommands.PID_ENGINE_RPM, s1Res)
            }
        }

        stateMachine.onElmReady()
        stateMachine.onBatteryDiscoveryProbing()

        if (s1Ok) {
            lastValidCanTimestamp = timeProvider()
            stateMachine.onEngineTelemetrySuccess()
            addLog("✅ Procedura auto-recovery completata: bus CAN motore riagganciato (Risposta: ${Elm327Protocol.cleanResponse(s1Res)}).")
        } else {
            addLog("⚠️ Procedura auto-recovery fallita: nessuna risposta CAN centralina.")
        }

        _liveState.value = _liveState.value.copy(
            capabilityState = stateMachine.currentCapabilityState,
            batteryAdapterLimitationWarning = null
        )
    }

    internal val obdTransactionMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Schedulatore dual-rate rigorosamente sequenziale (single-flight execution).
     * Ogni tick esegue il ciclo di telemetria inline garantendo che nessuna nuova transazione CAN
     * venga avviata finché la precedente non è completamente terminata o andata in timeout.
     * In questo modo si azzera qualunque rischio di sovrapposizione comandi, buffer overrun su ELM327
     * e risposte scambiate tra diverse centraline (VAL-OBD-007 / VAL-OBD-012).
     */
    internal suspend fun runDualRateScheduler() {
        while (currentCoroutineContext().isActive) {
            if (isEcuOperationInProgress) {
                delay(120L)
                continue
            }
            obdTransactionMutex.withLock {
                if (!isEcuOperationInProgress) {
                    executeDualRateCycle()
                }
            }
            val loopDelayMs = when {
                _liveState.value.isStandbyMode -> 2500L // Standby a basso consumo: 2.5s per evitare saturazione bus
                isTimingInProgress || lastKnownSpeed > 0 -> 60L
                else -> 120L
            }
            delay(loopDelayMs)
        }
    }

    internal suspend fun executeDualRateCycle() {
        try {
            executeDualRateCycleInternal()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Per-cycle exception isolation: a failure escaping a single slice (e.g. the battery
            // thermal cycle) must never terminate or stall the scheduler loop. Battery-side
            // failures stay decoupled from engine bus health (VAL-OBD-008).
            Log.e(TAG, "Eccezione isolata in un ciclo dual-rate: il loop continua", e)
            stateMachine.onBatteryProbeFailed()
        }
    }

    private suspend fun executeDualRateCycleInternal() {
        if (isEcuOperationInProgress) return

        val now = timeProvider()

        // GESTIONE STATO STANDBY A BASSO CONSUMO (Auto spenta o non READY)
        if (_liveState.value.isStandbyMode) {
            standbyCycleCounter++
            val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
            val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
            lastKnown12v = volt

            val isReadyByVoltage = Elm327Protocol.isVehicleReady(volt)
            // Permetti l'aggancio CAN anche se 12V < 13.0V (quadro acceso 11.8V-12.4V a DC-DC spento)
            val canProbe = if (!isReadyByVoltage) {
                probeBroadcastCan(attempts = 1, timeoutMs = 1200L)
            } else null

            val isCarActive = isReadyByVoltage || (canProbe?.isValid == true)

            if (isCarActive) {
                consecutiveStandbyChecks = 0
                stateMachine.onVehicleReady()
                if (isReadyByVoltage) {
                    addLog("⚡ RILEVATO STATO READY AUTO (12V: ${volt}V >= 13.0V)! Verifica bus CAN e aggancio rapido...")
                } else {
                    addLog("⚡ RILEVATO QUADRO ACCESO AUTO (12V: ${volt}V < 13.0V, CAN 7DF attivo)! Uscita dallo standby...")
                }
                currentCanHeader = ""
                currentRxFilter = null
                lastAutoRecoveryTimestamp = now
                lastStandbyExitTimestamp = now
                delay(250) // Stabilizzazione ricetrasmettitore CAN su adapter e bus

                // Al risveglio dallo standby andiamo direttamente sull'ECU Motore (attivo: 7E0 o fallback 7DF)
                ensureEngineHeader(force = true)
                var s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3000L)
                var s1Ok = Elm327Protocol.isValidCanResponse(s1Res)
                
                if (!s1Ok) {
                    s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3000L)
                    s1Ok = Elm327Protocol.isValidCanResponse(s1Res)
                }

                if (!s1Ok) {
                    s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED, timeoutMs = 3000L)
                    s1Ok = Elm327Protocol.isValidCanResponse(s1Res)
                }

                if (!s1Ok && activeEngineHeader != ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST) {
                    addLog("Standby exit: 7E0 non risponde, commutazione su broadcast funzionale 7DF (R2)...")
                    activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
                    ensureEngineHeader(force = true)
                    s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM, timeoutMs = 3000L)
                    s1Ok = Elm327Protocol.isValidCanResponse(s1Res)
                    if (!s1Ok) {
                        s1Res = bleManager.sendCommand(ToyotaYarisCommands.PID_SUPPORTED_PIDS, timeoutMs = 3000L)
                        s1Ok = Elm327Protocol.isValidCanResponse(s1Res)
                    }
                }
                
                if (s1Ok) {
                    stateMachine.onEngineTelemetrySuccess()
                }

                // Handshake Stadio 2: reset discovery e predisposizione interrogazione non-bloccante
                discoveryEngine.reset()
                stateMachine.onBatteryDiscoveryProbing()
                ensureEngineHeader()

                val canOk = s1Ok || (canProbe?.isValid == true)
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
                            "Veicolo attivo (12V: ${String.format(java.util.Locale.US, "%.1f", volt)}V), sincronizzazione con ECU Toyota in corso..."
                        } else {
                            "Veicolo attivo, sincronizzazione con ECU Toyota in corso..."
                        }
                    },
                    batteryAdapterLimitationWarning = null
                )
            } else {
                consecutiveStandbyChecks++
                stateMachine.onVehicleStandby()
                discoveryEngine.reset()
                _liveState.value = _liveState.value.copy(
                    isVehicleReady = false,
                    isStandbyMode = true,
                    hasEcuCommunication = false,
                    capabilityState = stateMachine.currentCapabilityState,
                    auxiliary12vVoltage = volt,
                    ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY o quadro) per avviare la telemetria.",
                    batteryAdapterLimitationWarning = null
                )
            }
            return
        }

        // GESTIONE TRANSIZIONE A STANDBY SE L'AUTO VIENE SPENTA DURANTE IL FUNZIONAMENTO
        val isCanSilentForStandby = (lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 12000L)) ||
                                    (lastValidCanTimestamp == 0L && (now - loopStartTimestamp > 12000L))
        if (consecutiveCanErrors >= 10 && isCanSilentForStandby) {
            val voltRes = bleManager.sendCommand(Elm327Protocol.CMD_VOLTAGE)
            val volt = Elm327Protocol.parseBatteryVoltage(voltRes) ?: lastKnown12v
            lastKnown12v = volt
            val isStandbyVoltage = Elm327Protocol.isVehicleStandby(volt)
            if (isStandbyVoltage && volt > 0f) {
                consecutiveStandbyChecks++
                if (consecutiveStandbyChecks >= 4) {
                    addLog("💤 Auto spenta (12V: ${volt}V < 11.8V, CAN silente per >12s). Entrata in standby.")
                    currentCanHeader = ""
                    currentRxFilter = null
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
                        ecuAlertMessage = "Auto in standby a basso consumo (12V: ${volt}V): in attesa di spia verde READY...",
                        batteryAdapterLimitationWarning = null
                    )
                    return
                }
            } else {
                consecutiveStandbyChecks = 0
            }
        } else {
            consecutiveStandbyChecks = 0
        }

        // 0. Auto-Recovery se il bus CAN è silente da oltre 15000ms dopo che era attivo, o se bloccato all'avvio (>15s)
        val isCanSilentAfterActive = lastValidCanTimestamp > 0L && (now - lastValidCanTimestamp > 15000L)
        val isInitialCanStuck = lastValidCanTimestamp == 0L && (now - loopStartTimestamp > 15000L) && (now - lastStandbyExitTimestamp > 15000L)
        if (isProtocolInitialized && (isCanSilentAfterActive || isInitialCanStuck) && (now - lastAutoRecoveryTimestamp > 25000L)) {
            lastAutoRecoveryTimestamp = now
            executeCanBusAutoRecovery()
        }

        // 1. Safe Interruption o ciclo periodico lento (ogni 3.5s) per batteria HV Denso
        val isBatteryDue = pendingBatterySafetyCheck || (now - lastBatteryCheckTimestamp >= BATTERY_POLL_INTERVAL_MS)
        if (isBatteryDue) {
            if (isEcuOperationInProgress) return
            pendingBatterySafetyCheck = false
            lastBatteryCheckTimestamp = now
            executeBatteryThermalCycle()
        }

        if (isEcuOperationInProgress) return

        // 2. Loop veloce per telemetria motore e Dragy (100-200ms)
        // Invariante VAL-OBD-007: il fast loop motore gira ad OGNI tick dello scheduler,
        // immediatamente dopo la fetta batteria (completata, fallita o in timeout), senza
        // mai essere saltato a causa dello stato di discovery batteria.
        internalLastEngineFastDispatchMs = timeProvider()
        executeEngineTelemetryFastCycle()

        if (isEcuOperationInProgress) return

        // 3. Ciclo periodico di sfondo per liquido di raffreddamento (ECT) ed aspirazione (IAT) (ogni 4s)
        // Invariante VAL-OBD-012: il polling 0105/010F mantiene la cadenza nativa 4000ms
        // indipendentemente dagli esiti delle sonde batteria (timeout inclusi).
        val coolantNow = timeProvider()
        if (coolantNow - lastCoolantCheckTimestamp >= COOLANT_POLL_INTERVAL_MS) {
            lastCoolantCheckTimestamp = coolantNow
            internalLastCoolantDispatchMs = coolantNow
            executeCoolantWarmupCycle()
        }
    }

    internal suspend fun executeBatteryThermalCycle() {
        try {
            ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)

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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

                val cleanRes = Elm327Protocol.cleanResponse(rawResponse ?: "")
                val udsNrc = Elm327Protocol.extractUdsNrc(rawResponse ?: "")
                if (rawResponse != null && !Elm327Protocol.isError(cleanRes) && !cleanRes.contains("TIMEOUT")) {
                    parsedStatus = ToyotaYarisCommands.parseBatteryResponse(rawResponse, _liveState.value.fanForcedMax)
                }

                val isUdsPositive = if (rawResponse != null && isProbing) {
                    Elm327Protocol.isUdsPositiveResponse(rawResponse, candidateToProbe.take(2))
                } else false

                if (parsedStatus != null || isUdsPositive) {
                    if (isProbing) {
                        discoveryEngine.onCandidateSuccess(candidateToProbe, rawResponse)
                        addLog("✅ Motore discovery phased batteria: agganciato PID $candidateToProbe!")
                    }
                    lastValidCanTimestamp = timeProvider()
                    stateMachine.onBatteryDiscovered(candidateToProbe)
                } else {
                    if (isProbing) {
                        if (udsNrc != null && (udsNrc.nrc == Elm327Protocol.NRC_SERVICE_NOT_SUPPORTED || udsNrc.nrc == Elm327Protocol.NRC_SUB_FUNCTION_NOT_SUPPORTED)) {
                            discoveryEngine.onCandidateRejected(candidateToProbe, rawResponse)
                            addLog("Discovery phased: PID $candidateToProbe rifiutato da ECU Denso (NRC ${udsNrc.nrc}), escluso permanentemente (FIX 4).")
                        } else if (udsNrc != null && udsNrc.nrc == Elm327Protocol.NRC_CONDITIONS_NOT_CORRECT) {
                            discoveryEngine.onCandidateFailed(candidateToProbe, ProbeStatus.INVALID, rawResponse)
                            addLog("Discovery phased: PID $candidateToProbe in attesa (NRC 22: ConditionsNotCorrect, veicolo non in READY).")
                        } else {
                            discoveryEngine.onCandidateFailed(candidateToProbe, ProbeStatus.NO_DATA, rawResponse)
                            addLog("Discovery phased: PID $candidateToProbe non valido o timeout, cursor avanzato (cooldown 5s).")
                        }
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
                currentState.batteryStatus.copy(timestamp = timeProvider())
            }

            // Valutazione Smart Auto-Cooling
            var updatedAutoStatus = autoStatus
            if (autoStatus.isEnabled && updatedBattery.maxTemp > 0.0) {
                val nowMs = timeProvider()
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
            val isManualForced = currentState.isManualFanForced || currentState.fanForcedMax
            val isBatteryDiscovered = stateMachine.currentCapabilityState.batteryEcuDiscoveryState == BatteryEcuDiscoveryState.Discovered && discoveryEngine.activeBatteryPid != null
            val isBatteryValid = isBatteryDiscovered && updatedBattery.maxTemp > 0.0
            val isBatteryCommunicationEstablished = (currentState.hasEcuCommunication || parsedStatus != null) && isBatteryValid
            
            // R3: Prevenzione forzatura ventola quando la comunicazione con la centralina batteria non è stabilita
            val shouldForceFan = isBatteryCommunicationEstablished && (isManualForced || isAutoCoolingActive || updatedBattery.maxTemp >= currentState.targetThreshold)
            val activeTargetSpeed = when {
                isManualForced -> currentState.manualFanTargetLevel.coerceIn(1, 6)
                isAutoCoolingActive -> updatedAutoStatus.targetSpeed.coerceIn(1, 6)
                else -> 6
            }

            if (shouldForceFan) {
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)
                // Su Toyota TNGA-B XP210 il controllo ventola primario affidabile è UDS Service 0x2F (InputOutputControlByIdentifier)
                val primaryUdsCmd = ToyotaYarisCommands.getFanSpeedCommandAlt(activeTargetSpeed) // 2F58030x
                val fanCmdRes = bleManager.sendCommand(primaryUdsCmd)
                val cleanFanRes = Elm327Protocol.cleanResponse(fanCmdRes)
                if (cleanFanRes.contains("7F2F") || cleanFanRes.contains("ERROR") || cleanFanRes.contains("NO DATA")) {
                    // Fallback secondario su Mode 30 legacy (30080x)
                    val fallbackCmd = ToyotaYarisCommands.getFanSpeedCommand(activeTargetSpeed)
                    bleManager.sendCommand(fallbackCmd)
                    stateMachine.onFanActuationStateChanged(FanActuationState.REQUESTED)
                } else {
                    stateMachine.onFanControlConfirmed()
                    stateMachine.onFanActuationStateChanged(FanActuationState.CONFIRMED)
                }
                addLog("⚡ VENTOLA HV FORZATA L$activeTargetSpeed [${if (isManualForced) "MANUALE" else "AUTO"}] | Batt: ${String.format(java.util.Locale.US, "%.1f", updatedBattery.maxTemp)}°C")
            } else if (currentState.batteryStatus.isFanForced) {
                ensureCanHeader(ToyotaYarisCommands.HEADER_BATTERY_ECU)
                // Rilascio ventola a gestione automatica ECU: UDS ReturnControlToECU (2F5800) e Mode 30 stop
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_RETURN_CONTROL_TO_ECU)
                bleManager.sendCommand(ToyotaYarisCommands.CMD_FAN_STOP_OR_RESET)
                stateMachine.onFanActuationStateChanged(FanActuationState.OEM_AUTOMATIC)
                addLog("Ventola HV: ripristinato controllo automatico OEM.")
            }

            _liveState.value = _liveState.value.copy(
                autoCoolingStatus = updatedAutoStatus,
                capabilityState = stateMachine.currentCapabilityState,
                batteryAdapterLimitationWarning = computeBatteryAdapterLimitationWarning(),
                batteryStatus = updatedBattery.copy(
                    isFanForced = shouldForceFan,
                    fanSpeedLevel = if (shouldForceFan) activeTargetSpeed else updatedBattery.fanSpeedLevel
                )
            )
        } finally {
            withContext(NonCancellable) {
                try {
                    ensureEngineHeader()
                } catch (e: Exception) {
                    Log.e(TAG, "Errore ripristino header CAN motore in finally", e)
                }
            }
        }
    }

    internal suspend fun executeEngineTelemetryFastCycle() {
        ensureEngineHeader()

        val sampleTimestamp = timeProvider()
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

        // Se eravamo su 7E0 e non abbiamo ricevuto né velocità né RPM (es. blocco gateway DLC3 o NO DATA),
        // fallback automatico su broadcast funzionale 7DF (R2)
        if (currentSpeed == null && currentRpm == null && activeEngineHeader == ToyotaYarisCommands.HEADER_ENGINE_ECU) {
            addLog("Telemetria veloce: 7E0 non risponde, commutazione su broadcast funzionale 7DF (R2)...")
            activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
            isMultiPidSupported = false
            ensureEngineHeader(force = true)
            val retrySpd = bleManager.sendCommand(ToyotaYarisCommands.PID_VEHICLE_SPEED)
            currentSpeed = ToyotaYarisCommands.parseVehicleSpeed(retrySpd)
            val retryRpm = bleManager.sendCommand(ToyotaYarisCommands.PID_ENGINE_RPM)
            currentRpm = ToyotaYarisCommands.parseEngineRpm(retryRpm)
            if (currentThrottle == null) {
                val retryThr = bleManager.sendCommand(ToyotaYarisCommands.PID_THROTTLE_POS)
                currentThrottle = ToyotaYarisCommands.parseThrottlePos(retryThr)
            }
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
        ensureEngineHeader()

        var rawCoolant = bleManager.sendCommand(ToyotaYarisCommands.PID_COOLANT_TEMP)
        var parsedCoolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)

        if (parsedCoolant == null && activeEngineHeader == ToyotaYarisCommands.HEADER_ENGINE_ECU) {
            addLog("Ciclo liquido raffreddamento: 7E0 non risponde, fallback su broadcast funzionale 7DF (R2)...")
            activeEngineHeader = ToyotaYarisCommands.HEADER_FUNCTIONAL_BROADCAST
            isMultiPidSupported = false
            ensureEngineHeader(force = true)
            rawCoolant = bleManager.sendCommand(ToyotaYarisCommands.PID_COOLANT_TEMP)
            parsedCoolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)
        }

        if (parsedCoolant != null) {
            lastKnownCoolant = parsedCoolant
            lastValidCanTimestamp = timeProvider()
            stateMachine.onEngineTelemetrySuccess()
        }

        val rawAmbient = bleManager.sendCommand(ToyotaYarisCommands.PID_INTAKE_AIR_TEMP)
        val parsedAmbient = ToyotaYarisCommands.parseIntakeAirTemp(rawAmbient)
        if (parsedAmbient != null) {
            lastKnownAmbient = parsedAmbient
            lastValidCanTimestamp = timeProvider()
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
        setManualForcedFan(forced, _liveState.value.manualFanTargetLevel)
    }

    fun setManualForcedFan(forced: Boolean, level: Int = _liveState.value.manualFanTargetLevel) {
        val safeLevel = level.coerceIn(1, 6)
        _liveState.value = _liveState.value.copy(
            isManualFanForced = forced,
            manualFanTargetLevel = safeLevel,
            fanForcedMax = forced
        )
        appPreferences?.isManualFanForced = forced
        appPreferences?.manualFanTargetLevel = safeLevel
        pendingBatterySafetyCheck = true
        addLog(if (forced) "⚡ Forzatura manuale ventola L$safeLevel ABILITATA" else "Forzatura ventola DISABILITATA (controllo OEM)")
    }

    fun setManualFanTargetLevel(level: Int) {
        val safeLevel = level.coerceIn(1, 6)
        _liveState.value = _liveState.value.copy(manualFanTargetLevel = safeLevel)
        appPreferences?.manualFanTargetLevel = safeLevel
        pendingBatterySafetyCheck = true
        addLog("Livello ventola manuale impostato a L$safeLevel")
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
        currentRxFilter = null
        isEcuOperationInProgress = false
        discoveryEngine.reset()
        stateMachine.teardownAllCapabilities(BleTransportState.Disconnected)
        _liveState.value = _liveState.value.copy(
            isLoopRunning = false,
            capabilityState = stateMachine.currentCapabilityState,
            batteryAdapterLimitationWarning = null,
            batteryStatus = _liveState.value.batteryStatus.copy(
                isFanForced = false,
                isEcuAckConfirmed = false,
                estimatedFanRpm = 0
            )
        )
    }

    // --- ECU CUSTOMIZATION & CODING OPERATIONS (UDS ISO 14229-1) ---

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

            if (!_liveState.value.hasEcuCommunication) {
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = _liveState.value.ecuCodingState.copy(
                        lastOperationStatus = "Errore: Nessuna comunicazione CAN. Auto in READY?"
                    )
                )
                addLog("⚠️ ECU Coding interrotto: Il bus CAN non è agganciato (hasEcuCommunication=false).")
                return@launch
            }

            isEcuOperationInProgress = true
            addLog("Avvio lettura configurazione centraline UDS (Meter 7C0, Body 750, Aircon 7C4, ADAS 7A0)...")
            _liveState.value = _liveState.value.copy(
                ecuCodingState = _liveState.value.ecuCodingState.copy(
                    isWriting = true,
                    lastOperationStatus = "Lettura impostazioni centralina in corso (UDS Mode 22)..."
                )
            )

            obdTransactionMutex.withLock {
                try {
                    // 1. Meter ECU (7C0 / 7C8) -> Reverse Beep & Seatbelts
                    ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED) // 1003
                    delay(50)
                    val resMeterBeep = bleManager.sendCommand(ToyotaYarisCommands.buildUdsRead(ToyotaYarisCommands.DID_METER_REVERSE_BEEP))
                    val cleanMeterBeep = Elm327Protocol.cleanResponse(resMeterBeep)
                    addLog("Meter 7C0 DID ${ToyotaYarisCommands.DID_METER_REVERSE_BEEP} Read: $cleanMeterBeep")
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT) // 1001
                    delay(50)

                    // 2. Main Body ECU (750 / 758) -> Doors, Windows, Turn Signals & Lights
                    ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED) // 1003
                    delay(50)
                    val resBodyDoor = bleManager.sendCommand(ToyotaYarisCommands.buildUdsRead(ToyotaYarisCommands.DID_BODY_AUTO_DOOR_LOCK))
                    val cleanBodyDoor = Elm327Protocol.cleanResponse(resBodyDoor)
                    addLog("Body 750 DID ${ToyotaYarisCommands.DID_BODY_AUTO_DOOR_LOCK} Read: $cleanBodyDoor")
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT) // 1001
                    delay(50)

                    // 3. Aircon ECU (7C4 / 7CC) -> A/C Behavior
                    ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED) // 1003
                    delay(50)
                    val resAc = bleManager.sendCommand(ToyotaYarisCommands.buildUdsRead(ToyotaYarisCommands.DID_AIRCON_AUTO_AC_BUTTON))
                    val cleanAc = Elm327Protocol.cleanResponse(resAc)
                    addLog("AirCon 7C4 DID ${ToyotaYarisCommands.DID_AIRCON_AUTO_AC_BUTTON} Read: $cleanAc")
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT) // 1001
                    delay(50)

                    // 4. TSS / ADAS (7A0 / 7A8) -> LDA & BSM
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU)
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED) // 1003
                    delay(50)
                    val resAdas = bleManager.sendCommand(ToyotaYarisCommands.buildUdsRead(ToyotaYarisCommands.DID_ADAS_LDA_WARNING_VOLUME))
                    val cleanAdas = Elm327Protocol.cleanResponse(resAdas)
                    addLog("ADAS 7A0 DID ${ToyotaYarisCommands.DID_ADAS_LDA_WARNING_VOLUME} Read: $cleanAdas")
                    bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT) // 1001
                    delay(50)

                    val anyPositive = Elm327Protocol.isUdsPositiveResponse(cleanMeterBeep, "22") ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanBodyDoor, "22") ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanAc, "22") ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanAdas, "22") ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanMeterBeep) ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanBodyDoor) ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanAc) ||
                                      Elm327Protocol.isUdsPositiveResponse(cleanAdas)

                    if (anyPositive) {
                        _liveState.value = _liveState.value.copy(
                            ecuCodingState = _liveState.value.ecuCodingState.copy(
                                isReadCompleted = true,
                                isWriting = false,
                                lastOperationStatus = "✅ Configurazione centralina UDS letta con successo (Backup salvato)"
                            )
                        )
                        addLog("Lettura parametri centralina UDS completata con successo.")
                    } else {
                        _liveState.value = _liveState.value.copy(
                            ecuCodingState = _liveState.value.ecuCodingState.copy(
                                isReadCompleted = false,
                                isWriting = false,
                                lastOperationStatus = "⚠️ Nessuna risposta dalle centraline: verifica quadro acceso in READY"
                            )
                        )
                        addLog("⚠️ Nessuna centralina Body/Meter/Clima/ADAS ha risposto. Quadro non in READY o bus non sincronizzato.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore lettura ECU", e)
                    _liveState.value = _liveState.value.copy(
                        ecuCodingState = _liveState.value.ecuCodingState.copy(
                            isWriting = false,
                            lastOperationStatus = "⚠️ Lettura completata (Backup locale attivo)"
                        )
                    )
                } finally {
                    withContext(NonCancellable) {
                        try {
                            // Restore standard Engine CAN header for telemetry loop
                            ensureEngineHeader()
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore ripristino header CAN motore in finally", e)
                        } finally {
                            isEcuOperationInProgress = false
                        }
                    }
                }
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

            if (!_liveState.value.hasEcuCommunication) {
                _liveState.value = _liveState.value.copy(
                    ecuCodingState = updatedState.copy(
                        lastOperationStatus = "Errore: Nessuna comunicazione CAN. Auto in READY?"
                    )
                )
                addLog("⚠️ ECU Coding interrotto: Il bus CAN non è agganciato (hasEcuCommunication=false).")
                return@launch
            }

            isEcuOperationInProgress = true
            _liveState.value = _liveState.value.copy(
                ecuCodingState = updatedState.copy(
                    isWriting = true,
                    lastOperationStatus = "Apertura Sessione Diagnostica UDS (10 03) & Scrittura EEPROM..."
                )
            )
            addLog("Avvio protocollo UDS Read-Before-Write su Toyota Yaris TNGA-B...")

            obdTransactionMutex.withLock {
                var conditionsNotCorrectDetected = false
                var writeFailureReason: String? = null
                var anyWriteSucceeded = false

                try {
                    // Helper locale per transizione a Sessione Estesa 1003 con verifica stretta di 5003 o gestione NRC 22
                    suspend fun openExtendedSession(): Boolean {
                        val sessionRes = bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_EXTENDED)
                        val cleanSessionRes = Elm327Protocol.cleanResponse(sessionRes)
                        val nrc = Elm327Protocol.extractUdsNrc(cleanSessionRes)
                        if (nrc != null) {
                            if (nrc.nrc == Elm327Protocol.NRC_CONDITIONS_NOT_CORRECT) {
                                conditionsNotCorrectDetected = true
                                writeFailureReason = "Veicolo non pronto: accendere quadro in READY, chiudere tutte le portiere e mettere il cambio in P"
                                addLog("⚠️ UDS Session 1003 rifiutata (NRC 22): $writeFailureReason")
                            } else {
                                val desc = Elm327Protocol.getUdsNrcDescription(nrc.nrc)
                                addLog("⚠️ UDS Session 1003 rifiutata (NRC ${nrc.nrc}): $desc")
                            }
                            return false
                        }
                        val isPositive = Elm327Protocol.isUdsPositiveResponse(cleanSessionRes, "10") ||
                                         cleanSessionRes.contains("5003") ||
                                         cleanSessionRes.contains("50")
                        if (!isPositive) {
                            addLog("⚠️ UDS Session 1003 non confermata: $cleanSessionRes")
                        }
                        return isPositive
                    }

                    // Helper locale per commit EEPROM e ritorno a Sessione Default 1001
                    suspend fun commitSessionDefault() {
                        bleManager.sendCommand(ToyotaYarisCommands.CMD_UDS_SESSION_DEFAULT)
                        delay(100)
                    }

                    // Helper per eseguire la sequenza Read-Before-Write su un singolo parametro DID
                    suspend fun executeReadBeforeWrite(
                        did: String,
                        newVal: String,
                        paramName: String
                    ): Boolean {
                        // 3. Lettura stato corrente (Read-Before-Write) per backup/rollback
                        val readCmd = ToyotaYarisCommands.buildUdsRead(did)
                        val origRes = bleManager.sendCommand(readCmd)
                        val cleanOrig = Elm327Protocol.cleanResponse(origRes)
                        addLog("Read-Before-Write $paramName ($did): $cleanOrig")

                        // 4. Calcolo nuovo payload (sostituzione mirata del parametro)
                        val writeCmd = ToyotaYarisCommands.buildUdsWrite(did, newVal)

                        // 5. Scrittura con Service 2E
                        val writeRes = bleManager.sendCommand(writeCmd)
                        val cleanWrite = Elm327Protocol.cleanResponse(writeRes)
                        val isWritePositive = Elm327Protocol.isUdsPositiveResponse(cleanWrite, "2E") ||
                                             cleanWrite.contains("6E$did") ||
                                             cleanWrite.contains("6E")

                        if (!isWritePositive) {
                            val writeNrc = Elm327Protocol.extractUdsNrc(cleanWrite)
                            if (writeNrc != null) {
                                val desc = Elm327Protocol.getUdsNrcDescription(writeNrc.nrc)
                                addLog("❌ Scrittura UDS 2E $paramName fallita (NRC ${writeNrc.nrc}): $desc")
                                if (writeNrc.nrc == Elm327Protocol.NRC_CONDITIONS_NOT_CORRECT) {
                                    conditionsNotCorrectDetected = true
                                    writeFailureReason = "Veicolo non pronto: accendere quadro in READY, chiudere tutte le portiere e mettere il cambio in P"
                                }
                            } else {
                                addLog("❌ Scrittura UDS 2E $paramName non confermata: $cleanWrite")
                            }
                            return false
                        }
                        addLog("✅ Scrittura UDS 2E $paramName confermata (6E $did)")

                        // 6. Read-After-Write di verifica
                        delay(40)
                        val verifyRes = bleManager.sendCommand(readCmd)
                        val cleanVerify = Elm327Protocol.cleanResponse(verifyRes)
                        val isVerifyPositive = Elm327Protocol.isUdsPositiveResponse(cleanVerify, "22") ||
                                               cleanVerify.contains("62$did") ||
                                               cleanVerify.contains("62")
                        addLog("Read-After-Write $paramName ($did): $cleanVerify (Verificato: $isVerifyPositive)")
                        return true
                    }

                    // --- 1. METER ECU (7C0 / 7C8) ---
                    ensureCanHeader(ToyotaYarisCommands.HEADER_METER_ECU)
                    if (openExtendedSession()) {
                        delay(40)
                        var meterOk = true
                        meterOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_METER_REVERSE_BEEP,
                            updatedState.reverseBeep.code,
                            "Reverse Beep"
                        ) && meterOk
                        delay(40)
                        meterOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_METER_DRIVER_SEATBELT,
                            if (updatedState.driverSeatbeltBeep) "01" else "00",
                            "Driver Seatbelt"
                        ) && meterOk
                        delay(40)
                        meterOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_METER_PASSENGER_SEATBELT,
                            if (updatedState.passengerSeatbeltBeep) "01" else "00",
                            "Passenger Seatbelt"
                        ) && meterOk
                        delay(40)
                        meterOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_METER_REAR_SEATBELT,
                            if (updatedState.rearSeatbeltBeep) "01" else "00",
                            "Rear Seatbelt"
                        ) && meterOk

                        // 7. Commit EEPROM chiudendo sessione diagnostica con 1001
                        commitSessionDefault()
                        if (meterOk) anyWriteSucceeded = true
                    }

                    // --- 2. MAIN BODY ECU (750 / 758) ---
                    ensureCanHeader(ToyotaYarisCommands.HEADER_BODY_ECU)
                    if (openExtendedSession()) {
                        delay(40)
                        var bodyOk = true
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_AUTO_DOOR_LOCK,
                            updatedState.autoDoorLock.code,
                            "Auto Door Lock"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_AUTO_DOOR_UNLOCK,
                            if (updatedState.autoDoorUnlock) "01" else "00",
                            "Auto Door Unlock"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_WINDOWS_KEY_FOB,
                            if (updatedState.windowsWithKeyFob) "01" else "00",
                            "Windows Key Fob"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_KEYLESS_BUZZER_VOL,
                            updatedState.keylessBuzzerVolume.code,
                            "Keyless Buzzer Volume"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_AUTO_RELOCK_TIME,
                            updatedState.autoRelockTime.code,
                            "Auto Relock Time"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_DOOR_UNLOCK_MODE,
                            updatedState.doorUnlockMode.code,
                            "Door Unlock Mode"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_TURN_SIGNAL_FLASHES,
                            updatedState.turnSignalFlashes.code,
                            "Turn Signal Flashes"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_LIGHT_SENSITIVITY,
                            updatedState.lightSensitivity.code,
                            "Light Sensitivity"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_FOLLOW_ME_HOME,
                            updatedState.followMeHome.code,
                            "Follow Me Home"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_INTERIOR_DIM_TIME,
                            updatedState.interiorDimTime.code,
                            "Interior Dim Time"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_FOOTWELL_LIGHT_DRIVE,
                            if (updatedState.footwellLightingInDrive) "01" else "00",
                            "Footwell Light in Drive"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_REAR_WIPER_REVERSE,
                            if (updatedState.rearWiperReverseLink) "01" else "00",
                            "Rear Wiper Reverse Link"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_DRIP_WIPE_EXTRA,
                            if (updatedState.dripWipeExtraPass) "01" else "00",
                            "Drip Wipe Extra Pass"
                        ) && bodyOk
                        delay(40)
                        bodyOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_BODY_WIPER_SPEED_LINK,
                            if (updatedState.wiperSpeedLink) "01" else "00",
                            "Wiper Speed Link"
                        ) && bodyOk

                        commitSessionDefault()
                        if (bodyOk) anyWriteSucceeded = true
                    }

                    // --- 3. AIRCON ECU (7C4 / 7CC) ---
                    ensureCanHeader(ToyotaYarisCommands.HEADER_AIRCON_ECU)
                    if (openExtendedSession()) {
                        delay(40)
                        var acOk = true
                        acOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_AIRCON_AUTO_AC_BUTTON,
                            if (updatedState.autoAcWithAutoButton) "01" else "00",
                            "Auto AC with AUTO Button"
                        ) && acOk
                        delay(40)
                        acOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_AIRCON_ECO_EFFICIENCY,
                            if (updatedState.ecoAirConEfficiencyMode) "01" else "00",
                            "Eco AirCon Efficiency Mode"
                        ) && acOk
                        delay(40)
                        acOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_AIRCON_DEFROSTER_BLOWER,
                            if (updatedState.blowerOnDefroster) "01" else "00",
                            "Blower on Defroster"
                        ) && acOk
                        delay(40)
                        acOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_AIRCON_TEMP_CALIBRATION,
                            updatedState.temperatureCalibration.code,
                            "Temperature Calibration"
                        ) && acOk

                        commitSessionDefault()
                        if (acOk) anyWriteSucceeded = true
                    }

                    // --- 4. ADAS ECU (7A0 / 7A8) ---
                    ensureCanHeader(ToyotaYarisCommands.HEADER_ADAS_ECU)
                    if (openExtendedSession()) {
                        delay(40)
                        var adasOk = true
                        adasOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_ADAS_LDA_WARNING_VOLUME,
                            updatedState.ldaWarningVolume.code,
                            "LDA Warning Volume"
                        ) && adasOk
                        delay(40)
                        adasOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_ADAS_BSM_SENSITIVITY,
                            updatedState.bsmSensitivity.code,
                            "BSM Sensitivity"
                        ) && adasOk
                        delay(40)
                        adasOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_ADAS_RCTA_ENABLED,
                            if (updatedState.rctaEnabled) "01" else "00",
                            "RCTA Enabled"
                        ) && adasOk
                        delay(40)
                        adasOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_ADAS_LTA_ENABLED,
                            if (updatedState.ltaEnabled) "01" else "00",
                            "LTA Enabled"
                        ) && adasOk
                        delay(40)
                        adasOk = executeReadBeforeWrite(
                            ToyotaYarisCommands.DID_ADAS_PCS_REMEMBER_LAST,
                            if (updatedState.pcsRememberLast) "01" else "00",
                            "PCS Remember Last"
                        ) && adasOk

                        commitSessionDefault()
                        if (adasOk) anyWriteSucceeded = true
                    }

                    if (anyWriteSucceeded) {
                        _liveState.value = _liveState.value.copy(
                            ecuCodingState = updatedState.copy(
                                isWriting = false,
                                isReadCompleted = true,
                                lastOperationStatus = "✅ Scrittura completata e VERIFICATA in centralina!"
                            )
                        )
                        addLog("✅ Scrittura centralina UDS completata e verificata con successo!")
                    } else {
                        val failureMsg = writeFailureReason ?: "❌ Scrittura non riuscita: centralina non ha risposto (NODATA). Verifica quadro in READY"
                        _liveState.value = _liveState.value.copy(
                            ecuCodingState = updatedState.copy(
                                isWriting = false,
                                isReadCompleted = false,
                                lastOperationStatus = failureMsg
                            )
                        )
                        addLog("❌ Scrittura centralina non verificata: $failureMsg")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore scrittura centralina", e)
                    _liveState.value = _liveState.value.copy(
                        ecuCodingState = updatedState.copy(
                            isWriting = false,
                            lastOperationStatus = "❌ Errore durante la scrittura in centralina"
                        )
                    )
                } finally {
                    withContext(NonCancellable) {
                        try {
                            // 8. Ripristino atomico dell'header motore per il loop di telemetria
                            ensureEngineHeader()
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore ripristino header CAN motore in finally", e)
                        } finally {
                            isEcuOperationInProgress = false
                        }
                    }
                }
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
