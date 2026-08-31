package com.yaris.hvfan.obd

import android.util.Log

enum class WarmupStage(
    val title: String,
    val subtitle: String,
    val targetTempDescription: String
) {
    S0("S0 - Motore Freddo", "Quadro acceso, termico non ancora avviato", "ECT < 40°C"),
    S1A("S1a - Riscaldamento Catalizzatore", "Accensione ritardata per riscaldare il catalizzatore (EV bloccato)", "Cat < 400°C"),
    S1B("S1b - Warm-up Liquido & Monoblocco", "Riscaldamento motore e circuito riscaldamento", "ECT 40°C - 55°C"),
    S2("S2 - Transizione Efficienza", "Il motore può spegnersi a veicolo fermo", "ECT 55°C - 70°C"),
    S3("S3 - Preriscaldamento Completo", "Fase di verifica regime termico per sblocco S4", "ECT 70°C - 73°C"),
    S4("S4 - Piena Efficienza Ibrida", "Atkinson puro, veleggiamento EV al 100% e massimo risparmio", "ECT > 73°C")
}

data class HybridWarmupStatus(
    val stage: WarmupStage = WarmupStage.S0,
    val coolantTemp: Float = 0f,
    val ambientTemp: Float = 0f,
    val catalystTemp: Float = 0f,
    val engineRpm: Int = 0,
    val progressPercent: Float = 0f,
    val hasLiveData: Boolean = false,
    val recommendations: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class AccelerationRunState(
    val currentSpeedKmh: Int = 0,
    val isLaunchReady: Boolean = false,
    val isTimingActive: Boolean = false,
    val elapsedMs: Long = 0L,
    val last0to50TimeSec: Float? = null,
    val last0to100TimeSec: Float? = null,
    val best0to50TimeSec: Float? = null,
    val best0to100TimeSec: Float? = null,
    val lastRunCompleted: Boolean = false
)

data class EnginePerformanceStatus(
    val timingAdvance: Float = 0f,
    val engineLoadPercent: Float = 0f,
    val throttlePercent: Float = 0f,
    val isOptimalAdvance: Boolean = false,
    val isHighPowerReady: Boolean = true,
    val hasLiveData: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class HvBatteryStatus(
    val temp1: Double = 0.0,
    val temp2: Double = 0.0,
    val temp3: Double = 0.0,
    val temp4: Double = 0.0,
    val maxTemp: Double = 0.0,
    val avgTemp: Double = 0.0,
    val intakeTemp: Double = 0.0,
    val fanSpeedLevel: Int = 0, // 0 to 6
    val isFanForced: Boolean = false,
    val isThermalThrottled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)



enum class Touch3OpeningScreen(val label: String, val code: String) {
    GAZOO_RACING("🏁 Toyota Gazoo Racing (GR)", "01"),
    HYBRID_SYNERGY("⚡ Hybrid Synergy Drive", "02"),
    STANDARD_TOYOTA("Toyota Standard", "00")
}

enum class AslVolumeMode(val label: String, val code: String) {
    OFF("Disattivato", "00"),
    LOW("Basso", "01"),
    MID("Medio (Consigliato)", "02"),
    HIGH("Alto (Autostrada)", "03")
}

enum class DigitalClusterTheme(val label: String, val code: String) {
    SPORT_GR("Sport / Gazoo Racing (Rosso & kW)", "01"),
    SMART("Smart (Minimal Ciano)", "02"),
    CASUAL("Casual (Standard Toyota)", "03"),
    TOUGH("Tough (Dinamico)", "04")
}

enum class RsaSpeedBeepMode(val label: String, val code: String) {
    MUTE("Muto / Solo Visivo (Consigliato)", "00"),
    LOW("Bip Basso", "01"),
    STANDARD("Bip Standard (Normativa ISA)", "02")
}

enum class CameraOffDelay(val label: String, val code: String) {
    IMMEDIATE("Spegnimento Immediato", "00"),
    SEC_5("Ritardo 5s in manovra D (Comodo)", "01"),
    SEC_10("Ritardo 10s", "02")
}

enum class ReverseBeepMode(val label: String, val code: String) {
    CONTINUOUS("Bip Continuo (Standard)", "01"),
    SINGLE("Singolo Bip (Comfort Silenzioso)", "00")
}

enum class KeylessBuzzerVolume(val label: String, val code: String) {
    MUTE("Muto / Disattivato", "00"),
    LOW("Basso (Volume 2)", "02"),
    MEDIUM("Medio (Volume 4)", "04"),
    HIGH("Alto (Volume 6)", "06")
}

enum class AutoRelockTime(val label: String, val seconds: Int, val code: String) {
    SEC_30("30 Secondi", 30, "01"),
    SEC_60("60 Secondi", 60, "02"),
    SEC_120("120 Secondi", 120, "03")
}

enum class DoorUnlockMode(val label: String, val code: String) {
    ALL_DOORS("Tutte le porte (1 tocco)", "00"),
    DRIVER_FIRST("Solo guida (2 tocchi tutte)", "01")
}

enum class TurnSignalFlashes(val label: String, val flashes: Int, val code: String) {
    FLASHES_3("3 Lampeggi (Standard)", 3, "03"),
    FLASHES_4("4 Lampeggi", 4, "04"),
    FLASHES_5("5 Lampeggi (Consigliato)", 5, "05"),
    FLASHES_6("6 Lampeggi", 6, "06"),
    OFF("Disattivato (Solo manuale)", 0, "00")
}

enum class AutoDoorLockMode(val label: String, val code: String) {
    OFF("Disattivato", "00"),
    BY_SPEED("Chiusura a 20 km/h (Speed Lock)", "01"),
    BY_SHIFT_D("Chiusura marcia D (Shift from P)", "02")
}

enum class LightSensitivity(val label: String, val code: String) {
    DARK_2("Molto Scuro (-2)", "01"),
    DARK_1("Scuro (-1)", "02"),
    NORMAL("Normale (Standard)", "03"),
    LIGHT_1("Luminoso (+1)", "04"),
    LIGHT_2("Molto Luminoso (+2)", "05")
}

enum class FollowMeHomeDuration(val label: String, val seconds: Int, val code: String) {
    OFF("Disattivato", 0, "00"),
    SEC_30("30 Secondi", 30, "01"),
    SEC_60("60 Secondi", 60, "02"),
    SEC_90("90 Secondi", 90, "03")
}

enum class InteriorLightDimTime(val label: String, val code: String) {
    SEC_7_5("7.5 Secondi", "01"),
    SEC_15("15 Secondi (Standard)", "02"),
    SEC_30("30 Secondi", "03")
}

enum class LdaWarningVolume(val label: String, val code: String) {
    LOW("Basso", "01"),
    MEDIUM("Medio (Standard)", "02"),
    HIGH("Alto", "03")
}

enum class BsmSensitivity(val label: String, val code: String) {
    NEAR("Vicino", "01"),
    NORMAL("Normale (Standard)", "02"),
    FAR("Lontano / Anticipato", "03")
}

data class EcuCustomizationState(
    // 0. Toyota Touch 3 & Smart Connect (Infotainment)
    val touch3OpeningAnimation: Touch3OpeningScreen = Touch3OpeningScreen.GAZOO_RACING,
    val aslVolumeMode: AslVolumeMode = AslVolumeMode.MID,
    val clusterTheme: DigitalClusterTheme = DigitalClusterTheme.SPORT_GR,
    val rsaSpeedLimitBeep: RsaSpeedBeepMode = RsaSpeedBeepMode.MUTE,
    val touchScreenBeep: Boolean = false,
    val rearCameraDelay: CameraOffDelay = CameraOffDelay.SEC_5,
    val micGainDb: Int = 2,

    // 1. Comfort & Cicalini di Bordo
    val reverseBeep: ReverseBeepMode = ReverseBeepMode.SINGLE,
    val driverSeatbeltBeep: Boolean = true,
    val passengerSeatbeltBeep: Boolean = true,
    val rearSeatbeltBeep: Boolean = true,

    // 2. Smart Key & Chiusura Porte
    val keylessBuzzerVolume: KeylessBuzzerVolume = KeylessBuzzerVolume.MEDIUM,
    val autoRelockTime: AutoRelockTime = AutoRelockTime.SEC_60,
    val doorUnlockMode: DoorUnlockMode = DoorUnlockMode.ALL_DOORS,
    val windowsWithKeyFob: Boolean = true,
    val autoDoorLock: AutoDoorLockMode = AutoDoorLockMode.BY_SPEED,
    val autoDoorUnlock: Boolean = true,

    // 3. Tergicristalli & Sensore Pioggia
    val rearWiperReverseLink: Boolean = true,
    val dripWipeExtraPass: Boolean = true,
    val wiperSpeedLink: Boolean = true,

    // 4. Luci, Frecce & Plafoniera
    val turnSignalFlashes: TurnSignalFlashes = TurnSignalFlashes.FLASHES_5,
    val interiorDimTime: InteriorLightDimTime = InteriorLightDimTime.SEC_15,
    val footwellLightingInDrive: Boolean = true,
    val lightSensitivity: LightSensitivity = LightSensitivity.NORMAL,
    val followMeHome: FollowMeHomeDuration = FollowMeHomeDuration.SEC_30,

    // 5. ADAS & TSS 2.5
    val ldaWarningVolume: LdaWarningVolume = LdaWarningVolume.MEDIUM,
    val bsmSensitivity: BsmSensitivity = BsmSensitivity.NORMAL,

    // 6. Clima & Efficienza Eco
    val autoAcWithAutoButton: Boolean = false,
    val ecoAirConEfficiencyMode: Boolean = true,

    // State Tracking
    val isReadCompleted: Boolean = false,
    val isWriting: Boolean = false,
    val lastOperationStatus: String = "Pronto per la lettura"
)

object ToyotaYarisCommands {
    private const val TAG = "ToyotaYarisCommands"

    // CAN Headers & Filter IDs
    const val HEADER_BATTERY_ECU = "7E2"
    const val FILTER_BATTERY_ECU = "7EA"
    const val CRA_BATTERY_ECU = "7EA"

    const val HEADER_ENGINE_ECU  = "7E0"
    const val FILTER_ENGINE_ECU  = "7E8"
    const val CRA_ENGINE_ECU  = "7E8"

    const val HEADER_BODY_ECU = "750"        // Main Body / Gateway ECU
    const val CRA_BODY_ECU = "758"
    const val HEADER_METER_ECU = "7C0"       // Combination Meter ECU
    const val CRA_METER_ECU = "7C8"
    const val HEADER_AIRCON_ECU = "7C4"      // Air Conditioning ECU
    const val CRA_AIRCON_ECU = "7CC"
    const val HEADER_ADAS_ECU = "7A0"        // TSS 2.5 Driving Assist / EPS
    const val CRA_ADAS_ECU = "7A8"

    const val CMD_SET_HEADER_BATTERY_ECU = "AT SH 7E2"  // Toyota HV Battery Management ECU
    const val CMD_SET_HEADER_ENGINE_ECU  = "AT SH 7E0"  // Toyota Engine / Hybrid Main ECU
    const val CMD_SET_RECEIVE_FILTER     = "AT CRA 7EA" // Filter for Battery ECU responses

    // Standard OBD-II PIDs (Mode 01 for Engine & Atmosphere)
    const val PID_VEHICLE_SPEED    = "010D" // Formula: A (km/h)
    const val PID_COOLANT_TEMP     = "0105" // Formula: A - 40 (°C)
    const val PID_INTAKE_AIR_TEMP  = "010F" // Formula: A - 40 (°C)
    const val PID_ENGINE_RPM       = "010C" // Formula: ((A*256)+B)/4
    const val PID_CATALYST_TEMP    = "013C" // Formula: ((A*256)+B)/10 - 40 (°C)
    const val PID_TIMING_ADVANCE   = "010E" // Formula: (A / 2.0) - 64 (°BTDC)
    const val PID_ENGINE_LOAD      = "0104" // Formula: (A * 100) / 255 (%)
    const val PID_THROTTLE_POS     = "0111" // Formula: (A * 100) / 255 (%)

    // Toyota Enhanced PID (Mode 22 UDS / Mode 21 KWP)
    const val PID_READ_BATTERY_DATA_TNGA = "2228C1"
    const val PID_READ_BATTERY_DATA_LEGACY = "2161"

    // Active Test / IO Control: Set Battery Cooling Fan to Level 6 (MAX)
    const val CMD_FAN_MAX_SPEED_UDS = "300806"       // Mode 30 IO Control (Fan Level 6)
    const val CMD_FAN_MAX_SPEED_ALT = "2F580306"     // Mode 2F IO Control Short Term Adjustment to 6
    const val CMD_TESTER_PRESENT     = "3E00"         // Tester Present keep-alive

    fun parseVehicleSpeed(raw: String): Int? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("410D")) {
            val idx = clean.indexOf("410D") + 4
            if (clean.length >= idx + 2) {
                return clean.substring(idx, idx + 2).toInt(16)
            }
        }
        return null
    }

    fun parseCoolantTemp(raw: String): Float? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("4105")) {
            val idx = clean.indexOf("4105") + 4
            if (clean.length >= idx + 2) {
                return (clean.substring(idx, idx + 2).toInt(16) - 40).toFloat()
            }
        }
        return null
    }

    fun parseIntakeAirTemp(raw: String): Float? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("410F")) {
            val idx = clean.indexOf("410F") + 4
            if (clean.length >= idx + 2) {
                return (clean.substring(idx, idx + 2).toInt(16) - 40).toFloat()
            }
        }
        return null
    }

    fun parseEngineRpm(raw: String): Int? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("410C")) {
            val idx = clean.indexOf("410C") + 4
            if (clean.length >= idx + 4) {
                val a = clean.substring(idx, idx + 2).toInt(16)
                val b = clean.substring(idx + 2, idx + 4).toInt(16)
                return ((a * 256) + b) / 4
            }
        }
        return null
    }

    fun parseTimingAdvance(raw: String): Float? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("410E")) {
            val idx = clean.indexOf("410E") + 4
            if (clean.length >= idx + 2) {
                val a = clean.substring(idx, idx + 2).toInt(16)
                return ((a / 2.0f) - 64.0f)
            }
        }
        return null
    }

    fun parseEngineLoad(raw: String): Float? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("4104")) {
            val idx = clean.indexOf("4104") + 4
            if (clean.length >= idx + 2) {
                val a = clean.substring(idx, idx + 2).toInt(16)
                return (a * 100.0f) / 255.0f
            }
        }
        return null
    }

    fun parseThrottlePos(raw: String): Float? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (clean.contains("4111")) {
            val idx = clean.indexOf("4111") + 4
            if (clean.length >= idx + 2) {
                val a = clean.substring(idx, idx + 2).toInt(16)
                return (a * 100.0f) / 255.0f
            }
        }
        return null
    }

    fun evaluateWarmupStatus(
        coolantTemp: Float,
        ambientTemp: Float,
        rpm: Int
    ): HybridWarmupStatus {
        val stage: WarmupStage
        val progress: Float
        val tips = mutableListOf<String>()

        when {
            coolantTemp < 40f && rpm <= 0 -> {
                stage = WarmupStage.S0
                progress = (coolantTemp / 73f).coerceIn(0f, 0.2f)
                tips.add("❄️ Motore spento a freddo (${coolantTemp.toInt()}°C). All'avvio, mantieni il riscaldamento abitacolo spento per 1 minuto.")
            }
            coolantTemp < 40f && rpm > 0 -> {
                stage = WarmupStage.S1A
                progress = 0.25f + ((coolantTemp / 40f) * 0.15f)
                tips.add("🔥 S1a: Riscaldamento catalizzatore in corso. Tieni il clima OFF o al minimo per non sottrarre calore e ridurre la durata della fase.")
                if (ambientTemp < 12f) {
                    tips.add("💨 Temperatura esterna rigida (${ambientTemp.toInt()}°C): Evita partenze brusche, procedi a velocità costante moderata.")
                }
            }
            coolantTemp in 40f..54.9f -> {
                stage = WarmupStage.S1B
                progress = 0.40f + (((coolantTemp - 40f) / 15f) * 0.20f)
                tips.add("🚗 S1b: Il termico si sta scaldando (${coolantTemp.toInt()}°C / 73°C). Guida dolce a 1500-2000 RPM per velocizzare il riscaldamento.")
                if (ambientTemp < 15f) {
                    tips.add("💡 Consiglio: Imposta il riscaldamento su max 20°C in modalità ECO per evitare riaccensioni continue del termico.")
                }
            }
            coolantTemp in 55f..69.9f -> {
                stage = WarmupStage.S2
                progress = 0.60f + (((coolantTemp - 55f) / 15f) * 0.25f)
                tips.add("⚡ S2: Transizione attiva. Il motore termico può spegnersi alle soste ai semafori.")
                tips.add("🎯 Mantieni un carico motore medio durante le accelerazioni per raggiungere rapidamente i 73°C.")
            }
            coolantTemp in 70f..72.9f -> {
                stage = WarmupStage.S3
                progress = 0.88f
                tips.add("🚀 S3: Quasi a regime completo (${coolantTemp.toInt()}°C). Rilascia l'acceleratore per 5s alla prima fermata per forzare il passaggio a S4!")
            }
            else -> {
                stage = WarmupStage.S4
                progress = 1.0f
                tips.add("✅ S4: Piena Efficienza Ibrida Raggiunta! Il motore termico opera in ciclo Atkinson al 100%.")
                tips.add("🌿 Massima economia: Usa la tecnica 'Pulse & Glide' e il veleggiamento EV per consumi record.")
            }
        }

        return HybridWarmupStatus(
            stage = stage,
            coolantTemp = coolantTemp,
            ambientTemp = ambientTemp,
            catalystTemp = if (coolantTemp > 60f) 550f else 320f,
            engineRpm = rpm,
            progressPercent = progress,
            hasLiveData = true,
            recommendations = tips
        )
    }

    /**
     * Parses the response from 2228C1 or 2161 into HvBatteryStatus.
     */
    fun parseBatteryResponse(raw: String, isForced: Boolean): HvBatteryStatus? {
        val clean = Elm327Protocol.cleanResponse(raw).uppercase()
        if (Elm327Protocol.isError(clean) || clean.length < 8) {
            return null
        }

        try {
            var hexPayload = clean
            if (hexPayload.contains("6228C1")) {
                hexPayload = hexPayload.substring(hexPayload.indexOf("6228C1") + 6)
            } else if (hexPayload.contains("6161")) {
                hexPayload = hexPayload.substring(hexPayload.indexOf("6161") + 4)
            }

            if (hexPayload.length < 8) {
                return null
            }

            val t1 = (hexPayload.substring(0, 2).toInt(16) - 40).toDouble()
            val t2 = if (hexPayload.length >= 4) (hexPayload.substring(2, 4).toInt(16) - 40).toDouble() else t1
            val t3 = if (hexPayload.length >= 6) (hexPayload.substring(4, 6).toInt(16) - 40).toDouble() else t1
            val t4 = if (hexPayload.length >= 8) (hexPayload.substring(6, 8).toInt(16) - 40).toDouble() else t1
            
            val intake = if (hexPayload.length >= 10) (hexPayload.substring(8, 10).toInt(16) - 40).toDouble() else t1
            val fanLevel = if (hexPayload.length >= 12) {
                val rawFan = hexPayload.substring(10, 12).toInt(16)
                rawFan.coerceIn(0, 6)
            } else {
                if (isForced) 6 else 0
            }

            val temps = listOf(t1, t2, t3, t4).filter { it > -30 && it < 100 }
            val maxT = if (temps.isNotEmpty()) temps.maxOrNull() ?: t1 else t1
            val avgT = if (temps.isNotEmpty()) temps.average() else t1

            return HvBatteryStatus(
                temp1 = t1,
                temp2 = t2,
                temp3 = t3,
                temp4 = t4,
                maxTemp = maxT,
                avgTemp = avgT,
                intakeTemp = intake,
                fanSpeedLevel = fanLevel,
                isFanForced = isForced,
                isThermalThrottled = maxT >= 36.0,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore parsing frame batteria: $raw", e)
            return null
        }
    }
}
