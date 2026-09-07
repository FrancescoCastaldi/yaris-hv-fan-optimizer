package com.yaris.hvfan

import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * Suite di integrazione simulata dedicata alla gestione degli errori dell'adapter OBD-II.
 *
 * Riproduce, tramite [FakeObdTransport], i pattern di guasto tipici degli adapter ELM327
 * clone/Vlinker osservati sul campo (v2.9.13 e precedenti):
 * 1. NODATA persistente su tutta la fallback chain batteria (7E2/2228C1 e varianti) mentre
 *    motore/body/meter continuano a rispondere correttamente (single-frame).
 * 2. Risposte malformate/non parsabili sul PID batteria.
 * 3. Eccezioni di trasporto (disconnessioni BLE transitorie) durante una query batteria.
 * 4. Risposte UDS negative (0x7F) su tutta la fallback chain.
 * 5. Adapter "flaky" che fallisce ripetutamente e poi recupera.
 *
 * Ogni scenario verifica che: (a) non venga mai lanciata un'eccezione che interrompe lo
 * scheduler, (b) la telemetria motore non venga mai bloccata da un guasto isolato sulla
 * batteria (VAL-OBD-008), e (c) lo stato esposto in [ObdLiveState] rifletta accuratamente
 * il guasto (incluso il nuovo [ObdLiveState.batteryAdapterLimitationWarning] introdotto in v2.9.14).
 */
class AdapterErrorHandlingIntegrationTest {

    private class FakeObdTransport : ObdTransport {
        private val _connectionState = MutableStateFlow<BleConnectionState>(
            BleConnectionState.Connected("Generic ELM327 Clone", "00:11:22:33:44:55")
        )
        override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        val dispatchedCommands = mutableListOf<String>()
        var commandResponder: suspend (command: String, timeoutMs: Long) -> String = { _, _ -> "OK" }

        override fun getConnectedDeviceName(): String = "Generic ELM327 Clone"

