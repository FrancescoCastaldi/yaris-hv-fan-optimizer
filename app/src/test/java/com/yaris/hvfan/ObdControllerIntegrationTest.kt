package com.yaris.hvfan

import com.yaris.hvfan.obd.*
import org.junit.Assert.*
import org.junit.Test

class ObdControllerIntegrationTest {

    @Test
    fun testCockpitDragyAccelerationTimerLogic() {
        var isArmed = true
        var startTimeMs = 0L
        var time0to100Ms: Long? = null

        // 1. Standstill (0 km/h) -> Armed
        val speed0 = 0
        assertTrue(isArmed)

        // 2. Launch start (5 km/h)
        val speedLaunch = 5
        startTimeMs = 1000L // 1.000s
        isArmed = false

        // 3. Reaching 100 km/h at 10.450s
        val speed100 = 100
        val finishTimeMs = 10450L
        if (speed100 >= 100 && time0to100Ms == null) {
            time0to100Ms = finishTimeMs - startTimeMs
        }

        assertNotNull(time0to100Ms)
        assertEquals(9450L, time0to100Ms) // 9.45s 0-100 km/h
    }

    @Test
    fun testWarmupStateMachineLogic() {
        // Test S0 (Engine Cold, initial cranking)
        var coolantTemp = 18.0
        var phase = when {
            coolantTemp < 40.0 -> "S1a (Riscaldamento Catalizzatore)"
            coolantTemp < 55.0 -> "S1b (Riscaldamento Iniziale Motore)"
            coolantTemp < 70.0 -> "S2 (Controllo Stechiometrico)"
            else -> "S4 (Piena Efficienza Ibrida / EV Completo)"
        }
        assertEquals("S1a (Riscaldamento Catalizzatore)", phase)

        // Test S4 (Warm Engine >= 70°C)
        coolantTemp = 88.0
        phase = when {
            coolantTemp < 40.0 -> "S1a (Riscaldamento Catalizzatore)"
            coolantTemp < 55.0 -> "S1b (Riscaldamento Iniziale Motore)"
            coolantTemp < 70.0 -> "S2 (Controllo Stechiometrico)"
            else -> "S4 (Piena Efficienza Ibrida / EV Completo)"
        }
        assertEquals("S4 (Piena Efficienza Ibrida / EV Completo)", phase)
    }

    @Test
    fun testEcuCustomizationWritePayloads() {
        val state = EcuCustomizationState(
            touch3OpeningAnimation = Touch3OpeningScreen.GAZOO_RACING,
            reverseBeep = ReverseBeepMode.SINGLE,
            autoDoorLock = AutoDoorLockMode.BY_SPEED,
            turnSignalFlashes = TurnSignalFlashes.FLASHES_5,
            keylessBuzzerVolume = KeylessBuzzerVolume.MEDIUM
        )

        // Verify UDS hex codes
        assertEquals("01", state.touch3OpeningAnimation.code)
        assertEquals("00", state.reverseBeep.code)
        assertEquals("01", state.autoDoorLock.code)
        assertEquals("05", state.turnSignalFlashes.code)
        assertEquals("04", state.keylessBuzzerVolume.code)
    }

    @Test
    fun testFactoryRestorePayloads() {
        // Factory OEM defaults
        val oemReverseBeep = ReverseBeepMode.CONTINUOUS
        val oemTurnSignals = TurnSignalFlashes.FLASHES_3
        val oemTouchScreen = Touch3OpeningScreen.STANDARD_TOYOTA

        assertEquals("01", oemReverseBeep.code)
        assertEquals("03", oemTurnSignals.code)
        assertEquals("00", oemTouchScreen.code)
    }

