package com.yaris.sniffer.server

import java.util.Locale

/**
 * Motore di Decodifica Semantica e Reverse Engineering per protocolli Automotive OBD-II, UDS e CAN bus.
 * Ottimizzato per Toyota Yaris Hybrid TNGA-B (XP210) e compatibile con ISO 14229-1 (UDS), ISO 15765-2 (ISO-TP)
 * e SAE J1979 (OBD-II).
 */
object AutomotiveProtocolDecoder {

    // 1. Centraline Elettroniche (ECU) e Header CAN Toyota TNGA-B
    val KNOWN_ECUS = mapOf(
        "7E0" to "Engine / Hybrid Powertrain ECU",
        "7E2" to "HV Battery Management ECU (Denso)",
        "7C0" to "Combination Meter ECU (Cluster)",
        "750" to "Main Body / Gateway ECU",
        "7C4" to "Air Conditioning ECU",
        "7A0" to "ADAS / TSS 2.5 Driving Assist / EPS",
        "7DF" to "OBD-II Functional Broadcast"
    )

    val KNOWN_RESPONSES = mapOf(
        "7E8" to "Engine ECU Response",
        "7EA" to "HV Battery ECU Response",
        "7C8" to "Combination Meter Response",
        "758" to "Main Body / Gateway Response",
        "7CC" to "Air Conditioning Response",
        "7A8" to "ADAS ECU Response"
    )

    fun getEcuName(header: String): String? {
        val clean = header.trim().uppercase()
        return KNOWN_ECUS[clean] ?: KNOWN_RESPONSES[clean]
    }

    fun getResponseIdForHeader(header: String): String {
        val clean = header.trim().uppercase()
        return when (clean) {
            "7E0" -> "7E8"
            "7E2" -> "7EA"
            "7C0" -> "7C8"
            "750" -> "758"
            "7C4" -> "7CC"
            "7A0" -> "7A8"
            "7DF" -> "7E8"
            else -> {
                val num = clean.toIntOrNull(16)
                if (num != null && clean.length == 3) {
                    Integer.toHexString(num + 8).uppercase()
                } else "7E8"
            }
        }
    }

    fun extractHeaderFromCommand(command: String): String? {
        val clean = command.trim().uppercase().replace(" ", "")
        if (clean.startsWith("ATSH")) {
            val hdr = clean.removePrefix("ATSH").trim()
            if (hdr.isNotEmpty()) return hdr
        }
        return null
    }

    // 2. Negative Response Codes UDS (ISO 14229-1)
    val NRC_MAP = mapOf(
        "10" to "generalReject",
        "11" to "serviceNotSupported",
        "12" to "subFunctionNotSupported",
        "13" to "incorrectMessageLengthOrInvalidFormat",
        "14" to "responseTooLong",
        "21" to "busyRepeatRequest",
        "22" to "conditionsNotCorrect",
        "24" to "requestSequenceError",
        "25" to "noResponseFromSubnetComponent",
        "26" to "failurePreventsExecutionOfRequestedAction",
        "31" to "requestOutOfRange",
        "33" to "securityAccessDenied",
        "35" to "invalidKey",
        "36" to "exceedNumberOfAttempts",
        "37" to "requiredTimeDelayNotExpired",
        "70" to "uploadDownloadNotAccepted",
        "71" to "transferDataSuspended",
        "72" to "generalProgrammingFailure",
        "73" to "wrongBlockSequenceCounter",
        "78" to "requestCorrectlyReceived-ResponsePending",
        "7E" to "subFunctionNotSupportedInActiveSession",
        "7F" to "serviceNotSupportedInActiveSession",
        "81" to "rpmTooHigh",
        "82" to "rpmTooLow",
        "83" to "engineIsRunning",
        "84" to "engineIsNotRunning",
        "88" to "torqueConverterClutchLocked",
        "89" to "voltageTooHigh",
        "8A" to "voltageTooLow"
    )

    // 3. Service Identifiers (UDS & OBD)
    val SID_MAP = mapOf(
        "01" to "ShowCurrentData",
        "02" to "ShowFreezeFrame",
        "03" to "ShowStoredDTCs",
        "04" to "ClearDTCs",
        "09" to "RequestVehicleInfo",
        "10" to "DiagnosticSessionControl",
        "11" to "ECUReset",
        "14" to "ClearDiagnosticInformation",
        "19" to "ReadDTCInformation",
        "21" to "ToyotaEnhancedReadData",
        "22" to "ReadDataByIdentifier",
        "23" to "ReadMemoryByAddress",
        "27" to "SecurityAccess",
        "28" to "CommunicationControl",
        "2E" to "WriteDataByIdentifier",
        "2F" to "InputOutputControlByIdentifier",
        "30" to "ToyotaActuatorTest",
        "31" to "RoutineControl",
        "34" to "RequestDownload",
        "35" to "RequestUpload",
        "36" to "TransferData",
        "37" to "RequestTransferExit",
        "3E" to "TesterPresent",
        "85" to "ControlDTCSetting"
    )

