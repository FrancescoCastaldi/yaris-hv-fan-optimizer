package com.yaris.hvfan.obd

object Elm327Protocol {
    // Sequenza di risveglio da low-power / sleep Vgate iCar Pro
    const val CMD_WAKE_UP = "\r\r"
    const val CMD_WARM_START = "AT WS"
    const val CMD_RESET = "AT Z"
    const val CMD_VOLTAGE = "AT RV"
    const val CMD_DEVICE_INFO = "ATI"
    const val CMD_DEVICE_INFO_STI = "STI"
    const val CMD_DEVICE_INFO_AT1 = "AT@1"
    const val CMD_DEVICE_ID_STN = "ST DI"

    const val CMD_ECHO_OFF = "AT E0"
    const val CMD_LINEFEEDS_OFF = "AT L0"
    const val CMD_SPACES_OFF = "AT S0"
    const val CMD_HEADERS_ON = "AT H1"
    const val CMD_HEADERS_OFF = "AT H0"
    const val CMD_ADAPTIVE_TIMING_1 = "AT AT 1"
    const val CMD_PROTOCOL_CAN_11_500 = "AT SP 6"
    const val CMD_CAN_AUTO_FORMAT_ON = "AT CAF 1"
    const val CMD_PROBE_DTC = "03"

    // Hardware Flow Control ISO-TP Constants for Denso Battery ECU (7E2 / 7EA)
    const val CMD_FLOW_CONTROL_BATTERY_HEADER = "AT FC SH 7E2"
    const val CMD_FLOW_CONTROL_BATTERY_DATA = "AT FC SD 300000" // Clear To Send (CTS), Block Size 0, Separation Time 0
    const val CMD_FLOW_CONTROL_MODE_CUSTOM = "AT FC SM 1"       // Custom Flow Control mode
    const val CMD_FLOW_CONTROL_MODE_DEFAULT = "AT FC SM 0"      // Standard Flow Control mode

    // Ripristino del filtro di ricezione automatico: sui cloni ELM327 / Vlinker un AT CRA rimasto
    // attivo risponde OK ma scarta ogni frame in ingresso, producendo NO DATA su qualsiasi PID.
    const val CMD_AUTO_RECEIVE = "AT AR"
    const val CMD_PROTOCOL_NUMBER = "AT DPN"

    // Timeout di ricezione AT ST hh (hh esadecimale x 4.096 ms). Il default di fabbrica ELM327 e'
    // 0x32 (~205 ms): sotto questa soglia la finestra si chiude prima che la ECU Toyota risponda.
    const val CMD_TIMEOUT_HANDSHAKE = "AT ST 96"    // ~614 ms per l'aggancio iniziale del bus
    // ~819 ms (0xC8 x 4.096ms): i cloni ELM327/Vlinker con flow-control ISO-TP carente perdono
    // spesso il completamento del multi-frame UDS 2228C1 quando la finestra ELM interna e' troppo
    // stretta (~410ms con 0x64), producendo NODATA anche se il bus CAN e' sano. Il valore e' stato
    // raddoppiato per dare margine ai frame consecutivi lenti, restando pero' ben al di sotto sia
    // di MAX_PROBE_TIMEOUT_MS (3000ms, discovery) sia di BATTERY_PID_TIMEOUT_MS (4000ms, steady-state)
    // cosi' da lasciare spazio a retry/gestione errori lato BLE.
    const val CMD_TIMEOUT_BATTERY_ECU = "AT ST FF"  // ~1044 ms (0xFF * 4.096ms) per ricezione affidabile multi-frame pacco celle Denso ISO-TP
    const val CMD_TIMEOUT_TELEMETRY = "AT ST 32"    // ~205 ms, default ELM327, per il loop rapido
    const val CMD_TIMEOUT_TELEMETRY_STANDBY = "AT ST 64" // ~410 ms per quadro acceso / non-READY (FIX 5)
    const val CMD_TIMEOUT_ECU_CODING = "AT ST 96"   // ~614 ms per Body, Meter, Aircon e ADAS UDS Mode 21/22/3B

