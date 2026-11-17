package com.obdiitools.obd

data class CanFrame(
    val id: String,
    val data: List<Int>,
    val rawLine: String,
    val receivedAt: Long = System.nanoTime(),
) {
    val dataHex: String
        get() = data.joinToString(" ") { "%02X".format(it) }

    val dataAscii: String
        get() = data.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")

    companion object {
        fun parse(line: String): CanFrame? {
            val tokens = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (tokens.size < 2) return null
            val id = tokens[0].uppercase()
            if (id.length !in 3..8 || !id.all { it in '0'..'9' || it in 'A'..'F' }) return null
            val numericId = id.toLongOrNull(16) ?: return null
            if (numericId > 0x1FFFFFFF || (id.length == 3 && numericId > 0x7FF)) return null
            val byteTokens = tokens.drop(1)
            if (byteTokens.isEmpty() || byteTokens.size > 8 || byteTokens.any { it.length != 2 || !it.all { c -> c in '0'..'9' || c.uppercaseChar() in 'A'..'F' } }) return null
            return CanFrame(id, byteTokens.map { it.toInt(16) }, line.trim())
        }
    }
}

enum class KnownEcu(val address: String, val label: String, val description: String) {
    BROADCAST("7DF",  "OBD2 Broadcast",  "Standard OBD2 — queries all ECUs"),
    ENGINE("7E0",     "Engine (PCM)",     "Powertrain / engine control module"),
    TRANSMISSION("7E1", "Transmission",  "Transmission control unit"),
    ABS("7B0",        "ABS / Brakes",    "Anti-lock brake / stability system"),
    BCM("7A0",        "Body Control",    "Body control module (locks, lights)"),
    AIRBAG("740",     "Airbag / SRS",    "Supplemental restraint system"),
    CLUSTER("720",    "Instrument Cluster", "Dashboard / gauge cluster"),
    HVAC("7C0",       "Climate Control", "Heating, ventilation, A/C"),
    CUSTOM("",        "Custom…",         "Enter a specific ECU address"),
}

data class UdsResponse(
    val ecuAddress: String,
    val did: String,
    val dataHex: String,
    val isSuccess: Boolean,
    val errorCode: String? = null,
    val errorDescription: String? = null,
) {
    val dataBytes: List<Int>
        get() = dataHex.chunked(2).mapNotNull { it.toIntOrNull(16) }

    val asAscii: String
        get() = dataBytes.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")

    val displayHex: String
        get() = dataBytes.joinToString(" ") { "%02X".format(it) }

    companion object {
        fun parse(ecuAddress: String, did: String, raw: String): UdsResponse {
            // ELM adapters may return one logical response over several indexed
            // lines (for example, "0:62F190..." followed by "1:...").
            // Remove only the single-hex-digit line index, retain each payload,
            // and concatenate in adapter order before validating the response.
            val rawLines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val cleaned = rawLines.mapNotNull { line ->
                val withoutIndex = line.replace(Regex("(?i)^[0-9A-F]:"), "")
                val compact = withoutIndex.filterNot { it.isWhitespace() }.uppercase()
                when {
                    compact.isNotEmpty() && compact.all { it in '0'..'9' || it in 'A'..'F' } -> compact
                    compact.contains("NODATA") || compact.contains("NO DATA") ||
                        compact.contains("ERROR") || compact.contains("?") -> compact
                    else -> null // Ignore adapter chatter such as SEARCHING...
                }
            }.joinToString("")
            val validHex = cleaned.isNotEmpty() && cleaned.length % 2 == 0 &&
                cleaned.all { it in '0'..'9' || it in 'A'..'F' }
            val expectedPrefix = "62" + did.uppercase()
            return when {
                cleaned.isEmpty() ->
                    UdsResponse(ecuAddress, did, "", false, "NC", "No response")
                cleaned.startsWith(expectedPrefix) && validHex && cleaned.length >= 6 ->
                    UdsResponse(ecuAddress, did, cleaned.substring(6), true)
                cleaned.startsWith("7F") && validHex && cleaned.length >= 6 -> {
                    val nrc = cleaned.substring(4, 6)
                    UdsResponse(ecuAddress, did, cleaned, false, nrc, nrcDescription(nrc))
                }
                cleaned.contains("NODATA") || cleaned.contains("NO") ->
                    UdsResponse(ecuAddress, did, "", false, "ND", "No data — ECU did not respond")
                cleaned.contains("ERROR") || cleaned.contains("?") ->
                    UdsResponse(ecuAddress, did, "", false, "ERR", "Communication error")
                else ->
                    UdsResponse(ecuAddress, did, "", false, "UNK", "Unexpected or malformed response")
            }
        }

        fun nrcDescription(nrc: String): String = when (nrc.uppercase()) {
            "10" -> "General Reject"
            "11" -> "Service Not Supported"
            "12" -> "Sub-function Not Supported"
            "13" -> "Incorrect Message Length"
            "22" -> "Conditions Not Correct"
            "31" -> "Request Out of Range"
            "33" -> "Security Access Denied"
            "35" -> "Invalid Key"
            "36" -> "Exceeded Attempt Limit"
            "37" -> "Required Time Delay Not Expired"
            else -> "Error (NRC 0x$nrc)"
        }
    }
}

