package com.yaris.hvfan.obd

object Elm327Protocol {
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