    // Sequenza canonica di handshake standard calibrata per Vgate iCar Pro & Toyota Yaris Hybrid TNGA-B
    val INIT_COMMANDS = listOf(
        CMD_WARM_START,          // AT WS (Warm Start senza drop del socket Bluetooth)
        CMD_ECHO_OFF,            // AT E0
        CMD_LINEFEEDS_OFF,       // AT L0
        CMD_SPACES_OFF,          // AT S0
        CMD_HEADERS_OFF,         // AT H0 (headers off, default canonico compatto - FIX 7)
        CMD_PROTOCOL_CAN_11_500, // AT SP 6 (ISO 15765-4 CAN 11-bit 500kbaud)
        CMD_ADAPTIVE_TIMING_1,   // AT AT 1
        CMD_CAN_AUTO_FORMAT_ON,  // AT CAF 1
        CMD_TIMEOUT_HANDSHAKE    // AT ST 96
    )

    // Alias per retrocompatibilità con test e riferimenti legacy: punta alla lista canonica unificata
    val VGATE_CALIBRATED_INIT_COMMANDS = INIT_COMMANDS

    const val PROTOCOL_FALLBACK = "AT SP 0" // Auto-detect protocol if SP 6 fails

    fun cleanResponse(raw: String): String {
        val withoutLinePrefixes = raw.replace(Regex("""(?:^|[\r\n\s])[0-9A-Fa-f]{1,2}\s*:\s*"""), " ")
        return withoutLinePrefixes.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("SEARCHING...", "")
            .replace("SEARCHING", "")
            .replace("BUSINIT:OK", "")
            .replace("BUSINIT:...", "")
            .replace("STOPPED", "")
            .trim()
    }

    private val VOLTAGE_WITH_UNIT_REGEX = Regex("""(\d{1,2}(?:\.\d+)?)\s*V""", RegexOption.IGNORE_CASE)
    private val GENERIC_DECIMAL_REGEX = Regex("""(\d{1,2}\.\d+)""")

    /**
     * Rileva in modo non distruttivo se l'adattatore supporta nativamente il chipset STN / OBDLink
     * prima di applicare comandi avanzati di Flow Control stile Hybrid Assistant (AT FC SM 1, AT FC SH, AT FC SD).
     * In caso di adapter Vlinker, vLinker MC/FD o cloni ELM327 standard, mantiene rigorosamente il Flow Control
     * automatico di sistema (AT CAF 1) senza inviare comandi AT FC che corrompono il buffer.
     */
    fun isStnHardwareSupported(
        deviceName: String?,
        atiResponse: String?,
        stDiResponse: String?
    ): Boolean {
        val nameUpper = (deviceName ?: "").uppercase()
        val atiUpper = (atiResponse ?: "").uppercase()
        val stDiUpper = (stDiResponse ?: "").uppercase()

        // 1. Esclusione tassativa di adapter Vlinker / vLinker (MC, FD, FS, Android-Vlink)
        // anche se rispondono ad alcune istruzioni STN, il loro buffer CAN si corrompe se si alterano i parametri AT FC
        if (nameUpper.contains("VLINK") || nameUpper.contains("V-LINK") ||
            atiUpper.contains("VLINK") || atiUpper.contains("V-LINK") ||
            stDiUpper.contains("VLINK") || stDiUpper.contains("V-LINK")
        ) {
            return false
        }

        val cleanStDi = cleanResponse(stDiUpper)

        // 2. Cloni ELM327 generici che ritornano '?', errori o stringhe non comprese su comandi STN
        if (stDiUpper.contains("?") || stDiUpper.contains("ERROR") || stDiUpper.contains("ERR") ||
            stDiUpper.contains("UNKNOWN") || stDiUpper.contains("NOT UNDERSTOOD") ||
            stDiUpper.contains("ALERT") || cleanStDi == "STDI" || cleanStDi == "OK" || isError(stDiUpper)
        ) {
            return false
        }

        // 3. Rilevamento chipset Scantool STN nativo (STN11xx, STN21xx) o dispositivo OBDLink originale.
        // Richiede conferma univoca dal firmware del chipset sul comando ST DI:
        return cleanStDi.contains("STN") || cleanStDi.contains("OBDLINK")
    }