/**
 * Interprets read-only ECU discovery probes. Accepts either a UDS default-session
 * response (10 01 -> 50 01) or a legislated OBD PID-support response (01 00 -> 41 00).
 * Negative responses are evidence of a responding ECU only when they identify
 * one of those requested services.
 */
fun parseEcuScanResult(address: String, raw: String): EcuScanResult? {
    val frames = raw.lineSequence().map { line ->
        line.trim().replace(Regex("(?i)^[0-9A-F]:"), "")
            .filterNot { it.isWhitespace() }.uppercase()
    }.filter { it.isNotEmpty() }.toList()

    if (frames.any { it.startsWith("5001") || it.startsWith("4100") }) {
        return EcuScanResult(address, hasPositiveResponse = true)
    }

    val negative = frames.firstOrNull { it.startsWith("7F10") || it.startsWith("7F01") }
    if (negative != null && negative.length >= 6) {
        return EcuScanResult(
            address,
            hasPositiveResponse = false,
            nrc = negative.substring(4, 6),
        )
    }
    return null
}

data class EcuScanResult(
    val address: String,
    val hasPositiveResponse: Boolean,
    val nrc: String? = null,
) {
    val moduleName: String? get() = guessModuleName(address)

    val responseLabel: String get() = when {
        hasPositiveResponse -> "READY"
        nrc != null         -> "NRC $nrc"
        else                -> "PRESENT"
    }
}

fun guessModuleName(address: String): String? = when (address.uppercase()) {
    "7DF" -> "OBD2 Broadcast"
    "7E0" -> "Engine (PCM)"
    "7E1" -> "Transmission (TCM)"
    "7E2" -> "HV Battery / Motor"
    "7B0" -> "ABS / Stability Control"
    "720" -> "Instrument Cluster"
    "726" -> "BCM (Ford)"
    "728" -> "HVAC (Ford)"
    "730" -> "Gateway (Ford)"
    "736" -> "Restraints (Ford)"
    "740" -> "Airbag / SRS"
    "742" -> "BCM (Nissan / Hyundai)"
    "743" -> "Combination Meter (Nissan)"
    "744" -> "HVAC (Nissan)"
    "745" -> "Navigation (Nissan)"
    "746" -> "EPS / Power Steering (Nissan)"
    "747" -> "4WD Control (Nissan)"
    "748" -> "ABS (Nissan)"
    "749" -> "TPMS (Nissan)"
    "750" -> "Transmission (alt)"
    "752" -> "BCM (Toyota)"
    "760" -> "BCM (Ford alt)"
    "770" -> "Gateway"
    "7A0" -> "Body Control"
    "7C0" -> "Climate Control"
    "7D0" -> "Body ECU (Toyota)"
    else  -> null
}

val COMMON_DIDS = listOf(
    "F190" to "Vehicle Identification Number (VIN)",
    "F18C" to "ECU Serial Number",
    "F187" to "Spare Part Number",
    "F189" to "Software Version Number",
    "F186" to "Active Diagnostic Session",
    "F18A" to "System Supplier Identifier",
    "F197" to "System Name / Engine Type",
    "F101" to "Software Fingerprint",
)


/**
 * Returns a VIN only when a successful UDS F190 response contains a valid
 * 17-character VIN. F190 is optional, so null means no verified VIN was read.
 */
fun parseUdsVin(response: UdsResponse): String? {
    if (!response.isSuccess) return null
    val vin = response.dataBytes.take(17).mapNotNull { byte ->
        if (byte in 0x20..0x7E) byte.toChar() else null
    }.joinToString("")
    return vin.takeIf { it.matches(Regex("^[A-HJ-NPR-Z0-9]{17}$")) }
}
