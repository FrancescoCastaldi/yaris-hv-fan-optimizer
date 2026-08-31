package com.yaris.hvfan.obd

object Elm327Protocol {
    val INIT_COMMANDS = listOf(
        "AT Z",       // Reset ELM327
        "AT D",       // Set to defaults
        "AT E0",      // Echo Off
        "AT L0",      // Linefeeds Off
        "AT S0",      // Spaces Off
        "AT H0",      // Headers Off
        "AT SP 6",    // Select ISO 15765-4 CAN 11-bit 500kbaud (Toyota Standard)
        "AT AT 1",    // Adaptive Timing Auto 1
        "AT ST 32"    // Set timeout to ~200ms
    )

    fun cleanResponse(raw: String): String {
        return raw.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("SEARCHING...", "")
            .replace("BUSINIT:OK", "")
            .replace("BUSINIT:...", "")
            .trim()
    }

    fun isError(response: String): Boolean {
        val clean = cleanResponse(response).uppercase()
        return clean.contains("NODATA") ||
               clean.contains("ERROR") ||
               clean.contains("UNABLETOCONNECT") ||
               clean.contains("STOPPED") ||
               clean.contains("TIMEOUT") ||
               clean.contains("CANERROR") ||
               clean.contains("FBERROR")
    }
}