    // 4. Data Identifiers (DIDs) Toyota TNGA-B & ISO 14229
    val TOYOTA_DID_MAP = mapOf(
        "28C1" to "HV Battery Cell Temperatures & Fan Telemetry",
        "28C0" to "HV Battery Pack Overview / Status",
        "A001" to "Combination Meter Reverse Buzzer",
        "A002" to "Driver Seatbelt Warning Chime",
        "A003" to "Passenger Seatbelt Warning Chime",
        "A004" to "Rear Seatbelt Warning Chime",
        "B001" to "Body Speed-Sensing Auto Door Lock",
        "B002" to "Body Shift-to-P Auto Door Unlock",
        "B003" to "Body Power Windows with Key Fob",
        "B004" to "Body Keyless Buzzer Volume",
        "B005" to "Body Auto Relock Timer",
        "B006" to "Body Door Unlock Mode",
        "B007" to "Body Turn Signal Flashes",
        "B008" to "Body Rear Wiper Reverse Link",
        "B009" to "Body Drip Wipe Extra Pass",
        "B00A" to "Body Wiper Speed Sensitivity Link",
        "B00B" to "Body Interior Light Dimming Duration",
        "B00C" to "Body Footwell Lighting in Drive",
        "B00D" to "Body Auto Light Sensitivity",
        "B00E" to "Body Follow Me Home Headlight Duration",
        "C001" to "Touch 3 Infotainment Opening Screen",
        "C002" to "Audio ASL Speed-Compensated Volume",
        "C003" to "Digital Cluster Theme",
        "C004" to "RSA Speed Warning Chime Mode",
        "C005" to "Touch Screen Beep Feedback",
        "C006" to "Rear View Camera Off Delay",
        "C007" to "Microphone Sensitivity Gain Level",
        "D001" to "TSS ADAS Lane Departure Alert Volume",
        "D002" to "Blind Spot Monitor BSM Sensitivity",
        "D003" to "Rear Cross Traffic Alert RCTA Enable",
        "D004" to "Lane Tracing Assist LTA Centering",
        "D005" to "Pre-Collision System PCS Remember Last State",
        "E001" to "AirCon Automatic A/C with AUTO button",
        "E002" to "AirCon Eco Mode Air Conditioning Efficiency",
        "E003" to "AirCon Blower Activation on Defroster",
        "E004" to "AirCon Temperature Sensor Calibration",
        "F190" to "VIN (Vehicle Identification Number)",
        "F186" to "Active Diagnostic Session",
        "F187" to "Spare Part Number",
        "F188" to "ECU Software Number",
        "F189" to "ECU Software Version",
        "F18A" to "System Supplier Identifier",
        "F18C" to "ECU Serial Number",
        "F191" to "ECU Hardware Number"
    )

    fun getDidName(did: String): String {
        val clean = did.trim().uppercase()
        return TOYOTA_DID_MAP[clean] ?: "DID 0x$clean"
    }

    fun interpretDidPayload(did: String, payloadHex: String): String? {
        val d = did.trim().uppercase()
        val p = payloadHex.trim().uppercase().replace(" ", "")
        return when (d) {
            "A001" -> when (p) {
                "00" -> "Singolo Bip (Comfort)"
                "01" -> "Continuo (Standard)"
                else -> null
            }
            "A002", "A003", "A004" -> when (p) {
                "00" -> "Disattivato"
                "01" -> "Attivo (Standard)"
                else -> null
            }
            "B001" -> when (p) {
                "00" -> "Disattivato"
                "01" -> "Chiusura a 20 km/h (Speed)"
                "02" -> "Chiusura cambio D (Shift from P)"
                else -> null
            }
            "B002", "B003", "B008", "B009", "B00A", "B00C", "C005", "D003", "D004", "D005", "E001", "E002", "E003" -> when (p) {
                "00" -> "Disattivato"
                "01" -> "Attivo"
                else -> null
            }
            "B004" -> when (p) {
                "00" -> "Muto"
                "02" -> "Basso (Vol 2)"
                "04" -> "Medio (Vol 4)"
                "06" -> "Alto (Vol 6)"
                else -> null
            }
            "B005" -> when (p) {
                "01" -> "30s"
                "02" -> "60s"
                "03" -> "120s"
                else -> null
            }
            "B006" -> when (p) {
                "00" -> "Tutte le porte"
                "01" -> "Solo guida (2 tocchi tutte)"
                else -> null
            }
            "B007" -> when (p) {
                "00" -> "Disattivato"
                "03" -> "3 Lampeggi (Standard)"
                "04" -> "4 Lampeggi"
                "05" -> "5 Lampeggi"
                "06" -> "6 Lampeggi"
                else -> null
            }
            "C001" -> when (p) {
                "00" -> "Toyota Standard"
                "01" -> "Gazoo Racing (GR)"
                "02" -> "Hybrid Synergy Drive"
                else -> null
            }
            "C002" -> when (p) {
                "00" -> "Disattivato"
                "01" -> "Basso"
                "02" -> "Medio"
                "03" -> "Alto"
                else -> null
            }
            "C003" -> when (p) {
                "01" -> "Sport GR (Rosso)"
                "02" -> "Smart (Ciano)"
                "03" -> "Casual"
                "04" -> "Tough"
                else -> null
            }
            "C004" -> when (p) {
                "00" -> "Muto / Solo Visivo"
                "01" -> "Bip Basso"
                "02" -> "Bip Standard (ISA)"
                else -> null
            }
            "C006" -> when (p) {
                "00" -> "Immediato"
                "01" -> "5 Secondi"
                "02" -> "10 Secondi"
                else -> null
            }
            "D001" -> when (p) {
                "01" -> "Basso"
                "02" -> "Medio"
                "03" -> "Alto"
                else -> null
            }
            "D002" -> when (p) {
                "01" -> "Vicino"
                "02" -> "Normale"
                "03" -> "Lontano"
                else -> null
            }
            "E004" -> when (p) {
                "00" -> "-2°C"
                "01" -> "-1°C"
                "02" -> "0°C (Standard)"
                "03" -> "+1°C"
                "04" -> "+2°C"
                else -> null
            }
            else -> null
        }
    }

    // 5. Mode 01 PIDs (SAE J1979)
    data class Mode01PidInfo(
        val name: String,
        val byteCount: Int,
        val unit: String,
        val decoder: (List<Int>) -> String
    )

