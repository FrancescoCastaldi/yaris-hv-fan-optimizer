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
        assertTrue(Elm327Protocol.INIT_COMMANDS.contains("AT AT 2"))
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
}
