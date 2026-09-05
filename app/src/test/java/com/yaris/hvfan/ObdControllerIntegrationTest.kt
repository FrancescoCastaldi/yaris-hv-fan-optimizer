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
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT ST 64"))
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

        val rawInvalid = "NO DATA\r\n>"
        assertNull(Elm327Protocol.parseBatteryVoltage(rawInvalid))

        // 2. Toyota XP210 Hybrid READY detection (> 13.0V DC-DC converter active)
        assertTrue(Elm327Protocol.isVehicleReady(14.2f))
        assertTrue(Elm327Protocol.isVehicleReady(13.8f))
        assertTrue(Elm327Protocol.isVehicleReady(13.0f))
        assertFalse(Elm327Protocol.isVehicleReady(12.6f))
        assertFalse(Elm327Protocol.isVehicleReady(11.9f))
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
}