    /**
     * Estrae la tensione reale della batteria 12V da risposte AT RV (es. "14.2V", "13.8V", "12.4V", "14V").
     * Immune al 100% da banner di versione o firmware del dongle (es. "ELM327 v1.5", "STN1110 v2.2", "v1.5", "v2.2").
     */
    fun parseBatteryVoltage(raw: String): Float? {
        // 1. Rimuove prefissi o banner firmware contenenti versioni (es. "ELM327 v1.5", "STN1110 v2.2", "v1.5", "v2.2")
        val sanitized = raw
            .replace(Regex("""(?i)\b(?:ELM327|STN\d+|OBDLINK|VLINKER|VERSION|VER)\b[\s]*v?(\d+\.\d+)"""), " ")
            .replace(Regex("""(?i)\bv\d+\.\d+\b"""), " ")

        // 2. Cerca prima con suffisso 'V' (priorita' massima)
        val matchWithUnit = VOLTAGE_WITH_UNIT_REGEX.find(sanitized)
        if (matchWithUnit != null) {
            val v = matchWithUnit.groupValues[1].toFloatOrNull()
            if (v != null && v in 8.0f..18.0f) return v
        }

        // 3. Fallback: cerca decimali che rientrino in un intervallo di tensione plausibile per batteria auto (9.0V - 16.5V)
        val allMatches = GENERIC_DECIMAL_REGEX.findAll(sanitized)
        for (match in allMatches) {
            val v = match.groupValues[1].toFloatOrNull()
            if (v != null && v in 9.0f..16.5f) {
                return v
            }
        }
        return null
    }

    /**
     * Verifica lo stato READY dell'auto ibrida Toyota XP210 basandosi sulla tensione reale del bus 12V.
     * Quando l'auto è in READY, il convertitore DC-DC dalla batteria HV porta il bus 12V sopra i 13.0V (13.8V - 14.5V).
     */
    fun isVehicleReady(voltage: Float?): Boolean {
        return voltage != null && voltage >= 13.0f
    }

    /**
     * Verifica lo stato di Standby dell'auto per evitare oscillazioni rapide (isteresi).
     * Quando l'auto è spenta o in accessori, la batteria 12V scende stabilmente a <= 12.6V.
     */
    fun isVehicleStandby(voltage: Float?): Boolean {
        return voltage != null && voltage <= 12.6f
    }

    /**
     * Isola le righe valide di risposta CAN/UDS da un output grezzo ELM327 multi-riga,
     * scartando banner di sincronizzazione (SEARCHING...) e righe di errore (NO DATA, CAN ERROR)
     * provenienti da ECU secondarie o da timeout parziali del bus.
     */
    fun extractValidFrames(raw: String): List<String> {
        val lines = raw.split('\r', '\n')
            .map { cleanResponse(it).uppercase() }
            .filter { it.isNotEmpty() && !isError(it) }
        if (lines.isNotEmpty()) return lines
        val cleanSingle = cleanResponse(raw).uppercase()
        return if (cleanSingle.isNotEmpty() && !isError(cleanSingle)) listOf(cleanSingle) else emptyList()
    }

    fun hasSupportedPidsResponse(response: String): Boolean {
        val frames = extractValidFrames(response)
        if (frames.any { it.contains("4100") }) return true
        val clean = cleanResponse(response).uppercase()
        return clean.contains("4100") && !clean.startsWith("NO DATA") && !clean.startsWith("NODATA") && !clean.startsWith("CAN ERROR")
    }