    @Test
    fun testElm327ProtocolInitAndErrorHandling() {
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT Z"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AT 1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT ST 96"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT SP 6"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT CAF 1"))
        assertEquals("AT SP 0", Elm327Protocol.PROTOCOL_FALLBACK)

        val rawWithGarbage = "SEARCHING...\r\n7E8 03 41 05 5A >"
        val cleaned = Elm327Protocol.cleanResponse(rawWithGarbage)
        assertEquals("7E80341055A", cleaned)

        assertTrue(Elm327Protocol.isError("NO DATA"))
        assertTrue(Elm327Protocol.isError("UNABLE TO CONNECT"))
        assertTrue(Elm327Protocol.isError("CAN ERROR"))
        assertTrue(Elm327Protocol.isError("BUFFER FULL"))
        assertTrue(Elm327Protocol.isError("?"))
        assertTrue(Elm327Protocol.isError(""))
        assertFalse(Elm327Protocol.isError("7EA 07 62 28 C1 28 00 00 >"))
    }

    @Test
    fun testBluetoothTransportEnum() {
        val ble = com.yaris.hvfan.ble.BluetoothTransportType.BLE
        val classic = com.yaris.hvfan.ble.BluetoothTransportType.CLASSIC_SPP
        val auto = com.yaris.hvfan.ble.BluetoothTransportType.AUTO

        assertEquals("BLE", ble.name)
        assertEquals("CLASSIC_SPP", classic.name)
        assertEquals("AUTO", auto.name)
    }

    @Test
    fun testToyotaPidParsers() {
        // 1. Vehicle Speed (100 km/h: 0x64 = 100)
        val rawSpeed = "7E8 03 41 0D 64 >"
        val speed = ToyotaYarisCommands.parseVehicleSpeed(rawSpeed)
        assertNotNull(speed)
        assertEquals(100, speed)

        // 2. Coolant Temp (80°C: 0x78 = 120 - 40 = 80)
        val rawCoolant = "7E8 03 41 05 78 >"
        val coolant = ToyotaYarisCommands.parseCoolantTemp(rawCoolant)
        assertNotNull(coolant)
        assertEquals(80.0f, coolant!!, 0.1f)

        // 3. Intake Air Temp (25°C: 0x41 = 65 - 40 = 25)
        val rawIat = "7E8 03 41 0F 41 >"
        val iat = ToyotaYarisCommands.parseIntakeAirTemp(rawIat)
        assertNotNull(iat)
        assertEquals(25.0f, iat!!, 0.1f)

        // 4. Engine RPM (2000 RPM: 0x1F40 = 8000 / 4 = 2000)
        val rawRpm = "7E8 04 41 0C 1F 40 >"
        val rpm = ToyotaYarisCommands.parseEngineRpm(rawRpm)
        assertNotNull(rpm)
        assertEquals(2000, rpm)

        // 5. Timing Advance (16.0°BTDC: 0xA0 = 160 / 2 - 64 = 16.0)
        val rawAdvance = "7E8 03 41 0E A0 >"
        val advance = ToyotaYarisCommands.parseTimingAdvance(rawAdvance)
        assertNotNull(advance)
        assertEquals(16.0f, advance!!, 0.1f)

        // 6. Engine Load (50%: 0x80 = 128 * 100 / 255 = 50.19%)
        val rawLoad = "7E8 03 41 04 80 >"
        val load = ToyotaYarisCommands.parseEngineLoad(rawLoad)
        assertNotNull(load)
        assertEquals(50.19f, load!!, 0.5f)

        // 7. Throttle Position (40%: 0x66 = 102 * 100 / 255 = 40.0%)
        val rawThrottle = "7E8 03 41 11 66 >"
        val throttle = ToyotaYarisCommands.parseThrottlePos(rawThrottle)
        assertNotNull(throttle)
        assertEquals(40.0f, throttle!!, 0.5f)
    }

    @Test
    fun testToyotaBatteryResponseParser() {
        // TNGA Yaris MK4 Frame (22 28 C1 -> 62 28 C1 44 45 44 43 41 03 -> T1=28°C, T2=29°C, T3=28°C, T4=27°C, Intake=25°C, Fan=3)
        val rawBatteryOk = "7EA 10 0E 62 28 C1 44 45 44 43 41 03 >"
        val batteryOk = ToyotaYarisCommands.parseBatteryResponse(rawBatteryOk, false)
        assertNotNull(batteryOk)
        assertEquals(28.0, batteryOk!!.temp1, 0.1)
        assertEquals(29.0, batteryOk.temp2, 0.1)
        assertEquals(29.0, batteryOk.maxTemp, 0.1)
        assertEquals(3, batteryOk.fanSpeedLevel)
        assertFalse(batteryOk.isThermalThrottled)

        // Thermal Throttling Frame (Max Temp = 38°C -> isThermalThrottled = true)
        val rawBatteryHot = "7EA 10 0E 62 28 C1 4E 4E 4C 4B 41 00 >" // 0x4E = 78 - 40 = 38°C
        val batteryHot = ToyotaYarisCommands.parseBatteryResponse(rawBatteryHot, false)
        assertNotNull(batteryHot)
        assertEquals(38.0, batteryHot!!.maxTemp, 0.1)
        assertTrue(batteryHot.isThermalThrottled)
    }

    @Test
    fun testComprehensiveWarmupStages() {
        // S0: Cold Standstill
        val s0 = ToyotaYarisCommands.evaluateWarmupStatus(20f, 15f, 0)
        assertEquals(WarmupStage.S0, s0.stage)
        assertTrue(s0.progressPercent <= 0.25f)

        // S1A: Cold Cat Warmup (Cranks engine)
        val s1a = ToyotaYarisCommands.evaluateWarmupStatus(30f, 15f, 1300)
        assertEquals(WarmupStage.S1A, s1a.stage)

        // S1B: Coolant Warmup (40°C - 55°C)
        val s1b = ToyotaYarisCommands.evaluateWarmupStatus(48f, 15f, 1400)
        assertEquals(WarmupStage.S1B, s1b.stage)

        // S2: Engine transition (55°C - 70°C)
        val s2 = ToyotaYarisCommands.evaluateWarmupStatus(65f, 18f, 1200)
        assertEquals(WarmupStage.S2, s2.stage)

        // S3: Pre-S4 verification (70°C - 73°C)
        val s3 = ToyotaYarisCommands.evaluateWarmupStatus(71.5f, 18f, 0)
        assertEquals(WarmupStage.S3, s3.stage)

        // S4: Full Hybrid Efficiency (> 73°C)
        val s4 = ToyotaYarisCommands.evaluateWarmupStatus(86f, 22f, 1500)
        assertEquals(WarmupStage.S4, s4.stage)
        assertEquals(1.0f, s4.progressPercent, 0.01f)
        assertTrue(s4.recommendations.isNotEmpty())
    }

    @Test
    fun testObdLiveStateThreeLevelState() {
        val initial = ObdLiveState()
        assertFalse(initial.hasEcuCommunication)
        assertNull(initial.ecuAlertMessage)

        val connectedWaitingEcu = initial.copy(
            isInitialized = true,
            hasEcuCommunication = false,
            ecuAlertMessage = "In attesa di comunicazione con la centralina Toyota..."
        )
        assertTrue(connectedWaitingEcu.isInitialized)
        assertFalse(connectedWaitingEcu.hasEcuCommunication)
        assertNotNull(connectedWaitingEcu.ecuAlertMessage)

        val fullyOperational = connectedWaitingEcu.copy(
            hasEcuCommunication = true,
            ecuAlertMessage = null
        )
        assertTrue(fullyOperational.isInitialized)
        assertTrue(fullyOperational.hasEcuCommunication)
        assertNull(fullyOperational.ecuAlertMessage)
    }

    @Test
    fun testMultiPidEngineParser() {
        // Standard ordered packed response: 41 0D 44 0C 1F 40 11 66
        // Speed: 0x44 = 68 km/h
        // RPM: 0x1F40 = 8000 / 4 = 2000 RPM
        // Throttle: 0x66 = 102 * 100 / 255 = 40.0%
        val rawMulti = "7E8 08 41 0D 44 0C 1F 40 11 66 >"
        val parsed = ToyotaYarisCommands.parseMultiPidEngineResponse(rawMulti)

        assertNotNull(parsed)
        assertEquals(68, parsed!!.speedKmh)
        assertEquals(2000, parsed.engineRpm)
        assertEquals(40.0f, parsed.throttlePercent!!, 0.5f)

        // Error response returns null
        val errRes = ToyotaYarisCommands.parseMultiPidEngineResponse("NO DATA")
        assertNull(errRes)
    }

    @Test
    fun testLinearInterpolationCrossing() {
        // Case 1: Crossing 50 km/h midway between 40 km/h (t0=1000) and 60 km/h (t1=1200)
        val t50 = ToyotaYarisCommands.interpolateCrossingTimeMs(
            t0Ms = 1000L,
            v0Kmh = 40.0f,
            t1Ms = 1200L,
            v1Kmh = 60.0f,
            targetKmh = 50.0f
        )
        assertEquals(1100L, t50)

        // Case 2: Crossing 100 km/h exactly at 3/4 interval: 90 -> 110 between 5000 and 5200 ms
        // (100 - 90) / (110 - 90) = 10 / 20 = 0.5 -> 5100ms
        val t100 = ToyotaYarisCommands.interpolateCrossingTimeMs(
            t0Ms = 5000L,
            v0Kmh = 90.0f,
            t1Ms = 5200L,
            v1Kmh = 110.0f,
            targetKmh = 100.0f
        )
        assertEquals(5100L, t100)

        // Case 3: Launch start crossing 0.5 km/h from standstill (0 km/h at 2000ms to 5 km/h at 2100ms)
        // 0.5 / 5.0 = 0.1 -> 2000 + 10ms = 2010ms
        val tLaunch = ToyotaYarisCommands.interpolateCrossingTimeMs(
            t0Ms = 2000L,
            v0Kmh = 0.0f,
            t1Ms = 2100L,
            v1Kmh = 5.0f,
            targetKmh = 0.5f
        )
        assertEquals(2010L, tLaunch)
    }

    @Test
    fun testExponentialBackoffAndReconnectingState() {
        // Verify exponential backoff cadence
        assertEquals(2000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(1))
        assertEquals(4000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(2))
        assertEquals(8000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(3))
        assertEquals(15000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(4))
        assertEquals(30000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(5))
        assertEquals(30000L, com.yaris.hvfan.ble.BleManager.calculateBackoffMs(10))

        // Verify Reconnecting state fields
        val reconnecting = com.yaris.hvfan.ble.BleConnectionState.Reconnecting(
            deviceName = "Android-Vlink",
            address = "AA:BB:CC:DD:EE:FF",
            attempt = 3
        )
        assertEquals("Android-Vlink", reconnecting.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", reconnecting.address)
        assertEquals(3, reconnecting.attempt)
    }

    @Test
    fun testTwoLevelConnectionStatusLogic() {
        // Test case 1: Bluetooth connected and initialized, but car ignition OFF (ECU silent)
        val stateDongleOnly = ObdLiveState(
            isInitialized = true,
            isLoopRunning = true,
            hasEcuCommunication = false
        )
        assertFalse(stateDongleOnly.hasEcuCommunication)

        val badgeTextDongleOnly = when {
            stateDongleOnly.hasEcuCommunication -> "● ECU ONLINE"
            stateDongleOnly.isInitialized -> "▲ DONGLE OK - ATTESA ECU"
            else -> "◌ LINK OBD..."
        }
        assertEquals("▲ DONGLE OK - ATTESA ECU", badgeTextDongleOnly)

        // Speed check: must be "--" when hasEcuCommunication is false
        val speedDisplayDongleOnly = if (stateDongleOnly.hasEcuCommunication) "${stateDongleOnly.accelerationState.currentSpeedKmh}" else "--"
        assertEquals("--", speedDisplayDongleOnly)

        // Battery temp check: must be "--.-°C" when hasEcuCommunication is false
        val tempDisplayDongleOnly = if (stateDongleOnly.hasEcuCommunication && stateDongleOnly.batteryStatus.maxTemp > 0.0) {
            String.format(java.util.Locale.US, "%.1f°C", stateDongleOnly.batteryStatus.maxTemp)
        } else {
            "--.-°C"
        }
        assertEquals("--.-°C", tempDisplayDongleOnly)

        // Test case 2: Car READY ON and receiving CAN frames
        val stateEcuOnline = ObdLiveState(
            isInitialized = true,
            isLoopRunning = true,
            hasEcuCommunication = true,
            batteryStatus = HvBatteryStatus(temp1 = 28.5, temp2 = 29.0, maxTemp = 29.0)
        )
        assertTrue(stateEcuOnline.hasEcuCommunication)

        val badgeTextEcuOnline = when {
            stateEcuOnline.hasEcuCommunication -> "● ECU ONLINE"
            stateEcuOnline.isInitialized -> "▲ DONGLE OK - ATTESA ECU"
            else -> "◌ LINK OBD..."
        }
        assertEquals("● ECU ONLINE", badgeTextEcuOnline)

        val tempDisplayEcuOnline = if (stateEcuOnline.hasEcuCommunication && stateEcuOnline.batteryStatus.maxTemp > 0.0) {
            String.format(java.util.Locale.US, "%.1f°C", stateEcuOnline.batteryStatus.maxTemp)
        } else {
            "--.-°C"
        }
        assertEquals("29.0°C", tempDisplayEcuOnline)
    }

    @Test
    fun testDeviceSortingPrioritizesObdAndBonded() {
        val dev1 = com.yaris.hvfan.ble.DiscoveredBleDevice(
            name = "Smart TV Samsung",
            address = "11:22:33:44:55:66",
            rssi = -40,
            isBonded = false
        )
        val dev2 = com.yaris.hvfan.ble.DiscoveredBleDevice(
            name = "Headphones Sony",
            address = "22:33:44:55:66:77",
            rssi = -50,
            isBonded = true
        )
        val dev3 = com.yaris.hvfan.ble.DiscoveredBleDevice(
            name = "Android-Vlink",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            isBonded = false
        )
        val dev4 = com.yaris.hvfan.ble.DiscoveredBleDevice(
            name = "vLinker MC+ BLE",
            address = "99:88:77:66:55:44",
            rssi = -60,
            isBonded = true
        )

        val rawList = listOf(dev1, dev2, dev3, dev4)
        val sortedList = rawList.sortedWith(
            compareByDescending<com.yaris.hvfan.ble.DiscoveredBleDevice> { dev ->
                val upper = dev.name.uppercase()
                upper.contains("VLINK") ||
                upper.contains("OBD") ||
                upper.contains("VGATE") ||
                upper.contains("ELM327") ||
                upper.contains("BAFX")
            }
            .thenByDescending { it.isBonded }
            .thenByDescending { it.rssi }
        )

        // OBD devices must come first, with bonded OBD device ranked #1
        assertEquals("vLinker MC+ BLE", sortedList[0].name)
        assertEquals("Android-Vlink", sortedList[1].name)
        assertEquals("Headphones Sony", sortedList[2].name) // Bonded non-OBD comes before non-bonded non-OBD
        assertEquals("Smart TV Samsung", sortedList[3].name)
    }

    @Test
    fun testElm327VoltageParserAndVehicleReadyDetection() {
        // 1. Clean voltage parsing
        val raw142 = "14.2V\r\n>"
        assertEquals(14.2f, Elm327Protocol.parseBatteryVoltage(raw142) ?: 0f, 0.05f)

        val raw138 = "13.8V"
        assertEquals(13.8f, Elm327Protocol.parseBatteryVoltage(raw138) ?: 0f, 0.05f)

        val raw124 = "12.4V"
        assertEquals(12.4f, Elm327Protocol.parseBatteryVoltage(raw124) ?: 0f, 0.05f)

        val rawDirty = "SEARCHING... 13.5V\r\n>"
        assertEquals(13.5f, Elm327Protocol.parseBatteryVoltage(rawDirty) ?: 0f, 0.05f)

        // Verifiche critiche per prevenire match errati su banner firmware dongle ("v1.5", "v2.2")
        val rawWithVgateBanner = "ELM327 v1.5\r\n14.2V\r\n>"
        assertEquals(14.2f, Elm327Protocol.parseBatteryVoltage(rawWithVgateBanner) ?: 0f, 0.05f)

        val rawWithStnBanner = "STN1110 v2.2\r\n13.9V\r\n>"
        assertEquals(13.9f, Elm327Protocol.parseBatteryVoltage(rawWithStnBanner) ?: 0f, 0.05f)

        // Banner orfano senza voltaggio non deve essere interpretato come 1.5V
        val rawBannerOnly = "ELM327 v1.5\r\n>"
        assertNull(Elm327Protocol.parseBatteryVoltage(rawBannerOnly))

        val rawInvalid = "NO DATA\r\n>"
        assertNull(Elm327Protocol.parseBatteryVoltage(rawInvalid))

        // 2. Toyota XP210 Hybrid READY detection (> 13.0V DC-DC converter active)
        assertTrue(Elm327Protocol.isVehicleReady(14.2f))
        assertTrue(Elm327Protocol.isVehicleReady(13.8f))
        assertTrue(Elm327Protocol.isVehicleReady(13.0f))
        assertFalse(Elm327Protocol.isVehicleReady(12.8f))
        assertFalse(Elm327Protocol.isVehicleReady(12.6f))
        assertFalse(Elm327Protocol.isVehicleReady(11.9f))
        assertFalse(Elm327Protocol.isVehicleReady(1.5f)) // Non deve essere considerato valido né ready
        assertFalse(Elm327Protocol.isVehicleReady(null))
    }

    @Test
    fun testIsoTpFlowControlAndHandshakeConstants() {
        // Vgate wake-up & warm start
        assertEquals("\r\r", Elm327Protocol.CMD_WAKE_UP)
        assertEquals("AT WS", Elm327Protocol.CMD_WARM_START)
        assertEquals("AT RV", Elm327Protocol.CMD_VOLTAGE)

        // Denso Battery ECU ISO-TP Flow Control (7E2 / 7EA)
        assertEquals("AT FC SH 7E2", ToyotaYarisCommands.CMD_FC_SH_BATTERY)
        assertEquals("AT FC SD 300000", ToyotaYarisCommands.CMD_FC_SD_CTS)
        assertEquals("AT FC SM 1", ToyotaYarisCommands.CMD_FC_SM_CUSTOM)
        assertEquals("AT FC SM 0", ToyotaYarisCommands.CMD_FC_SM_DEFAULT)

        assertEquals("AT FC SH 7E2", Elm327Protocol.CMD_FLOW_CONTROL_BATTERY_HEADER)
        assertEquals("AT FC SD 300000", Elm327Protocol.CMD_FLOW_CONTROL_BATTERY_DATA)
        assertEquals("AT FC SM 1", Elm327Protocol.CMD_FLOW_CONTROL_MODE_CUSTOM)
        assertEquals("AT FC SM 0", Elm327Protocol.CMD_FLOW_CONTROL_MODE_DEFAULT)
    }

    /**
     * Verifica che il timeout ELM interno per il multi-frame UDS 2228C1 sia stato aumentato in
     * modo conservativo (AT ST C8, ~819ms, il doppio del precedente AT ST 64/~410ms) e che resti
     * ampiamente al di sotto sia del timeout BLE di discovery (3000ms) sia di quello steady-state
     * (4000ms), cosi' da lasciare margine a retry e gestione errori lato app.
     */
    @Test
    fun testBatteryEcuIsoTpTimeoutIsConservativeAndBoundedByBleTimeouts() {
        assertEquals("AT ST C8", Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU)

        // 0xC8 = 200 decimale; formula ELM327 AT ST hh: hh x 4.096ms
        val elmTimeoutMs = 0xC8 * 4.096
        assertEquals(819.2, elmTimeoutMs, 0.1)

        // Deve restare ben al di sotto del timeout BLE di discovery (3000ms) e steady-state (4000ms)
        assertTrue(elmTimeoutMs < BatteryDiscoveryEngine.MAX_PROBE_TIMEOUT_MS)
        assertTrue(elmTimeoutMs < 4000L)

        // MAX_PROBE_TIMEOUT_MS invariato: resta ampiamente sufficiente per il nuovo timeout ELM
        assertEquals(3000L, BatteryDiscoveryEngine.MAX_PROBE_TIMEOUT_MS)
    }

    @Test
    fun testStandbyModeStateAndBadgeDisplay() {
        // Vehicle not READY (Standby low-power mode)
        val standbyState = ObdLiveState(
            isInitialized = true,
            isLoopRunning = true,
            hasEcuCommunication = false,
            isVehicleReady = false,
            isStandbyMode = true,
            auxiliary12vVoltage = 12.2f,
            ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
        )
        assertTrue(standbyState.isStandbyMode)
        assertFalse(standbyState.isVehicleReady)
        assertFalse(standbyState.hasEcuCommunication)
        assertEquals(12.2f, standbyState.auxiliary12vVoltage, 0.01f)
        assertNotNull(standbyState.ecuAlertMessage)

        // Vehicle enters READY mode (> 13.0V) and receives CAN frames
        val readyState = standbyState.copy(
            isVehicleReady = true,
            isStandbyMode = false,
            hasEcuCommunication = true,
            auxiliary12vVoltage = 14.1f,
            ecuAlertMessage = null
        )
        assertFalse(readyState.isStandbyMode)
        assertTrue(readyState.isVehicleReady)
        assertTrue(readyState.hasEcuCommunication)
        assertEquals(14.1f, readyState.auxiliary12vVoltage, 0.01f)
        assertNull(readyState.ecuAlertMessage)
    }

    @Test
    fun testStnHardwareNonDestructiveDetection() {
        // Case A: Vlinker devices must NEVER enable custom AT FC (prevents buffer corruption)
        assertFalse(Elm327Protocol.isStnHardwareSupported("Android-Vlink", "ELM327 v2.2", "STN2120 v5.1.0"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("vLinker MC+ BLE", "vLinker MC v2.2", "STN2120"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("vLinker FD", "ELM327 v2.2", "STN2120 v5.2.0"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("V-LINK", "ELM327 v1.5", "?"))

        // Case B: Standard ELM327 clone returning '?' or ERROR on ST DI
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDII", "ELM327 v1.5", "?"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("ELM327 Bluetooth", "ELM327 v2.1", "ERROR"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("Konnwei OBD", "ELM327 v1.5", "NO DATA"))

        // Case C: Genuine Scantool OBDLink original hardware
        assertTrue(Elm327Protocol.isStnHardwareSupported("OBDLink MX+", "OBDLink MX+ v4.5.1", "STN1151 v4.5.1"))
        assertTrue(Elm327Protocol.isStnHardwareSupported("OBDLink LX", "OBDLink LX v4.3.0", "STN1130 v4.3.0"))
        assertTrue(Elm327Protocol.isStnHardwareSupported("OBDLink CX", "OBDLink CX v5.6.1", "STN2230 v5.6.1"))

        // Case D: Generic adapter with genuine STN chipset (and not Vlinker)
        assertTrue(Elm327Protocol.isStnHardwareSupported("ScanTool Device", "STN1110 v3.3.1", "STN1110 v3.3.1"))
        assertTrue(Elm327Protocol.isStnHardwareSupported("Custom OBD", "STN2120 v5.0.0", "STN2120 v5.0.0"))
    }

    @Test
    fun testTwoStageHandshakePidsAndFallbackChain() {
        // Stage 1: Engine ECU quick bus lock PID
        assertEquals("0100", ToyotaYarisCommands.PID_SUPPORTED_PIDS)
        assertEquals("010C", ToyotaYarisCommands.PID_ENGINE_RPM)
        assertEquals("7E0", ToyotaYarisCommands.HEADER_ENGINE_ECU)
        assertEquals("7E8", ToyotaYarisCommands.FILTER_ENGINE_ECU)

        // Stage 2: Battery ECU and fallback chain
        assertEquals("7E2", ToyotaYarisCommands.HEADER_BATTERY_ECU)
        assertEquals("7EA", ToyotaYarisCommands.FILTER_BATTERY_ECU)
        assertEquals(6, ToyotaYarisCommands.BATTERY_FALLBACK_PIDS.size)
        assertEquals("2228C1", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[0]) // Primary TNGA Mode 22
        assertEquals("2228C0", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[1]) // Alternative Mode 22
        assertEquals("220101", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[2]) // Mode 22 UDS 0101
        assertEquals("2101", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[3])   // Mode 21 Local ID 01
        assertEquals("21C3", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[4])   // Lithium Mode 21
        assertEquals("2161", ToyotaYarisCommands.BATTERY_FALLBACK_PIDS[5])   // Legacy KWP Mode 21

        // Verify parsing for each fallback response variant
        // 1. Primary 2228C1 -> 6228C1
        val res2228C1 = "7EA 10 0E 62 28 C1 42 44 43 41 3E 05 >" // T1=26, T2=28, T3=27, T4=25, Intake=22, Fan=5
        val b1 = ToyotaYarisCommands.parseBatteryResponse(res2228C1, false)
        assertNotNull(b1)
        assertEquals(26.0, b1!!.temp1, 0.1)
        assertEquals(28.0, b1.temp2, 0.1)
        assertEquals(5, b1.fanSpeedLevel)

        // 2. Alternative 2228C0 -> 6228C0
        val res2228C0 = "7EA 10 0E 62 28 C0 43 43 42 40 3F 02 >" // T1=27, T2=27, T3=26, T4=24, Intake=23, Fan=2
        val b2 = ToyotaYarisCommands.parseBatteryResponse(res2228C0, false)
        assertNotNull(b2)
        assertEquals(27.0, b2!!.temp1, 0.1)
        assertEquals(2, b2.fanSpeedLevel)

        // 3. Mode 22 UDS 220101 -> 620101
        val res220101 = "7EA 10 0E 62 01 01 42 43 42 41 3E 04 >" // T1=26, T2=27, T3=26, T4=25, Intake=22, Fan=4
        val b3Uds = ToyotaYarisCommands.parseBatteryResponse(res220101, false)
        assertNotNull(b3Uds)
        assertEquals(26.0, b3Uds!!.temp1, 0.1)
        assertEquals(4, b3Uds.fanSpeedLevel)

        // 4. Mode 21 Local ID 2101 -> 6101
        val res2101 = "7EA 08 61 01 41 42 41 40 3D 03 >" // T1=25, T2=26, T3=25, T4=24, Intake=21, Fan=3
        val b4Kwp = ToyotaYarisCommands.parseBatteryResponse(res2101, false)
        assertNotNull(b4Kwp)
        assertEquals(25.0, b4Kwp!!.temp1, 0.1)
        assertEquals(3, b4Kwp.fanSpeedLevel)

        // 5. Lithium Pack 21C3 -> 61C3
        val res21C3 = "7EA 08 61 C3 41 42 41 40 3D 00 >" // T1=25, T2=26, T3=25, T4=24, Intake=21, Fan=0
        val b3 = ToyotaYarisCommands.parseBatteryResponse(res21C3, false)
        assertNotNull(b3)
        assertEquals(25.0, b3!!.temp1, 0.1)
        assertEquals(26.0, b3.temp2, 0.1)
        assertEquals(0, b3.fanSpeedLevel)

        // 6. Legacy KWP 2161 -> 6161
        val res2161 = "7EA 08 61 61 40 40 3F 3E 3C 01 >" // T1=24, T2=24, T3=23, T4=22, Intake=20, Fan=1
        val b4 = ToyotaYarisCommands.parseBatteryResponse(res2161, false)
        assertNotNull(b4)
        assertEquals(24.0, b4!!.temp1, 0.1)
        assertEquals(1, b4.fanSpeedLevel)
    }

    @Test
    fun testDrPriusUniversalBaseStackCommands() {
        assertEquals("\r\r", Elm327Protocol.CMD_WAKE_UP)
        assertEquals("AT Z", Elm327Protocol.CMD_RESET)
        assertEquals("ATI", Elm327Protocol.CMD_DEVICE_INFO)
        assertEquals("ST DI", Elm327Protocol.CMD_DEVICE_ID_STN)

        // Must start with AT Z and conclude with the wide handshake timeout
        assertEquals("AT Z", Elm327Protocol.INIT_COMMANDS.first())
        assertEquals("AT ST 96", Elm327Protocol.INIT_COMMANDS.last())

        // Must not contain AT D (which would reset parameters)
        assertFalse(Elm327Protocol.INIT_COMMANDS.contains("AT D"))

        // Must contain all core Dr. Prius commands
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT E0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT L0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT S0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT H0"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AT 1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT SP 6"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT CAF 1"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AR"))
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT ST 96"))

        // Exact Dr. Prius base stack order: protocollo prima del timing, filtro ricezione azzerato
        assertEquals(
            listOf(
                "AT Z", "AT E0", "AT L0", "AT S0", "AT H0", "AT AT 1",
                "AT SP 6", "AT CAF 1", "AT AR", "AT ST 96"
            ),
            Elm327Protocol.INIT_COMMANDS
        )
    }

    @Test
    fun testVoltageParsingWithComplexFirmwareBanners() {
        // Banner with high version number like v12.1 must not be parsed as 12.1V
        val rawStnVersionHigh = "STN2120 v12.1\r\n14.3V\r\n>"
        assertEquals(14.3f, Elm327Protocol.parseBatteryVoltage(rawStnVersionHigh) ?: 0f, 0.05f)

        val rawVlinkerBanner = "vLinker FD v2.2\r\n13.7V\r\n>"
        assertEquals(13.7f, Elm327Protocol.parseBatteryVoltage(rawVlinkerBanner) ?: 0f, 0.05f)

        val rawObdlinkBanner = "OBDLink MX+ v5.6.1\r\n14.4V\r\n>"
        assertEquals(14.4f, Elm327Protocol.parseBatteryVoltage(rawObdlinkBanner) ?: 0f, 0.05f)

        // Bare decimal voltage without 'V'
        val rawBareVoltage = "12.4\r\n>"
        assertEquals(12.4f, Elm327Protocol.parseBatteryVoltage(rawBareVoltage) ?: 0f, 0.05f)

        // Bare banner without voltage must return null
        val rawBareBanner = "vLinker MC v2.2\r\n>"
        assertNull(Elm327Protocol.parseBatteryVoltage(rawBareBanner))

        // Integer voltages with 'V'
        val rawIntVoltage = "14V\r\n>"
        assertEquals(14.0f, Elm327Protocol.parseBatteryVoltage(rawIntVoltage) ?: 0f, 0.05f)

        // Prefixed with V or VOLT
        val rawPrefixedVolt = "VOLT 13.8V\r\n>"
        assertEquals(13.8f, Elm327Protocol.parseBatteryVoltage(rawPrefixedVolt) ?: 0f, 0.05f)
    }

    @Test
    fun testStnHardwareDetectionRejectsFakeClonesWithDirtyResponses() {
        // Counterfeit OBDLink clones with deceptive Bluetooth name but no STN chip
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink Clone", "ELM327 v1.5", "OK"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink SX Fake", "ELM327 v2.1", "ST DI"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink BT", "ELM327 v1.5", "ERR01"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink LX Clone", "ELM327 v1.5", "SYNTAX ERROR"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink MX Counterfeit", "ELM327 v1.5", "COMMAND NOT UNDERSTOOD"))

        // Standard ELM327 clones with dirty responses
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDII", "ELM327 v1.5", "ERR01"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDII", "ELM327 v1.5", "SYNTAX ERROR"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDII", "ELM327 v1.5", "ACT ALERT"))
    }

    @Test
    fun testBatteryResponseRejectsMismatchedOrEngineFrames() {
        // Mode 01 PID 00 engine frame must NOT be parsed as battery data
        val engineFrame = "7E8 06 41 00 BE 7F A8 11 >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(engineFrame, false))

        // Mode 01 PID 0C engine RPM frame must NOT be parsed as battery data
        val rpmFrame = "7E8 04 41 0C 1F 40 >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(rpmFrame, false))

        // Random non-battery payload must NOT be parsed as battery data
        val randomPayload = "7EA 08 DE AD BE EF 00 11 >"
        assertNull(ToyotaYarisCommands.parseBatteryResponse(randomPayload, false))
    }

    @Test
    fun testReadySyncAndStandbyStateMachineTransitions() {
        // 1. Initial State: Standby (12V < 13.0V)
        val state1 = ObdLiveState(
            isInitialized = true,
            isLoopRunning = true,
            hasEcuCommunication = false,
            isVehicleReady = false,
            isStandbyMode = true,
            auxiliary12vVoltage = 12.2f,
            ecuAlertMessage = "Auto in standby a basso consumo: accendi la vettura (spia verde READY) per avviare la telemetria."
        )
        assertTrue(state1.isStandbyMode)
        assertFalse(state1.isVehicleReady)
        assertFalse(state1.hasEcuCommunication)

        // 2. Transition: Car in READY (14.2V), CAN handshake in progress
        val state2 = state1.copy(
            isVehicleReady = true,
            isStandbyMode = false,
            hasEcuCommunication = false,
            auxiliary12vVoltage = 14.2f,
            ecuAlertMessage = "Veicolo in stato READY (12V: 14.2V). Sincronizzazione con ECU Toyota in corso..."
        )
        assertFalse(state2.isStandbyMode)
        assertTrue(state2.isVehicleReady)
        assertFalse(state2.hasEcuCommunication)
        assertTrue(state2.ecuAlertMessage!!.contains("READY"))

        // 3. Complete: CAN confirmed, ONLINE
        val state3 = state2.copy(
            hasEcuCommunication = true,
            ecuAlertMessage = null
        )
        assertFalse(state3.isStandbyMode)
        assertTrue(state3.isVehicleReady)
        assertTrue(state3.hasEcuCommunication)
        assertNull(state3.ecuAlertMessage)
    }

    @Test
    fun testIsoTpMultiFrameWithLineSequenceNumbers() {
        // Standard ELM327 with AT CAF 1 formats multi-frame responses with line sequence numbers '0:', '1:', etc.
        val multiLine2228C1 = """
            0: 62 28 C1 44 45 44
            1: 43 41 03 00 00 00
            >
        """.trimIndent()

        val parsed = ToyotaYarisCommands.parseBatteryResponse(multiLine2228C1, false)
        assertNotNull(parsed)
        assertEquals(28.0, parsed!!.temp1, 0.1) // 0x44 = 68 - 40 = 28
        assertEquals(29.0, parsed.temp2, 0.1)  // 0x45 = 69 - 40 = 29
        assertEquals(28.0, parsed.temp3, 0.1)  // 0x44 = 68 - 40 = 28
        assertEquals(27.0, parsed.temp4, 0.1)  // 0x43 = 67 - 40 = 27
        assertEquals(25.0, parsed.intakeTemp, 0.1) // 0x41 = 65 - 40 = 25
        assertEquals(3, parsed.fanSpeedLevel)   // 0x03 = 3

        // 3-frame battery response
        val multiLine3Frames = """
            0: 62 28 C0 42 44 43
            1: 41 3E 05 00 00 00
            2: 00 00 00 00 00 00
            >
        """.trimIndent()
        val parsed3 = ToyotaYarisCommands.parseBatteryResponse(multiLine3Frames, false)
        assertNotNull(parsed3)
        assertEquals(26.0, parsed3!!.temp1, 0.1)
        assertEquals(28.0, parsed3.temp2, 0.1)
        assertEquals(5, parsed3.fanSpeedLevel)

        // Multi-line Multi-PID engine response with sequence numbers
        val multiLineMultiPid = """
            0: 41 0D 44 0C 1F
            1: 40 11 66 >
        """.trimIndent()
        val parsedMulti = ToyotaYarisCommands.parseMultiPidEngineResponse(multiLineMultiPid)
        assertNotNull(parsedMulti)
        assertEquals(68, parsedMulti!!.speedKmh)
        assertEquals(2000, parsedMulti.engineRpm)
        assertEquals(40.0f, parsedMulti.throttlePercent!!, 0.5f)
    }

    @Test
    fun testStnHardwareDetectionRejectsCounterfeitClonesWithSpoofedAti() {
        // Clones spoofing ATI as OBDLink but returning non-STN responses to ST DI must be rejected
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink MX+", "OBDLink MX+ v4.5.1", "COMMAND NOT UNDERSTOOD"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink MX+", "OBDLink MX+ v4.5.1", "ACT ALERT"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink LX", "OBDLink LX v4.3.0", "OK"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink BT", "OBDLink BT", "ST DI"))
        assertFalse(Elm327Protocol.isStnHardwareSupported("OBDLink MX", "ELM327 v1.5", "NO DATA"))

        // Genuine Scantool STN / OBDLink hardware returning valid ST DI
        assertTrue(Elm327Protocol.isStnHardwareSupported("OBDLink MX+", "OBDLink MX+ v4.5.1", "STN1151 v4.5.1"))
        assertTrue(Elm327Protocol.isStnHardwareSupported("OBDLink CX", "OBDLink CX v5.6.1", "STN2230 v5.6.1"))
        assertTrue(Elm327Protocol.isStnHardwareSupported("ScanTool Device", "STN1110 v3.3.1", "STN1110 v3.3.1"))
    }

    @Test
    fun testConnectionBadgeVehicleReadyDisplayLogic() {
        // 1. Vehicle READY with 14.2V and CAN sync in progress
        val isReady = true
        val hasCan = false
        val volt = 14.2f

        val badgeText = if (hasCan) {
            "● READY ONLINE (${String.format(java.util.Locale.US, "%.1f", volt)}V)"
        } else if (isReady || volt >= 13.0f) {
            if (volt > 0f) "◌ SINCRONIZZAZIONE (${String.format(java.util.Locale.US, "%.1f", volt)}V)" else "◌ SINCRONIZZAZIONE"
        } else {
            "▲ DONGLE OK - ATTESA ECU"
        }
        assertEquals("◌ SINCRONIZZAZIONE (14.2V)", badgeText)

        // 2. Vehicle READY with 0.0V (unparseable or noise) and CAN sync in progress
        val badgeTextZeroVolt = if (hasCan) {
            "● READY ONLINE"
        } else if (isReady || 0.0f >= 13.0f) {
            if (0.0f > 0f) "◌ SINCRONIZZAZIONE (0.0V)" else "◌ SINCRONIZZAZIONE"
        } else {
            "▲ DONGLE OK - ATTESA ECU"
        }
        assertEquals("◌ SINCRONIZZAZIONE", badgeTextZeroVolt)

        // 3. Online with CAN confirmed
        val badgeTextOnline = if (true) {
            "● READY ONLINE (${String.format(java.util.Locale.US, "%.1f", volt)}V)"
        } else {
            ""
        }
        assertEquals("● READY ONLINE (14.2V)", badgeTextOnline)
    }

    @Test
    fun testElm327ProtocolAdvancedErrorAndStatusDetection() {
        // Standalone SEARCHING or SEARCHING... without response must be treated as error/in-progress
        assertTrue(Elm327Protocol.isError("SEARCHING..."))
        assertTrue(Elm327Protocol.isError("SEARCHING...\r\n>"))
        assertTrue(Elm327Protocol.isError("SEARCHING"))

        // BUT genuine responses with SEARCHING header followed by Mode 41 data must NOT be flagged as error
        val searchWithData = "SEARCHING...\r\n41 00 BE 7F B8 11 >"
        assertFalse(Elm327Protocol.isError(searchWithData))
        val cleaned = Elm327Protocol.cleanResponse(searchWithData)
        assertTrue(cleaned.contains("4100"))

        // Dongle error and bus halt conditions
        assertTrue(Elm327Protocol.isError("STOPPED"))
        assertTrue(Elm327Protocol.isError("BUS BUSY"))
        assertTrue(Elm327Protocol.isError("BUS ERROR"))
        assertTrue(Elm327Protocol.isError("BUS INIT: ERROR"))
        assertTrue(Elm327Protocol.isError("BUS INIT ERROR"))
        assertTrue(Elm327Protocol.isError("CAN ERROR"))
        assertTrue(Elm327Protocol.isError("FB ERROR"))
    }

    @Test
    fun testStage1CanHandshakeRejectsNoiseAndRequiresPositiveMode41() {
        // Helper function simulating Stage 1 validation in ObdController
        fun isValidStage1(rawResponse: String): Boolean {
            val clean = Elm327Protocol.cleanResponse(rawResponse)
            return !Elm327Protocol.isError(clean) &&
                   (clean.contains("4100") || clean.contains("410C"))
        }

        // 1. Positive standard Mode 01 PID 00 response
        assertTrue(isValidStage1("41 00 BE 7F A8 11 >"))
        assertTrue(isValidStage1("7E8 06 41 00 BE 7F A8 11 >"))
        assertTrue(isValidStage1("SEARCHING...\r\n41 00 BE 7F B8 11 >"))

        // 2. Positive standard Mode 01 PID 0C response
        assertTrue(isValidStage1("41 0C 1F 40 >"))
        assertTrue(isValidStage1("7E8 04 41 0C 1F 40 >"))

        // 3. Noise / Status / Incomplete frames of length >= 6 must be REJECTED
        assertFalse(isValidStage1("SEARCHING..."))
        assertFalse(isValidStage1("STOPPED"))
        assertFalse(isValidStage1("BUS BUSY"))
        assertFalse(isValidStage1("NO DATA"))
        assertFalse(isValidStage1("UNABLE TO CONNECT"))
        assertFalse(isValidStage1("7EA 08 DE AD BE EF 00 11 >")) // Battery or arbitrary payload (no 4100/410C)
        assertFalse(isValidStage1("123456")) // Arbitrary hex
    }

    @Test
    fun testVehicleReadyAndStandbyThresholdStrictness() {
        // R3: Standby mode when not in READY (< 13.0V), active when >= 13.0V
        assertFalse(Elm327Protocol.isVehicleReady(0.0f))
        assertFalse(Elm327Protocol.isVehicleReady(12.2f))
        assertFalse(Elm327Protocol.isVehicleReady(12.79f))
        assertFalse(Elm327Protocol.isVehicleReady(12.80f))
        assertFalse(Elm327Protocol.isVehicleReady(12.90f))
        assertFalse(Elm327Protocol.isVehicleReady(12.99f))

        assertTrue(Elm327Protocol.isVehicleReady(13.0f))
        assertTrue(Elm327Protocol.isVehicleReady(13.8f))
        assertTrue(Elm327Protocol.isVehicleReady(14.2f))
        assertTrue(Elm327Protocol.isVehicleReady(14.5f))
    }
}