    val MODE01_PIDS = mapOf(
        "00" to Mode01PidInfo("Supported PIDs [01-20]", 4, "bitmask") { bytes ->
            "0x" + bytes.joinToString("") { "%02X".format(it) }
        },
        "01" to Mode01PidInfo("Monitor Status", 4, "") { bytes ->
            "0x" + bytes.joinToString("") { "%02X".format(it) }
        },
        "04" to Mode01PidInfo("Engine Load", 1, "%") { bytes ->
            "%.1f %%".format(Locale.US, bytes[0] / 2.55)
        },
        "05" to Mode01PidInfo("Coolant Temp", 1, "°C") { bytes ->
            "${bytes[0] - 40} °C"
        },
        "0B" to Mode01PidInfo("Intake Manifold Pressure", 1, "kPa") { bytes ->
            "${bytes[0]} kPa"
        },
        "0C" to Mode01PidInfo("Engine RPM", 2, "rpm") { bytes ->
            val rpm = ((bytes[0] * 256) + bytes[1]) / 4
            "$rpm rpm"
        },
        "0D" to Mode01PidInfo("Vehicle Speed", 1, "km/h") { bytes ->
            "${bytes[0]} km/h"
        },
        "0E" to Mode01PidInfo("Timing Advance", 1, "°") { bytes ->
            "%.1f °".format(Locale.US, (bytes[0] - 128) / 2.0)
        },
        "0F" to Mode01PidInfo("Intake Air Temp", 1, "°C") { bytes ->
            "${bytes[0] - 40} °C"
        },
        "10" to Mode01PidInfo("MAF Air Flow", 2, "g/s") { bytes ->
            val maf = ((bytes[0] * 256) + bytes[1]) / 100.0
            "%.2f g/s".format(Locale.US, maf)
        },
        "11" to Mode01PidInfo("Throttle Position", 1, "%") { bytes ->
            "%.1f %%".format(Locale.US, bytes[0] / 2.55)
        },
        "1F" to Mode01PidInfo("Run Time Since Start", 2, "s") { bytes ->
            "${(bytes[0] * 256) + bytes[1]} s"
        },
        "20" to Mode01PidInfo("Supported PIDs [21-40]", 4, "bitmask") { bytes ->
            "0x" + bytes.joinToString("") { "%02X".format(it) }
        },
        "2F" to Mode01PidInfo("Fuel Level", 1, "%") { bytes ->
            "%.1f %%".format(Locale.US, bytes[0] / 2.55)
        },
        "31" to Mode01PidInfo("Distance Since Cleared", 2, "km") { bytes ->
            "${(bytes[0] * 256) + bytes[1]} km"
        },
        "3C" to Mode01PidInfo("Catalyst Temp B1S1", 2, "°C") { bytes ->
            val temp = ((bytes[0] * 256) + bytes[1]) / 10.0 - 40.0
            "%.1f °C".format(Locale.US, temp)
        },
        "40" to Mode01PidInfo("Supported PIDs [41-60]", 4, "bitmask") { bytes ->
            "0x" + bytes.joinToString("") { "%02X".format(it) }
        },
        "42" to Mode01PidInfo("Control Module Voltage", 2, "V") { bytes ->
            val volt = ((bytes[0] * 256) + bytes[1]) / 1000.0
            "%.2f V".format(Locale.US, volt)
        },
        "46" to Mode01PidInfo("Ambient Air Temp", 1, "°C") { bytes ->
            "${bytes[0] - 40} °C"
        },
        "5C" to Mode01PidInfo("Engine Oil Temp", 1, "°C") { bytes ->
            "${bytes[0] - 40} °C"
        }
    )

    // 6. Decodifica Comandi AT
    private fun decodeAtCommand(clean: String, raw: String): String {
        return when {
            clean == "ATZ" -> "[AT: Reset chip ELM327]"
            clean == "ATWS" -> "[AT: Warm Start chip ELM327]"
            clean in listOf("ATD", "ATBD") -> "[AT: Ripristino impostazioni di fabbrica]"
            clean == "ATE0" -> "[AT: Echo OFF]"
            clean == "ATE1" -> "[AT: Echo ON]"
            clean == "ATL0" -> "[AT: Linefeeds OFF]"
            clean == "ATL1" -> "[AT: Linefeeds ON]"
            clean == "ATS0" -> "[AT: Spazi OFF]"
            clean == "ATS1" -> "[AT: Spazi ON]"
            clean == "ATH0" -> "[AT: Header CAN OFF]"
            clean == "ATH1" -> "[AT: Header CAN ON]"
            clean == "ATCAF0" -> "[AT: CAN Auto Formatting OFF]"
            clean == "ATCAF1" -> "[AT: CAN Auto Formatting ON]"
            clean.startsWith("ATSP") -> {
                val proto = clean.removePrefix("ATSP")
                val desc = when (proto) {
                    "6" -> "ISO 15765-4 CAN (11-bit, 500 kbaud)"
                    "0" -> "Rilevamento Automatico"
                    else -> "Protocollo $proto"
                }
                "[AT SP: Imposta $desc]"
            }
            clean.startsWith("ATTP") -> "[AT TP: Prova protocollo ${clean.removePrefix("ATTP")}]"
            clean == "ATDPN" -> "[AT DPN: Richiesta numero protocollo attivo]"
            clean == "ATDP" -> "[AT DP: Descrizione protocollo attivo]"
            clean == "ATRV" -> "[AT RV: Lettura tensione batteria 12V]"
            clean == "ATIGN" -> "[AT IGN: Lettura stato quadro/accensione]"
            clean.startsWith("ATSH") -> {
                val hdr = clean.removePrefix("ATSH")
                val ecu = getEcuName(hdr) ?: "Custom ECU"
                "[AT SH: Imposta Header CAN Trasmissione a $hdr ($ecu)]"
            }
            clean.startsWith("ATCRA") -> {
                val filt = clean.removePrefix("ATCRA")
                val ecu = getEcuName(filt) ?: "Filtro"
                "[AT CRA: Imposta Filtro Ricezione CAN a $filt ($ecu)]"
            }
            clean.startsWith("ATST") -> {
                val hex = clean.removePrefix("ATST")
                val ms = (hex.toIntOrNull(16) ?: 0) * 4.096
                "[AT ST: Timeout CAN/UART impostato a ~${ms.toInt()}ms (0x$hex)]"
            }
            clean.startsWith("ATAT") -> {
                val mode = clean.removePrefix("ATAT")
                val desc = when (mode) {
                    "0" -> "Disattivato"
                    "1" -> "Auto 1 (Standard)"
                    "2" -> "Auto 2 (Aggressivo)"
                    else -> mode
                }
                "[AT AT: Adaptive Timing $desc]"
            }
            clean == "ATAR" -> "[AT AR: Auto Receive abilitato]"
            clean == "ATAL" -> "[AT AL: Consenti messaggi lunghi]"
            clean.startsWith("ATFCSH") -> {
                val hdr = clean.removePrefix("ATFCSH")
                "[AT FC SH: Header Flow Control ISO-TP impostato a $hdr]"
            }
            clean.startsWith("ATFCSD") -> {
                val data = clean.removePrefix("ATFCSD")
                val desc = if (data == "300000") "CTS, BlockSize=0, SeparationTime=0ms" else data
                "[AT FC SD: Dati Flow Control ISO-TP: $desc]"
            }
            clean.startsWith("ATFCSM") -> {
                val mode = clean.removePrefix("ATFCSM")
                val desc = if (mode == "1") "Custom Mode" else "Default Mode"
                "[AT FC SM: Modalità Flow Control ISO-TP: $desc]"
            }
            clean in listOf("ATI", "STI", "AT@1", "STDI") -> "[AT/ST: Richiesta identificativo / versione firmware chip]"
            else -> "[AT: $raw]"
        }
    }