    /**
     * Verifica se la risposta al probe di Stadio 1 (010C RPM, 010D Velocità, 0100 PIDs supportati)
     * è positiva (Service 01 -> Response 41 XX), gestendo sia risposte con header (ATH1, es. 7E8 04 41 0C 1F 40)
     * sia senza header (ATH0, es. 41 0C 1F 40 o 41 0D 00), inclusi regimi minimi/fermo (00 00)
     * e risposte multi-ECU dove una centralina secondaria restituisce NO DATA / CAN ERROR.
     */
    fun isStage1PositiveResponse(command: String, response: String): Boolean {
        val cleanCmd = cleanResponse(command).uppercase()
        val frames = extractValidFrames(response)
        if (frames.isNotEmpty()) {
            return frames.any { frame ->
                when {
                    cleanCmd.contains("010C") -> frame.contains("410C")
                    cleanCmd.contains("010D") -> frame.contains("410D")
                    cleanCmd.contains("0100") -> frame.contains("4100")
                    else -> frame.contains("41")
                }
            }
        }
        val clean = cleanResponse(response).uppercase()
        if (isError(clean)) return false
        return when {
            cleanCmd.contains("010C") -> clean.contains("410C")
            cleanCmd.contains("010D") -> clean.contains("410D")
            cleanCmd.contains("0100") -> clean.contains("4100")
            else -> clean.contains("41")
        }
    }

    /**
     * Verifica se la risposta al probe Mode 03 (Request Trouble Codes) è positiva (servizio 43).
     * Gestisce sia risposte con header (ATH1, es. "7E8 06 43 00 00 00 00 00" o First Frame ISO-TP "7E8 10 09 43 04 ...")
     * sia senza header (ATH0, es. "43 00 00..."),
     * e risposte multi-ECU dove una centralina secondaria ritorna NRC (es. 743 03 7F 03 12) mentre il powertrain risponde positivamente.
     */
    fun isMode03Response(response: String): Boolean {
        val cleanAll = cleanResponse(response).uppercase()
        // Su Toyota Yaris TNGA, Mode 03 non è sempre supportato e NODATA è una risposta valida,
        // non un errore di bus CAN.
        if (cleanAll.contains("NODATA")) return true

        val lines = response.split('\r', '\n')
            .map { cleanResponse(it).uppercase() }
            .filter { it.isNotEmpty() && !isError(it) }

        val framesToCheck = if (lines.isNotEmpty()) lines else listOf(cleanAll)

        for (frame in framesToCheck) {
            // Un frame è NRC per Mode 03 se il service byte è 7F seguito dal SID 03
            val isNrc = if (frame.startsWith("7") && frame.length >= 3) {
                frame.matches(Regex("""^7[0-9A-F]{2}(?:1[0-9A-F]{3}|[0-9A-F]{1,2})?7F03.*"""))
            } else {
                frame.startsWith("7F03")
            }
            if (isNrc) {
                continue
            }

            // Un frame è positivo per Mode 03 se il service byte è 43
            // ATH1: CAN ID (7xx) + PCI (SF o FF) + 43
            // ATH0: inizia con 43 (senza CAN ID)
            val isPositive = if (frame.startsWith("7") && frame.length >= 3) {
                frame.matches(Regex("""^7[0-9A-F]{2}(?:1[0-9A-F]{3}|[0-9A-F]{1,2})?43.*"""))
            } else {
                frame.startsWith("43")
            }

            if (isPositive) {
                return true
            }
        }

        return false
    }

    /**
     * Verifica se la stringa contiene un qualsiasi frame CAN hex (es. 7Ex...).
     */
    fun isValidCanResponse(response: String): Boolean {
        val frames = extractValidFrames(response)
        if (frames.isNotEmpty()) {
            return frames.any { it.matches(Regex("""^[0-9A-F]{3,}.*""")) }
        }
        val clean = cleanResponse(response).uppercase()
        if (isError(clean) && !clean.contains("NODATA")) return false
        return clean.matches(Regex("""^[0-9A-F]{3,}.*"""))
    }

