package com.obdiitools.obd

/**
 * Decoded read-only live data for the Nissan CVT7 transmission controller.
 * Field decoding remains fail-closed until every offset and scale is verified.
 */
data class NissanCvt7DiscoveryResult(
    val ecuAddress: String,
    val liveData: NissanCvt7LiveData?,
)

data class NissanCvt7LiveData(
    val vehicleSpeedKph: Int,
    val engineRpm: Int,
    val fluidTemperatureC: Int,
    val gearRatio: Float,
    val primaryPressureMpa: Float,
    val secondaryPressureMpa: Float,
    val targetSecondaryPressureMpa: Float,
    val linePressureMpa: Float,
)

object NissanCvt7Protocol {
    fun isExpectedTcmIdentity(lines: List<String>): Boolean {
        val hex = lines.joinToString("") { it.filterNot(Char::isWhitespace).uppercase() }
            .filter { it in "0123456789ABCDEF" }
        val marker = hex.indexOf("62F187")
        if (marker < 0) return false
        val payload = hex.substring(marker + 6)
        val ascii = payload.chunked(2).mapNotNull { pair ->
            if (pair.length != 2) null else pair.toIntOrNull(16)?.toChar()
        }.joinToString("")
        val normalized = ascii.filter { it.isLetterOrDigit() }.uppercase()
        return normalized.contains("31036") && normalized.contains("4AT0C")
    }

    fun hasPositiveResponse(lines: List<String>, responsePrefix: String): Boolean {
        if (lines.isEmpty() || !responsePrefix.matches(Regex("(?i)^[0-9A-F]{4,6}$"))) {
            return false
        }
        val compact = lines.joinToString("") { it.filterNot(Char::isWhitespace).uppercase() }
        return compact.contains(responsePrefix.uppercase())
    }

    /**
     * The captured 0x21/0x01 reply is a 0x7A-byte multi-frame response. The
     * previous 76-byte map demonstrably decoded unrelated bytes as live values.
     * Do not publish telemetry until the complete response's field map is verified.
     */
    fun parseLiveData(lines: List<String>): NissanCvt7LiveData? {
        if (!hasPositiveResponse(lines, "6101")) return null
        return null
    }
}