    private fun decodeStCommand(clean: String, raw: String): String {
        return when {
            clean == "STDI" -> "[ST DI: Richiesta Device ID chipset STN / OBDLink]"
            clean == "STI" -> "[STI: Richiesta Firmware Info chipset STN]"
            else -> "[ST: $raw]"
        }
    }

    /**
     * Decodifica semantica in tempo reale dei comandi trasmessi (TX).
     */
    fun decodeTx(command: String, activeHeader: String = ""): String {
        val clean = command.trim().replace(" ", "").uppercase()
        if (clean.isEmpty()) return ""

        if (clean == "\\R\\R(WAKE)" || clean == "\\R\\R" || command.contains("\r\r")) {
            return "[WAKE: Sequenza di risveglio / low-power escape]"
        }

        if (clean.startsWith("AT")) {
            return decodeAtCommand(clean, command.trim())
        }
        if (clean.startsWith("ST")) {
            return decodeStCommand(clean, command.trim())
        }

        if (clean.startsWith("3000") && clean.length in 4..6) {
            val bs = if (clean.length >= 4) clean.substring(2, 4).toIntOrNull(16) ?: 0 else 0
            val st = if (clean.length >= 6) clean.substring(4, 6).toIntOrNull(16) ?: 0 else 0
            return if (bs == 0 && st == 0) {
                "[ISO-TP Flow Control: CTS (Clear To Send), BS=0, STmin=0ms]"
            } else {
                "[ISO-TP Flow Control: CTS, BS=$bs, STmin=${st}ms]"
            }
        }
        // Riconoscimento eventuale framing ISO-TP Single Frame: es. "02 01 0D"
        val payload = if (clean.length >= 4 && clean.length % 2 == 0) {
            val b0 = clean.substring(0, 2).toIntOrNull(16) ?: -1
            val b1 = clean.substring(2, 4)
            val isKnownSid = b1 in SID_MAP.keys || b1 in listOf("21", "22", "2E", "2F", "30", "31", "3E", "10", "01", "03", "04")
            if (b0 in 2..7 && isKnownSid && clean.length >= 2 + (b0 * 2)) {
                clean.substring(2)
            } else if (b0 == 1 && b1 in listOf("03", "04") && clean.length == 4) {
                clean.substring(2)
            } else clean
        } else clean

        // Mode 01 PIDs
        if (payload.startsWith("01") && payload.length >= 4) {
            val pids = mutableListOf<String>()
            var idx = 2
            while (idx + 2 <= payload.length) {
                val pidHex = payload.substring(idx, idx + 2)
                val info = MODE01_PIDS[pidHex]
                pids.add("0x$pidHex" + (if (info != null) " (${info.name})" else ""))
                idx += 2
            }
            return if (pids.size == 1) {
                "[Mode 01 PID: ${pids[0]}]"
            } else {
                "[Mode 01 Multi-PID: ${pids.joinToString(", ")}]"
            }
        }

        // Mode 03 / 04 / 09
        if (payload == "03") return "[OBD Mode 03: Richiesta DTCs]"
        if (payload == "04") return "[OBD Mode 04: Azzeramento DTCs / Reset]"
        if (payload.startsWith("0902")) return "[OBD Mode 09: Richiesta VIN veicolo]"
        if (payload.startsWith("09")) return "[OBD Mode 09: Richiesta Info PID 0x${payload.substring(2)}]"

        // UDS DiagnosticSessionControl (0x10)
        if (payload.startsWith("10") && payload.length >= 4) {
            val subFunc = payload.substring(2, 4)
            val sessionName = when (subFunc) {
                "01" -> "Default Session (0x01)"
                "02" -> "Programming Session (0x02)"
                "03" -> "Extended Diagnostic Session (0x03)"
                "04" -> "Safety System Session (0x04)"
                else -> "Session 0x$subFunc"
            }
            return "[UDS 0x10 DiagnosticSessionControl: $sessionName]"
        }

        // UDS ReadDataByIdentifier (0x22)
        if (payload.startsWith("22") && payload.length >= 6) {
            val did = payload.substring(2, 6)
            val didName = getDidName(did)
            return "[UDS 0x22 ReadDID: 0x$did ($didName)]"
        }

        // UDS WriteDataByIdentifier (0x2E)
        if (payload.startsWith("2E") && payload.length >= 6) {
            val did = payload.substring(2, 6)
            val didName = getDidName(did)
            val writePayload = payload.substring(6)
            val interp = interpretDidPayload(did, writePayload)
            val interpStr = if (interp != null) " [$interp]" else ""
            return "[UDS 0x2E WriteDID: 0x$did ($didName), Payload: $writePayload$interpStr]"
        }

        // UDS InputOutputControl (0x2F)
        if (payload.startsWith("2F") && payload.length >= 6) {
            val did = payload.substring(2, 6)
            val didName = getDidName(did)
            return "[UDS 0x2F IOControl: 0x$did ($didName)]"
        }

        // UDS RoutineControl (0x31)
        if (payload.startsWith("31") && payload.length >= 4) {
            val subFunc = payload.substring(2, 4)
            val subName = when (subFunc) {
                "01" -> "Start Routine"
                "02" -> "Stop Routine"
                "03" -> "RequestResults"
                else -> "SubFunc 0x$subFunc"
            }
            val routineId = if (payload.length >= 8) " 0x" + payload.substring(4, 8) else ""
            return "[UDS 0x31 RoutineControl: $subName$routineId]"
        }

        // UDS TesterPresent (0x3E)
        if (payload.startsWith("3E")) {
            val subFunc = if (payload.length >= 4) payload.substring(2, 4) else "00"
            val desc = if (subFunc == "80") "Suppress Positive Response" else "Zero Sub-function"
            return "[UDS 0x3E TesterPresent: Keep-Alive ($desc)]"
        }

        // Toyota Mode 21 (Enhanced Diagnostics)
        if (payload.startsWith("21") && payload.length >= 4) {
            val pid = payload.substring(2, 4)
            val desc = when (pid) {
                "01" -> "Hybrid Powertrain Live Telemetry"
                "81" -> "HV Battery Block Voltages"
                else -> "Custom PID 0x$pid"
            }
            return "[Toyota Mode 21: $desc]"
        }

        // Toyota Mode 30 (Active Test)
        if (payload.startsWith("30") && payload.length >= 4) {
            val pid = payload.substring(2, 4)
            if (pid == "08") {
                val level = if (payload.length >= 6) payload.substring(4, 6).toIntOrNull(16) ?: 0 else 0
                val lvlDesc = if (level == 0) "Off (Level 0)" else "Level $level"
                return "[Toyota Mode 30: Active Test HV Battery Fan Control, $lvlDesc]"
            }
            return "[Toyota Mode 30: Active Test PID 0x$pid]"
        }

        return ""
    }