    /**
     * Verifica se una risposta UDS (ISO 14229) è positiva.
     * Isola il byte di servizio della risposta per verificare che non sia un NRC (0x7F all'inizio del payload)
     * e permette la presenza legittima del valore 0x7F nei dati della centralina (temperature, impostazioni di coding, ecc.).
     * Riconosce risposte positive per SID specifici:
     * - 0x50 (DiagnosticSessionControl, es. 5003 o 5001)
     * - 0x62 (ReadDataByIdentifier)
     * - 0x6E (WriteDataByIdentifier)
     * - 0x7E (TesterPresent)
     */
    fun isUdsPositiveResponse(response: String, expectedService: String? = null): Boolean {
        val targetPositiveSid = expectedService?.let { exp ->
            val cleanExp = exp.trim().uppercase()
            // If full request starting with service (e.g. "1003", "10", "22", "2E", "3E", "3E00")
            val baseServiceHex = if (cleanExp.length >= 2) cleanExp.take(2) else cleanExp
            baseServiceHex.toIntOrNull(16)?.let {
                String.format(java.util.Locale.US, "%02X", it + 0x40)
            }
        }

        val lines = response.split('\r', '\n')
            .map { it.replace(">", "").trim() }
            .filter { it.isNotEmpty() && !isError(it) }

        val linesToCheck = if (lines.isNotEmpty()) {
            lines
        } else {
            val clean = cleanResponse(response).uppercase()
            if (isError(clean)) return false
            listOf(clean)
        }

        for (line in linesToCheck) {
            val tokens = line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val sid = when {
                tokens.size >= 4 && tokens[0].length == 3 && tokens[0].all { it in "0123456789ABCDEFabcdef" } &&
                    tokens[1].matches(Regex("""(?i)^1[0-9A-F]$""")) -> {
                    // Formato con header ATH1 con spazi, First Frame ISO-TP: [CAN_ID] [1x] [len] [SID] ...
                    tokens[3].uppercase()
                }
                tokens.size >= 3 && tokens[0].length == 3 && tokens[0].all { it in "0123456789ABCDEFabcdef" } -> {
                    // Formato con header ATH1 con spazi, Single Frame: [CAN_ID] [DLC/PCI] [SID] ...
                    tokens[2].uppercase()
                }
                tokens.isNotEmpty() && tokens[0].matches(Regex("""(?i)^7[0-9A-F]{2}(?:1[0-9A-F]{3}|[0-9A-F]{1,2})([0-9A-F]{2}).*""")) -> {
                    // Formato compatto con header CAN 7xx (Single Frame o First Frame)
                    val match = Regex("""(?i)^7[0-9A-F]{2}(?:1[0-9A-F]{3}|[0-9A-F]{1,2})([0-9A-F]{2}).*""").find(tokens[0])
                    match?.groupValues?.get(1)?.uppercase() ?: ""
                }
                tokens.isNotEmpty() -> {
                    // Formato senza header (ATH0): i primi 2 caratteri sono il SID
                    tokens[0].take(2).uppercase()
                }
                else -> ""
            }

            if (sid.isEmpty() || sid == "7F") {
                continue
            }

            if (targetPositiveSid != null) {
                if (sid == targetPositiveSid) {
                    return true
                }
            } else {
                val sidInt = sid.toIntOrNull(16)
                if (sidInt != null && (sidInt in 0x40..0x7E || sid in listOf("50", "62", "6E", "7E"))) {
                    return true
                }
            }
        }

        return false
    }

    // Negative Response Codes (NRC) UDS ISO 14229 / ISO 15765-4
    const val NRC_SERVICE_NOT_SUPPORTED = "11"
    const val NRC_SUB_FUNCTION_NOT_SUPPORTED = "12"
    const val NRC_CONDITIONS_NOT_CORRECT = "22"
    const val NRC_REQUEST_SEQUENCE_ERROR = "24"
    const val NRC_REQUEST_OUT_OF_RANGE = "31"
    const val NRC_RESPONSE_PENDING = "78"

    data class UdsNrcResponse(
        val serviceId: String,
        val nrc: String
    )

