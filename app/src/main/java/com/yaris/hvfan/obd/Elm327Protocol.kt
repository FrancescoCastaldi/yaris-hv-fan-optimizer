package com.yaris.hvfan.obd

object Elm327Protocol {
    // Sequenza di risveglio da low-power / sleep Vgate iCar Pro
    const val CMD_WAKE_UP = "\r\r"
    const val CMD_WARM_START = "AT WS"
    const val CMD_RESET = "AT Z"
    const val CMD_VOLTAGE = "AT RV"

    // Hardware Flow Control ISO-TP Constants for Denso Battery ECU (7E2 / 7EA)
    const val CMD_FLOW_CONTROL_BATTERY_HEADER = "AT FC SH 7E2"
    const val CMD_FLOW_CONTROL_BATTERY_DATA = "AT FC SD 300000" // Clear To Send (CTS), Block Size 0, Separation Time 0
    const val CMD_FLOW_CONTROL_MODE_CUSTOM = "AT FC SM 1"       // Custom Flow Control mode
    const val CMD_FLOW_CONTROL_MODE_DEFAULT = "AT FC SM 0"      // Standard Flow Control mode

    val INIT_COMMANDS = listOf(
        "AT Z",       // Reset ELM327 / Vgate / STN (gestito con delay speciale)
        "AT D",       // Set to defaults
        "AT E0",      // Echo Off
        "AT L0",      // Linefeeds Off
        "AT S0",      // Spaces Off
        "AT H0",      // Headers Off
        "AT AT 1",    // Standard Adaptive Timing (stabile su multi-frame CAN)
        "AT ST 64",   // Timeout a ~400ms (necessario per UDS 2228C1 Toyota TNGA)
        "AT SP 6",    // Select ISO 15765-4 CAN 11-bit 500kbaud (Toyota Standard)
        "AT CAF 1"    // CAN Auto-Formatting On
    )

    const val PROTOCOL_FALLBACK = "AT SP 0" // Auto-detect protocol if SP 6 fails

    fun cleanResponse(raw: String): String {
        return raw.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("SEARCHING...", "")
            .replace("BUSINIT:OK", "")
            .replace("BUSINIT:...", "")
            .replace("STOPPED", "")
            .trim()
    }

    private val VOLTAGE_REGEX = Regex("""(\d{1,2}\.\d+)""")

    /**
     * Estrae la tensione reale della batteria 12V da risposte AT RV (es. "14.2V", "13.8V", "12.4V").
     */
    fun parseBatteryVoltage(raw: String): Float? {
        val match = VOLTAGE_REGEX.find(raw)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Verifica lo stato READY dell'auto ibrida Toyota XP210 basandosi sulla tensione reale del bus 12V.
     * Quando l'auto è in READY, il convertitore DC-DC dalla batteria HV porta il bus 12V sopra i 13.0V (13.8V - 14.5V).
     */
    fun isVehicleReady(voltage: Float?): Boolean {
        return voltage != null && voltage >= 13.0f
    }

    fun isError(response: String): Boolean {
        val clean = cleanResponse(response).uppercase()
        return clean.isEmpty() ||
               clean.contains("NODATA") ||
               clean.contains("ERROR") ||
               clean.contains("UNABLETOCONNECT") ||
               clean.contains("TIMEOUT") ||
               clean.contains("CANERROR") ||
               clean.contains("FBERROR") ||
               clean.contains("BUFFERFULL") ||
               clean.contains("BUSINIT:ERROR") ||
               clean.contains("?")
    }
}
