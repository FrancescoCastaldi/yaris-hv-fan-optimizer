package com.yaris.hvfan.obd

object Elm327Protocol {
    // Sequenza di risveglio da low-power / sleep Vgate iCar Pro
    const val CMD_WAKE_UP = "\r\r"
    const val CMD_WARM_START = "AT WS"
    const val CMD_RESET = "AT Z"
    const val CMD_VOLTAGE = "AT RV"
    const val CMD_DEVICE_INFO = "ATI"
    const val CMD_DEVICE_ID_STN = "ST DI"

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
    const val CMD_TIMEOUT_BATTERY_ECU = "AT ST C8"  // ~819 ms per il multi-frame UDS 2228C1
    const val CMD_TIMEOUT_TELEMETRY = "AT ST 32"    // ~205 ms, default ELM327, per il loop rapido

    // Sequenza Dr. Prius universale ad alta compatibilita'
    val INIT_COMMANDS = listOf(
        "AT Z",       // Reset ELM327 / Vgate / STN (gestito con delay speciale)
        "AT E0",      // Echo Off
        "AT L0",      // Linefeeds Off
        "AT S0",      // Spaces Off
        "AT H0",      // Headers Off
        "AT AT 1",    // Standard Adaptive Timing (stabile su multi-frame CAN)
        "AT SP 6",    // ISO 15765-4 CAN 11-bit 500kbaud: il protocollo va scelto prima del timing
        "AT CAF 1",   // CAN Auto-Formatting On
        CMD_AUTO_RECEIVE,      // Azzera eventuali filtri AT CRA residui
        CMD_TIMEOUT_HANDSHAKE  // Finestra ampia per l'handshake sul bus
    )

    const val PROTOCOL_FALLBACK = "AT SP 0" // Auto-detect protocol if SP 6 fails

    fun cleanResponse(raw: String): String {
        val withoutLinePrefixes = raw.replace(Regex("""(?:^|[\r\n\s])[0-9A-Fa-f]{1,2}:\s*"""), " ")
        return withoutLinePrefixes.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("SEARCHING...", "")
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
     * Immune al 100% da banner di versione o firmware del dongle (es. "ELM327 v1.5", "STN1110 v2.2", "v2.2").
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

    fun hasSupportedPidsResponse(response: String): Boolean {
        val clean = cleanResponse(response).uppercase()
        return !isError(clean) && clean.contains("4100")
    }

    /**
     * Verifica se una risposta UDS (ISO 14229) è positiva (non NRC 7F né NODATA/ERROR).
     */
    fun isUdsPositiveResponse(response: String, expectedService: String? = null): Boolean {
        val clean = cleanResponse(response).uppercase()
        if (isError(clean)) return false
        if (clean.contains("7F")) return false // Negative Response Code (NRC)
        if (expectedService != null) {
            val positiveSid = String.format(java.util.Locale.US, "%02X", expectedService.toInt(16) + 0x40)
            return clean.contains(positiveSid)
        }
        return true
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
               clean == "SEARCHING..." ||
               clean == "SEARCHING" ||
               clean.contains("?")
    }
}
