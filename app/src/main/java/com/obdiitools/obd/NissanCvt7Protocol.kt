package com.obdiitools.obd

/**
 * Read-only Nissan CVT7 response decoding.
 *
 * The byte map is transcribed from the reference application's CVT7 formatter.
 * Offsets are zero-based from the first byte of the positive response, including
 * the leading 0x61 0x01 service/local-identifier bytes.
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
    private const val REQUIRED_RESPONSE_BYTES = 83

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
     * Parses the CVT7 0x21/0x01 reply captured through the ELM327.
     *
     * CVTz50's CVT7 path expects the adapter framing prefix 07A, then numbered
     * response fragments (0: through F:) which are concatenated in order. The
     * resulting payload begins 61 01 and contains at least 83 bytes. If byte 86
     * is present, its value must be <= 4, matching the reference decoder's
     * additional response sanity check.
     */
    fun parseLiveData(lines: List<String>): NissanCvt7LiveData? {
        if (lines.isEmpty()) return null

        val firstLine = lines.first().filterNot(Char::isWhitespace).uppercase()
        if (!firstLine.startsWith("07A")) return null

        val payload = buildString {
            lines.forEachIndexed { index, rawLine ->
                var line = rawLine.filterNot(Char::isWhitespace).uppercase()
                if (index == 0) line = line.removePrefix("07A")
                if (line.isEmpty()) return@forEachIndexed

                // ELM response fragments are labelled with a single hex digit
                // and a colon. Strip only that framing label, not payload bytes.
                if (!line.matches(Regex("^(?:[0-9A-F]:)?[0-9A-F]+$"))) {
                    return@forEachIndexed
                }
                append(line.replace(Regex("^[0-9A-F]:"), ""))
            }
        }

        if (!payload.startsWith("6101") || payload.length % 2 != 0 ||
            !payload.all { it in "0123456789ABCDEF" }) {
            return null
        }

        val bytes = payload.chunked(2).map { it.toInt(16) }
        if (bytes.size < REQUIRED_RESPONSE_BYTES) return null
        return NissanCvt7LiveData(
            vehicleSpeedKph = bytes[15],
            engineRpm = bytes[21] shl 5,
            fluidTemperatureC = bytes[37] - 40,
            gearRatio = bytes[24] * 0.045f,
            primaryPressureMpa = bytes[44] * 0.025f,
            secondaryPressureMpa = bytes[36] * 0.025f,
            targetSecondaryPressureMpa = bytes[43] * 0.025f,
            // The reference formatter sources both displayed values from byte 43.
            linePressureMpa = bytes[43] * 0.025f,
        )
    }
}