        override suspend fun sendWakeSequence() {
            dispatchedCommands.add("WAKE_SEQUENCE")
        }

        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            dispatchedCommands.add(command)
            return commandResponder(command, timeoutMs)
        }
    }

    private class ManualClock(startMs: Long = 100_000L) {
        var nowMs: Long = startMs
        fun now(): Long = nowMs
        fun advance(ms: Long) { nowMs += ms }
    }

    /** Risposte single-frame plausibili per motore/body/meter, usate in tutti gli scenari. */
    private fun engineAndAuxResponses(cmd: String): String? = when (cmd) {
        ToyotaYarisCommands.PID_SUPPORTED_PIDS -> "7E8 06 41 00 BE 7F A8 11 >"
        ToyotaYarisCommands.PID_VEHICLE_SPEED -> "7E8 03 41 0D 32 >"
        ToyotaYarisCommands.PID_ENGINE_RPM -> "7E8 04 41 0C 1F 40 >"
        ToyotaYarisCommands.PID_THROTTLE_POS -> "7E8 03 41 11 40 >"
        ToyotaYarisCommands.PID_COOLANT_TEMP -> "7E8 03 41 05 78 >"
        ToyotaYarisCommands.PID_INTAKE_AIR_TEMP -> "7E8 03 41 0F 41 >"
        ToyotaYarisCommands.PID_TIMING_ADVANCE -> "7E8 03 41 0E A0 >"
        ToyotaYarisCommands.PID_ENGINE_LOAD -> "7E8 03 41 04 80 >"
        ToyotaYarisCommands.CMD_TESTER_PRESENT -> "7EA 01 7E"
        else -> null
    }

    /**
     * Scenario 1 (VAL-ADP-001): riproduce esattamente il pattern degli screenshot di campo —
     * l'intera fallback chain batteria (2228C1 -> 2228C0 -> 220101 -> 2101 -> 21C3 -> 2161)
     * restituisce sempre NODATA, mentre motore/body rispondono regolarmente. Verifica che dopo
     * più cicli: (a) la telemetria motore resta viva, (b) nessuna eccezione propaga, (c) il
     * nuovo alert di probabile incompatibilità hardware si attiva.
     */
    @Test
    fun testAdp001_persistentNoDataOnBatteryChain_engineTelemetryAliveAndHardwareWarningFires() = runTest {
        val clock = ManualClock()
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            engineAndAuxResponses(cmd)
                ?: if (cmd.startsWith("AT")) "OK"
                else if (ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)) "NO DATA"
                else "OK"
        }

        val engine = BatteryDiscoveryEngine(timeProvider = { clock.now() })
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = engine,
            timeProvider = { clock.now() }
        )

        // Lap 1: tutti i 6 candidati falliscono una volta, ciascuno entra in cooldown 30s.
        val lap1Start = clock.now()
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            controller.executeEngineTelemetryFastCycle()
            clock.advance(3500L)
        }
        // Allinea l'orologio esattamente alla scadenza del cooldown del primo candidato, cosi'
        // il lap 2 puo' effettivamente ri-probare (getNextCandidate() non deve mai restituire
        // null per cooldown residuo di TUTTI i candidati, altrimenti il giro non avanza).
        clock.nowMs = lap1Start + engine.cooldownDurationMs
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            controller.executeEngineTelemetryFastCycle()
            clock.advance(3500L)
        }

        assertTrue(
            "La telemetria motore deve continuare ad aggiornarsi nonostante NODATA persistente sulla batteria (VAL-OBD-008)",
            controller.liveState.value.performanceStatus.hasLiveData
        )
        assertFalse(
            "L'engine non deve mai latchare un PID batteria quando tutti restituiscono NODATA",
            engine.isDiscovered
        )
        assertNotNull(
            "Dopo >= 2 giri completi falliti, deve attivarsi l'avviso di probabile limite hardware",
            controller.liveState.value.batteryAdapterLimitationWarning
        )
        assertTrue(
            controller.liveState.value.batteryAdapterLimitationWarning!!.contains("STN")
        )
    }

    /**
     * Scenario 2 (VAL-ADP-002): l'adapter risponde "positivamente" (nessun NODATA/ERROR) ma con
     * un payload spazzatura/non conforme al formato UDS atteso su TUTTI i candidati batteria.
     * Deve essere trattato come fallimento del candidato (cooldown), non causare un crash del
     * parser né una falsa scoperta.
     */
    @Test
    fun testAdp002_malformedGarbagePayloadOnBatteryPid_treatedAsFailureNotCrash() = runTest {
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            engineAndAuxResponses(cmd)
                ?: if (cmd.startsWith("AT")) "OK"
                else if (ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)) "FF FF FF garbage não-hex ¿?"
                else "OK"
        }

        val engine = BatteryDiscoveryEngine()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = engine
        )

        // Non deve lanciare eccezioni nonostante il payload non valido.
        controller.executeBatteryThermalCycle()

        assertFalse("Un payload spazzatura non deve mai essere accettato come scoperta valida", engine.isDiscovered)
        assertTrue(
            "Il primo candidato deve entrare in cooldown dopo un payload non parsabile",
            engine.isCandidateInCooldown(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0])
        )
        // L'header CAN deve comunque essere ripristinato al motore (invariante VAL-OBD-006).
        assertEquals(ToyotaYarisCommands.HEADER_ENGINE_ECU, controller.currentCanHeader)
    }

    /**
     * Scenario 3 (VAL-ADP-003): il trasporto lancia un'eccezione (es. disconnessione BLE
     * transitoria / GATT error) durante l'invio del comando keep-alive o della query batteria.
     * L'eccezione deve restare isolata al ciclo batteria (VAL-OBD-008): lo scheduler non deve
     * mai propagare l'eccezione né saltare il ripristino dell'header CAN motore.
     */
    @Test
    fun testAdp003_transportExceptionDuringBatteryQuery_isolatedAndHeaderRestored() = runTest {
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            when {
                cmd == ToyotaYarisCommands.CMD_TESTER_PRESENT ->
                    throw IOException("Simulated GATT disconnect durante keep-alive 7E2")
                cmd.startsWith("AT") -> "OK"
                else -> "OK"
            }
        }

        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = BatteryDiscoveryEngine()
        )

        // executeDualRateCycle() e' il punto di ingresso con isolamento eccezioni (VAL-OBD-008):
        // una IOException nella fetta batteria non deve mai propagare né bloccare lo scheduler.
        controller.executeDualRateCycle()

        assertEquals(
            "Anche sotto eccezione di trasporto, l'header deve essere ripristinato al motore in finally",
            ToyotaYarisCommands.HEADER_ENGINE_ECU,
            controller.currentCanHeader
        )

        // Il ciclo successivo deve poter proseguire normalmente (nessuno stato corrotto residuo).
        fakeTransport.commandResponder = { cmd, _ -> engineAndAuxResponses(cmd) ?: "OK" }
        controller.executeDualRateCycle()
        assertTrue(
            "Dopo il ripristino del trasporto, la telemetria motore deve tornare a fluire normalmente",
            controller.liveState.value.performanceStatus.hasLiveData
        )
    }

    /**
     * Scenario 4 (VAL-ADP-004): tutti i candidati della fallback chain rispondono con una
     * risposta UDS negativa esplicita (0x7F, es. requestOutOfRange / subFunctionNotSupported)
     * anziché NODATA. Deve essere comunque trattato come fallimento del candidato.
     */
    @Test
    fun testAdp004_negativeUdsResponseOnAllBatteryPids_rejectedAsFailure() = runTest {
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            engineAndAuxResponses(cmd)
                ?: if (cmd.startsWith("AT")) "OK"
                else if (ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd)) "7EA 03 7F 22 31 >"
                else "OK"
        }

        val engine = BatteryDiscoveryEngine()
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = engine
        )

        controller.executeBatteryThermalCycle()

        assertFalse("Una risposta UDS negativa non deve mai essere accettata come scoperta valida", engine.isDiscovered)
        assertTrue(
            "Il candidato deve entrare in cooldown dopo una risposta UDS negativa (7F)",
            engine.isCandidateInCooldown(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0])
        )
    }

    /**
     * Scenario 5 (VAL-ADP-005): adapter "flaky" — fallisce per 2 giri completi (attivando
     * l'avviso hardware), poi improvvisamente risponde correttamente al primo candidato
     * ripresentato dopo il cooldown. L'avviso deve sparire automaticamente (si azzera
     * completedFailureCycles su onCandidateSuccess) e la scoperta deve latchare.
     */
    @Test
    fun testAdp005_flakyAdapterRecoversAfterRepeatedFailures_warningClearsOnSuccess() = runTest {
        var currentTime = 100_000L
        val validPid = ToyotaYarisCommands.PID_READ_BATTERY_DATA_TNGA
        val sampleBatteryResponse =
            "7EA 21 00 62 28 C1 1B 00 9C 00 00 00 00 00 00 00 1E 20 22 21 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"

        var recovered = false
        val fakeTransport = FakeObdTransport()
        fakeTransport.commandResponder = { cmd, _ ->
            engineAndAuxResponses(cmd) ?: when {
                cmd.startsWith("AT") -> "OK"
                cmd == validPid && recovered -> sampleBatteryResponse
                ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.contains(cmd) -> "NO DATA"
                else -> "OK"
            }
        }

        val engine = BatteryDiscoveryEngine(timeProvider = { currentTime })
        val controller = ObdController(
            bleManager = fakeTransport,
            scope = this,
            stateMachine = ObdStateMachine(),
            discoveryEngine = engine,
            timeProvider = { currentTime }
        )

        // Lap 1: tutti i 6 candidati falliscono una volta.
        val lap1Start = currentTime
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            currentTime += 3500L
        }
        // Allinea l'orologio alla scadenza del cooldown del primo candidato per consentire al
        // lap 2 di ri-probare (altrimenti getNextCandidate() restituisce null per tutti).
        currentTime = lap1Start + engine.cooldownDurationMs
        // Lap 2: tutti i 6 candidati falliscono di nuovo -> 2 giri completi -> attiva l'avviso.
        repeat(ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size) {
            controller.executeBatteryThermalCycle()
            currentTime += 3500L
        }
        assertNotNull(
            "L'avviso deve essere attivo dopo 2 giri completi falliti",
            controller.liveState.value.batteryAdapterLimitationWarning
        )

        // L'adapter "si riprende": il prossimo candidato ripresentato (dopo il suo cooldown
        // scaduto) risponde correttamente.
        recovered = true
        currentTime += engine.cooldownDurationMs + 1_000L
        controller.executeBatteryThermalCycle()

        assertTrue("La scoperta deve latchare dopo il recupero dell'adapter", engine.isDiscovered)
        assertNull(
            "L'avviso di limite hardware deve sparire automaticamente dopo una scoperta riuscita",
            controller.liveState.value.batteryAdapterLimitationWarning
        )
        assertEquals(0, engine.completedFailureCycles)
    }
}