    /**
     * Isola ed estrae un Negative Response Code UDS (ISO 14229 / ISO 15765-4)
     * nel formato "7F <ServiceId> <NRC>". Gestisce sia risposte compatte (ATH0)
     * sia formati con header CAN (ATH1, es. 7EA 03 7F 22 11 o 7EA037F2211) e multi-frame.
     */
    fun extractUdsNrc(response: String): UdsNrcResponse? {
        val lines = response.split('\r', '\n')
            .map { it.replace(">", "").trim() }
            .filter { it.isNotEmpty() }

        val linesToCheck = if (lines.isNotEmpty()) lines else listOf(cleanResponse(response).uppercase())

        for (line in linesToCheck) {
            val cleanLine = cleanResponse(line).uppercase()
            if (!cleanLine.contains("7F")) continue

            // 1. Linea tokenizzata con spazi (es. "7EA 03 7F 22 11" o "7F 22 11")
            val tokens = line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                val idx7F = tokens.indexOfFirst { it.equals("7F", ignoreCase = true) }
                if (idx7F >= 0 && tokens.size > idx7F + 2) {
                    val sid = tokens[idx7F + 1].uppercase()
                    val nrc = tokens[idx7F + 2].uppercase()
                    if (sid.length == 2 && nrc.length == 2 &&
                        sid.all { it in "0123456789ABCDEF" } &&
                        nrc.all { it in "0123456789ABCDEF" }
                    ) {
                        return UdsNrcResponse(serviceId = sid, nrc = nrc)
                    }
                }
            }

            // 2. Linea compatta con header CAN 7xx (es. "7EA037F2211" o "7E8037F0111")
            val canHeaderMatch = Regex("""(?i)^7[0-9A-F]{2}(?:1[0-9A-F]{3}|[0-9A-F]{1,2})?7F([0-9A-F]{2})([0-9A-F]{2})""").find(cleanLine)
            if (canHeaderMatch != null) {
                return UdsNrcResponse(
                    serviceId = canHeaderMatch.groupValues[1].uppercase(),
                    nrc = canHeaderMatch.groupValues[2].uppercase()
                )
            }

            // 3. Linea compatta senza header (es. "7F2211")
            val rawNrcMatch = Regex("""(?i)^7F([0-9A-F]{2})([0-9A-F]{2})""").find(cleanLine)
            if (rawNrcMatch != null) {
                return UdsNrcResponse(
                    serviceId = rawNrcMatch.groupValues[1].uppercase(),
                    nrc = rawNrcMatch.groupValues[2].uppercase()
                )
            }
        }

        return null
    }

    /**
     * Restituisce una descrizione chiara e orientata all'utente per i codici NRC UDS ISO 14229.
     */
    fun getUdsNrcDescription(nrc: String): String {
        return when (nrc.uppercase()) {
            NRC_SERVICE_NOT_SUPPORTED -> "Servizio diagnostico non supportato dalla centralina (NRC 0x11)"
            NRC_SUB_FUNCTION_NOT_SUPPORTED -> "Sotto-funzione non supportata dalla centralina (NRC 0x12)"
            NRC_CONDITIONS_NOT_CORRECT -> "Condizioni non corrette: veicolo non pronto, assicurarsi che l'auto sia in READY, con tutte le portiere chiuse e cambio in P (NRC 0x22)"
            NRC_REQUEST_SEQUENCE_ERROR -> "Errore di sequenza nella richiesta diagnostica (NRC 0x24)"
            NRC_REQUEST_OUT_OF_RANGE -> "Parametro fuori limite o non valido (NRC 0x31)"
            NRC_RESPONSE_PENDING -> "Risposta centralina in elaborazione (NRC 0x78)"
            else -> "Risposta negativa centralina NRC 0x$nrc"
        }
    }

    fun isError(response: String): Boolean {
        val clean = cleanResponse(response).uppercase()
        return clean.isEmpty() ||
               clean.contains("NODATA") ||
               clean.contains("ERROR") ||
               clean.contains("ERR") ||
               clean.contains("UNABLETOCONNECT") ||
               clean.contains("TIMEOUT") ||
               clean.contains("CANERROR") ||
               clean.contains("FBERROR") ||
               clean.contains("BUFFERFULL") ||
               clean.contains("BUSINIT:ERROR") ||
               clean.contains("BUSINITERROR") ||
               clean.contains("NOTUNDERSTOOD") ||
               clean.contains("STOPPED") ||
               clean.contains("BUSBUSY") ||
               clean.contains("BUSERROR") ||
               clean.contains("ALERT") ||
               clean.contains("LVRESET") ||
               clean == "SEARCHING..." ||
               clean == "SEARCHING" ||
               clean.contains("?")
    }
}