    /**
     * Decodifica semantica in tempo reale delle risposte ricevute (RX).
     */
    fun decodeRx(response: String, activeHeader: String = "", lastTx: String = ""): String {
        val clean = response.trim()
        if (clean.isEmpty()) return ""

        val upper = clean.uppercase()
        if (upper == "OK") return "[ACK: OK]"
        if (upper == "NO DATA" || upper == "NODATA") return "[ELM327: NO DATA]"
        if (upper == "CAN ERROR") return "[ELM327: CAN ERROR (Bus fault)]"
        if (upper.contains("BUS INIT: ERROR")) return "[ELM327: Bus Init Error]"
        if (upper.contains("BUS INIT: OK")) return "[ELM327: Bus Init OK]"
        if (upper == "STOPPED") return "[ELM327: STOPPED]"
        if (upper.matches(Regex("""\d{1,2}\.\d+\s*V"""))) return "[AT RV: 12V Battery Voltage = $clean]"

        val stripped = clean.replace(Regex("""(?:^|[\r\n\s])[0-9A-Fa-f]{1,2}\s*:\s*"""), " ")
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()

        val hexBytes = stripped.split(Regex("""\s+""")).filter { it.matches(Regex("""[0-9A-Fa-f]{2}""")) }
        if (hexBytes.isEmpty()) return ""

        val hexStream = hexBytes.joinToString("").uppercase()

        // ISO-TP Framing check
        if (hexStream.startsWith("300000")) {
            return "[ISO-TP Flow Control: CTS (Clear To Send), BS=0, STmin=0ms]"
        }
        val isoTpAnnotation = if (hexStream.startsWith("1") && hexStream.length >= 4) {
            val totalLen = hexStream.substring(1, 4).toIntOrNull(16)
            if (totalLen != null && totalLen >= 8) {
                "[ISO-TP First Frame: totalLen=$totalLen bytes]"
            } else null
        } else if (hexStream.startsWith("2") && hexStream.length >= 2 && hexStream[1].isDigit()) {
            val seq = hexStream.substring(1, 2)
            "[ISO-TP Consecutive Frame: seq=$seq]"
        } else null

        var innerAnnotation = ""

        // Negative Response Code: 7F <SID> <NRC>
        val idx7F = hexBytes.indexOfFirst { it.equals("7F", ignoreCase = true) }
        if (idx7F != -1 && idx7F + 2 < hexBytes.size) {
            val sid = hexBytes[idx7F + 1].uppercase()
            val nrc = hexBytes[idx7F + 2].uppercase()
            val sidName = SID_MAP[sid] ?: "SID 0x$sid"
            val nrcName = NRC_MAP[nrc] ?: "unknownNRC"
            innerAnnotation = "[NRC 0x$nrc: $nrcName (SID 0x$sid - $sidName)]"
        } else {
            // DiagnosticSessionControl ACK: 50 <subfunc>
            val idx50 = hexBytes.indexOfFirst { it.equals("50", ignoreCase = true) }
            if (idx50 != -1 && idx50 + 1 < hexBytes.size) {
                val sub = hexBytes[idx50 + 1].uppercase()
                val sName = when (sub) {
                    "01" -> "Default Session (0x01)"
                    "02" -> "Programming Session (0x02)"
                    "03" -> "Extended Session (0x03)"
                    "04" -> "Safety System Session (0x04)"
                    else -> "Session 0x$sub"
                }
                innerAnnotation = "[UDS 0x50 SessionControl ACK: $sName]"
            } else {
                // ReadDataByIdentifier ACK: 62 <DID> <Payload...>
                val idx62 = hexBytes.indexOfFirst { it.equals("62", ignoreCase = true) }
                if (idx62 != -1 && idx62 + 2 < hexBytes.size) {
                    val did = (hexBytes[idx62 + 1] + hexBytes[idx62 + 2]).uppercase()
                    val payload = hexBytes.subList(idx62 + 3, hexBytes.size).joinToString("")
                    val didName = getDidName(did)
                    val interp = interpretDidPayload(did, payload)
                    val interpStr = if (interp != null) " [$interp]" else ""
                    val payloadDisplay = if (payload.isNotEmpty()) "Payload: $payload$interpStr" else "ACK"
                    innerAnnotation = "[UDS 0x62 ReadDID ACK: DID 0x$did ($didName), $payloadDisplay]"
                } else {
                    // WriteDataByIdentifier ACK: 6E <DID>
                    val idx6E = hexBytes.indexOfFirst { it.equals("6E", ignoreCase = true) }
                    if (idx6E != -1 && idx6E + 2 < hexBytes.size) {
                        val did = (hexBytes[idx6E + 1] + hexBytes[idx6E + 2]).uppercase()
                        val didName = getDidName(did)
                        innerAnnotation = "[UDS 0x6E WriteDID ACK: DID 0x$did ($didName)]"
                    } else {
                        // TesterPresent ACK: 7E <subfunc>
                        val idx7E = hexBytes.indexOfFirst { it.equals("7E", ignoreCase = true) }
                        if (idx7E != -1) {
                            val sub = if (idx7E + 1 < hexBytes.size) hexBytes[idx7E + 1].uppercase() else "00"
                            innerAnnotation = "[UDS 0x7E TesterPresent ACK: 0x$sub]"
                        } else {
                            // Mode 01 Response: 41 <PID> <Data...>
                            val idx41 = hexBytes.indexOfFirst { it.equals("41", ignoreCase = true) }
                            if (idx41 != -1 && idx41 + 1 < hexBytes.size) {
                                val decodedList = mutableListOf<String>()
                                var curr = idx41 + 1
                                while (curr < hexBytes.size) {
                                    val pidHex = hexBytes[curr].uppercase()
                                    val info = MODE01_PIDS[pidHex]
                                    if (info != null) {
                                        if (curr + info.byteCount < hexBytes.size) {
                                            val dataBytes = hexBytes.subList(curr + 1, curr + 1 + info.byteCount).map { it.toInt(16) }
                                            decodedList.add("${info.name} = ${info.decoder(dataBytes)}")
                                            curr += 1 + info.byteCount
                                        } else {
                                            decodedList.add("PID 0x$pidHex (${info.name})")
                                            break
                                        }
                                    } else {
                                        decodedList.add("PID 0x$pidHex")
                                        curr += 1
                                    }
                                }
                                if (decodedList.isNotEmpty()) {
                                    innerAnnotation = "[Mode 01 ACK: ${decodedList.joinToString(", ")}]"
                                }
                            } else {
                                // Mode 03 DTCs ACK: 43 ...
                                val idx43 = hexBytes.indexOfFirst { it.equals("43", ignoreCase = true) }
                                if (idx43 != -1) {
                                    val dtcBytes = hexBytes.subList(idx43 + 1, hexBytes.size)
                                    innerAnnotation = if (dtcBytes.all { it == "00" }) {
                                        "[OBD Mode 03 ACK: Nessun codice di errore DTC memorizzato]"
                                    } else {
                                        "[OBD Mode 03 ACK: DTC Raw: ${dtcBytes.joinToString(" ")}]"
                                    }
                                } else if (hexBytes.contains("44")) {
                                    innerAnnotation = "[OBD Mode 04 ACK: DTCs azzerati con successo]"
                                } else {
                                    // Mode 21 ACK: 61 <PID> ...
                                    val idx61 = hexBytes.indexOfFirst { it.equals("61", ignoreCase = true) }
                                    if (idx61 != -1 && idx61 + 1 < hexBytes.size) {
                                        val pid = hexBytes[idx61 + 1].uppercase()
                                        val desc = when (pid) {
                                            "01" -> "Hybrid Powertrain Live Telemetry"
                                            "81" -> "HV Battery Block Voltages"
                                            else -> "PID 0x$pid"
                                        }
                                        innerAnnotation = "[Toyota Mode 21 ACK: $desc]"
                                    } else {
                                        // Mode 30 ACK: 70 <PID> ...
                                        val idx70 = hexBytes.indexOfFirst { it.equals("70", ignoreCase = true) }
                                        if (idx70 != -1 && idx70 + 1 < hexBytes.size) {
                                            val pid = hexBytes[idx70 + 1].uppercase()
                                            val desc = if (pid == "08") "Active Test HV Battery Fan Control" else "Active Test 0x$pid"
                                            innerAnnotation = "[Toyota Mode 30 ACK: $desc]"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return when {
            isoTpAnnotation != null && innerAnnotation.isNotEmpty() -> "$isoTpAnnotation $innerAnnotation"
            isoTpAnnotation != null -> isoTpAnnotation
            else -> innerAnnotation
        }
    }

    /**
     * Converte un comando TX in una riga standard candump / SavvyCAN `(timestamp) can0 ID#PAYLOAD`.
     */
    fun formatCanDumpTx(timestampMs: Long, command: String, activeHeader: String): String? {
        val clean = command.trim().replace(" ", "").uppercase()
        if (clean.isEmpty() || clean.startsWith("AT") || clean.startsWith("ST") || clean.contains("WAKE")) {
            return null
        }
        val canId = if (activeHeader.isNotBlank()) activeHeader.uppercase() else "7DF"

        val payloadHex = if (clean.length in 2..14 && clean.length % 2 == 0) {
            val byteLen = clean.length / 2
            val b0 = clean.substring(0, 2).toIntOrNull(16) ?: -1
            val b1 = if (clean.length >= 4) clean.substring(2, 4) else ""
            val isKnownSid = b1 in SID_MAP.keys || b1 in listOf("21", "22", "2E", "2F", "30", "31", "3E", "10", "01", "03", "04")
            if (b0 in 2..7 && b0 == byteLen - 1 && isKnownSid) {
                clean.padEnd(16, '0')
            } else {
                val pci = "%02X".format(byteLen)
                (pci + clean).padEnd(16, '0')
            }
        } else {
            clean.padEnd(16, '0').take(16)
        }

        val tsSec = timestampMs / 1000
        val tsMicro = (timestampMs % 1000) * 1000
        return String.format(Locale.US, "(%d.%06d) can0 %s#%s", tsSec, tsMicro, canId, payloadHex)
    }

    /**
     * Converte una risposta RX in righe standard candump / SavvyCAN `(timestamp) can0 ID#PAYLOAD`.
     */
    fun formatCanDumpRx(timestampMs: Long, response: String, activeHeader: String): List<String> {
        val clean = response.trim()
        if (clean.isEmpty() || clean.equals("OK", ignoreCase = true) || clean.equals("NO DATA", ignoreCase = true) || clean.startsWith("?")) {
            return emptyList()
        }
        val canId = getResponseIdForHeader(if (activeHeader.isNotBlank()) activeHeader else "7E0")

        val lines = clean.split(Regex("""[\r\n]+""")).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var offsetMicro = 0L

        for (line in lines) {
            val stripped = line.replace(Regex("""^[0-9A-Fa-f]{1,2}\s*:\s*"""), "")
                .replace(">", "")
                .trim()
            val hexBytes = stripped.split(Regex("""\s+""")).filter { it.matches(Regex("""[0-9A-Fa-f]{2}""")) }
            if (hexBytes.isEmpty()) continue

            val hexStream = hexBytes.joinToString("").uppercase()
            val payloadHex = if (hexStream.length >= 16) {
                hexStream.take(16)
            } else if (hexStream.startsWith("1") || hexStream.startsWith("2") || hexStream.startsWith("30")) {
                hexStream.padEnd(16, '0')
            } else {
                val byteLen = hexBytes.size
                val sfLen = hexBytes[0].toIntOrNull(16)
                if (sfLen != null && sfLen == byteLen - 1) {
                    hexStream.padEnd(16, '0')
                } else {
                    val pci = "%02X".format(byteLen)
                    (pci + hexStream).padEnd(16, '0')
                }
            }

            val tsSec = (timestampMs + (offsetMicro / 1000)) / 1000
            val tsMicro = ((timestampMs * 1000) + offsetMicro) % 1_000_000
            result.add(String.format(Locale.US, "(%d.%06d) can0 %s#%s", tsSec, tsMicro, canId, payloadHex))
            offsetMicro += 1000
        }

        return result
    }

    /**
     * Aggregatore dedicato per Reverse Engineering: accumula DIDs letti con successo, DIDs scritti,
     * servizi rifiutati (NRC) e comandi speciali raggruppati per centralina (Header CAN).
     */
    class ReverseEngineeringTracker {
        data class ReadDidRecord(
            val ecuHeader: String,
            val did: String,
            val didName: String,
            var lastPayload: String,
            var count: Int,
            var lastTimestampMs: Long
        )

        data class WrittenDidRecord(
            val ecuHeader: String,
            val did: String,
            val didName: String,
            var lastPayload: String,
            var command: String,
            var count: Int,
            var lastTimestampMs: Long
        )

        data class RejectedServiceRecord(
            val ecuHeader: String,
            val sid: String,
            val didOrParam: String,
            val nrc: String,
            val nrcName: String,
            var count: Int,
            var lastTimestampMs: Long
        )

        data class DiscoveredCommandRecord(
            val ecuHeader: String,
            val commandType: String,
            val description: String,
            var count: Int,
            var lastTimestampMs: Long
        )

        private val readDids = mutableMapOf<String, ReadDidRecord>()
        private val writtenDids = mutableMapOf<String, WrittenDidRecord>()
        private val rejectedServices = mutableMapOf<String, RejectedServiceRecord>()
        private val discoveredCommands = mutableMapOf<String, DiscoveredCommandRecord>()
        private val activeEcus = linkedSetOf<String>()

        @Synchronized
        fun recordTx(command: String, activeHeader: String) {
            val ecu = if (activeHeader.isNotBlank()) activeHeader.uppercase() else "UNKNOWN"
            if (ecu != "UNKNOWN") activeEcus.add(ecu)

            val clean = command.trim().replace(" ", "").uppercase()

            if (clean.startsWith("01") && clean.length >= 4) {
                val key = "$ecu:MODE01:$clean"
                val rec = discoveredCommands.getOrPut(key) {
                    DiscoveredCommandRecord(ecu, "Mode 01 OBD", decodeTx(command, ecu), 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastTimestampMs = System.currentTimeMillis()
            } else if (clean.startsWith("21") && clean.length >= 4) {
                val key = "$ecu:MODE21:$clean"
                val rec = discoveredCommands.getOrPut(key) {
                    DiscoveredCommandRecord(ecu, "Toyota Mode 21", decodeTx(command, ecu), 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastTimestampMs = System.currentTimeMillis()
            } else if (clean.startsWith("30") && clean.length >= 4) {
                val key = "$ecu:MODE30:$clean"
                val rec = discoveredCommands.getOrPut(key) {
                    DiscoveredCommandRecord(ecu, "Toyota Mode 30 Active Test", decodeTx(command, ecu), 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastTimestampMs = System.currentTimeMillis()
            }
        }

        @Synchronized
        fun recordRx(response: String, activeHeader: String, lastTx: String) {
            val ecu = if (activeHeader.isNotBlank()) activeHeader.uppercase() else "UNKNOWN"
            if (ecu != "UNKNOWN") activeEcus.add(ecu)

            val cleanResp = response.trim().uppercase()
            val hexBytes = cleanResp.replace(Regex("""(?:^|[\r\n\s])[0-9A-Fa-f]{1,2}\s*:\s*"""), " ")
                .replace(">", "")
                .split(Regex("""\s+"""))
                .filter { it.matches(Regex("""[0-9A-Fa-f]{2}""")) }

            val idx7F = hexBytes.indexOfFirst { it == "7F" }
            if (idx7F != -1 && idx7F + 2 < hexBytes.size) {
                val sid = hexBytes[idx7F + 1]
                val nrc = hexBytes[idx7F + 2]
                val nrcName = NRC_MAP[nrc] ?: "unknownNRC"

                val cleanTx = lastTx.trim().replace(" ", "").uppercase()
                val didOrParam = if (cleanTx.startsWith("22") && cleanTx.length >= 6) {
                    cleanTx.substring(2, 6)
                } else if (cleanTx.startsWith("2E") && cleanTx.length >= 6) {
                    cleanTx.substring(2, 6)
                } else if (cleanTx.length >= 4) {
                    cleanTx.substring(2)
                } else "-"

                val key = "$ecu:$sid:$didOrParam:$nrc"
                val rec = rejectedServices.getOrPut(key) {
                    RejectedServiceRecord(ecu, sid, didOrParam, nrc, nrcName, 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastTimestampMs = System.currentTimeMillis()
                return
            }

            val idx62 = hexBytes.indexOfFirst { it == "62" }
            if (idx62 != -1 && idx62 + 2 < hexBytes.size) {
                val did = hexBytes[idx62 + 1] + hexBytes[idx62 + 2]
                val didName = getDidName(did)
                val payload = hexBytes.subList(idx62 + 3, hexBytes.size).joinToString("")
                val key = "$ecu:$did"
                val rec = readDids.getOrPut(key) {
                    ReadDidRecord(ecu, did, didName, payload, 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastPayload = payload
                rec.lastTimestampMs = System.currentTimeMillis()
                return
            }

            val idx6E = hexBytes.indexOfFirst { it == "6E" }
            if (idx6E != -1 && idx6E + 2 < hexBytes.size) {
                val did = hexBytes[idx6E + 1] + hexBytes[idx6E + 2]
                val didName = getDidName(did)

                val cleanTx = lastTx.trim().replace(" ", "").uppercase()
                val writtenPayload = if (cleanTx.startsWith("2E") && cleanTx.length >= 6) {
                    cleanTx.substring(6)
                } else ""

                val key = "$ecu:$did"
                val rec = writtenDids.getOrPut(key) {
                    WrittenDidRecord(ecu, did, didName, writtenPayload, lastTx.trim(), 0, System.currentTimeMillis())
                }
                rec.count++
                if (writtenPayload.isNotEmpty()) rec.lastPayload = writtenPayload
                rec.command = lastTx.trim()
                rec.lastTimestampMs = System.currentTimeMillis()
                return
            }

            val idx50 = hexBytes.indexOfFirst { it == "50" }
            if (idx50 != -1 && idx50 + 1 < hexBytes.size) {
                val sub = hexBytes[idx50 + 1]
                val sessionName = when (sub) {
                    "01" -> "Default Session"
                    "02" -> "Programming Session"
                    "03" -> "Extended Diagnostic Session"
                    else -> "Session 0x$sub"
                }
                val key = "$ecu:SESSION:$sub"
                val rec = discoveredCommands.getOrPut(key) {
                    DiscoveredCommandRecord(ecu, "UDS Session Change", "Transizione a $sessionName (0x$sub)", 0, System.currentTimeMillis())
                }
                rec.count++
                rec.lastTimestampMs = System.currentTimeMillis()
            }
        }

        @Synchronized
        fun generateSummary(): String {
            val sb = StringBuilder()
            sb.appendLine("================================================================")
            sb.appendLine("  === REVERSE ENGINEERING SUMMARY: DISCOVERED DIDs & COMMANDS ===")
            sb.appendLine("================================================================")
            sb.appendLine("Timestamp Analisi: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date()))
            sb.appendLine("Centraline Rilevate: ${activeEcus.size} (${activeEcus.joinToString(", ") { "$it [${getEcuName(it) ?: "ECU"}]" }})")
            sb.appendLine()

            val allEcus = (activeEcus + readDids.values.map { it.ecuHeader } + writtenDids.values.map { it.ecuHeader } + rejectedServices.values.map { it.ecuHeader }).distinct().sorted()

            if (allEcus.isEmpty() && readDids.isEmpty() && writtenDids.isEmpty()) {
                sb.appendLine("Nessun dato CAN/UDS o DID registrato durante questa sessione.")
                sb.appendLine("================================================================")
                return sb.toString()
            }

            for (ecu in allEcus) {
                val ecuName = getEcuName(ecu) ?: "Custom / Unknown ECU"
                sb.appendLine("[ECU $ecu - $ecuName]")

                val ecuReads = readDids.values.filter { it.ecuHeader == ecu }
                if (ecuReads.isNotEmpty()) {
                    sb.appendLine("  - READ DIDs (0x22 -> 0x62 ACK):")
                    for (r in ecuReads) {
                        val interp = interpretDidPayload(r.did, r.lastPayload)
                        val interpStr = if (interp != null) " [$interp]" else ""
                        sb.appendLine("    * DID 0x${r.did} (${r.didName}): Payload=${r.lastPayload}$interpStr (Queries: ${r.count})")
                    }
                } else {
                    sb.appendLine("  - READ DIDs: Nessuno")
                }

                val ecuWrites = writtenDids.values.filter { it.ecuHeader == ecu }
                if (ecuWrites.isNotEmpty()) {
                    sb.appendLine("  - WRITTEN DIDs (0x2E -> 0x6E ACK):")
                    for (w in ecuWrites) {
                        val interp = interpretDidPayload(w.did, w.lastPayload)
                        val interpStr = if (interp != null) " [$interp]" else ""
                        val cmdStr = if (w.command.isNotBlank()) " -> Command: ${w.command}" else ""
                        sb.appendLine("    * DID 0x${w.did} (${w.didName}): Written Payload=${w.lastPayload}$interpStr$cmdStr (Count: ${w.count})")
                    }
                } else {
                    sb.appendLine("  - WRITTEN DIDs: Nessuno")
                }

                val ecuRejects = rejectedServices.values.filter { it.ecuHeader == ecu }
                if (ecuRejects.isNotEmpty()) {
                    sb.appendLine("  - REJECTED SERVICES (0x7F NRC):")
                    for (rej in ecuRejects) {
                        val sidName = SID_MAP[rej.sid] ?: "SID 0x${rej.sid}"
                        val paramStr = if (rej.didOrParam != "-") " (DID/Param 0x${rej.didOrParam})" else ""
                        sb.appendLine("    * $sidName$paramStr: [NRC 0x${rej.nrc}: ${rej.nrcName}] (Attempts: ${rej.count})")
                    }
                }

                val ecuCmds = discoveredCommands.values.filter { it.ecuHeader == ecu }
                if (ecuCmds.isNotEmpty()) {
                    sb.appendLine("  - COMANDI E PROCEDURE SCOPERTE:")
                    for (c in ecuCmds) {
                        sb.appendLine("    * [${c.commandType}] ${c.description} (Count: ${c.count})")
                    }
                }

                sb.appendLine()
            }

            sb.appendLine("================================================================")
            return sb.toString()
        }

        @Synchronized
        fun clear() {
            readDids.clear()
            writtenDids.clear()
            rejectedServices.clear()
            discoveredCommands.clear()
            activeEcus.clear()
        }
    }
}
