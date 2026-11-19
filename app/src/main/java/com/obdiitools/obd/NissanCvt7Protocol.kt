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

data class NissanCvt7PollResult(
    val sample: NissanCvt7LiveData?,
    val rawResponse: String,
    val parseStatus: String,
)

data class NissanCvt7LiveData(
    val vehicleSpeedKph: Int,
    val engineRpm: Int,
    val slipRpm: Int,
    val fluidTemperatureC: Int,
    val gearRatio: Float,
    val primaryPressureMpa: Float,
    val secondaryPressureMpa: Float,
    val targetSecondaryPressureMpa: Float,
    val linePressureMpa: Float,
    val accelerator: Float,
    val torqueConverterRatio: Float,
    val cvtTemperatureCount: Int,
    val batteryVoltage: Float,
    val lockupCurrentAmps: Float,
    val luPressureMpa: Float,
    val selectorPosition: String,
    val virtualGear: Int,
    val dGearRatio: Float,
    val gForce: Float,
    val engineTorqueNm: Float,
    val enginePowerHp: Int,
    val outputTorqueNm: Int,
    val selectorStatusFlag: Boolean,
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
     * Parses the Nissan CVT7 read-DTC response to request 19 02 2F.
     *
     * CVTz50 sends 19022F to 7E1 and expects 5902. Its CVT7 parser skips the
     * one-byte DTC status availability mask, then reads each four-byte record
     * as a two-byte SAE DTC followed by two status bytes. The status bytes are
     * not interpreted here; CVTz50 also ignores them when listing codes.
     *
     * Returns null for no/negative/malformed responses and an empty list for a
     * valid response containing no stored codes.
     */
    fun parseCvtDtcResponse(lines: List<String>): List<String>? {
        if (lines.isEmpty()) return null

        val response = buildString {
            lines.forEachIndexed { index, rawLine ->
                var line = rawLine.filterNot(Char::isWhitespace).uppercase()
                if (index == 0) line = line.removePrefix("07A")
                line = line.replace(Regex("^[0-9A-F]:"), "")
                if (line.isEmpty() || line == "19022F" ||
                    line.contains("NODATA") || line.contains("NO DATA") ||
                    line.contains("ERROR") || line.contains("?")) {
                    return@forEachIndexed
                }
                if (line.all { it in "0123456789ABCDEF" }) append(line)
            }
        }

        if (!response.startsWith("5902") || response.length % 2 != 0) return null
        val data = response.substring(4)
        if (data.length < 2) return null // status availability mask is required
        val records = data.substring(2)
        if (records.length % 8 != 0) return null

        return records.chunked(8).mapNotNull { record ->
            val dtcBytes = record.substring(0, 4)
            if (dtcBytes == "0000" || dtcBytes == "FFFF") null
            else decodeDtcBytes(dtcBytes)
        }.distinct()
    }

    private fun decodeDtcBytes(hex: String): String {
        if (hex.length != 4 || !hex.all { it in "0123456789ABCDEF" }) return ""
        val first = hex.substring(0, 2).toInt(16)
        val second = hex.substring(2, 4)
        val family = when ((first shr 6) and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }
        val digit = (first shr 4) and 0x03
        val lowNibble = first and 0x0F
        return "$family$digit${lowNibble.toString(16).uppercase()}$second"
    }

    /**
     * Parses the CVT7 0x21/0x01 reply captured through the ELM327.
     *
     * CVTz50's CVT7 path expects the adapter framing prefix 07A, then numbered
     * response fragments (0: through F:) which are concatenated in order. The
     * resulting payload begins 61 01 and contains at least 83 bytes. Byte 22
     * is the signed SLIP REV value displayed by the reference monitor.
     */
    fun diagnoseLiveData(lines: List<String>): String {
        if (lines.isEmpty()) return "No response lines (timeout/NODATA)."
        val firstLine = lines.first().filterNot(Char::isWhitespace).uppercase()
        if (!firstLine.startsWith("07A")) {
            return "Framing mismatch: first line ${firstLine.take(80)}; expected 07A prefix."
        }
        val payload = buildString {
            lines.forEachIndexed { index, rawLine ->
                var line = rawLine.filterNot(Char::isWhitespace).uppercase()
                if (index == 0) line = line.removePrefix("07A")
                if (line.isEmpty()) return@forEachIndexed
                if (!line.matches(Regex("^(?:[0-9A-F]:)?[0-9A-F]+$"))) return@forEachIndexed
                append(line.replace(Regex("^[0-9A-F]:"), ""))
            }
        }
        if (!payload.startsWith("6101")) {
            return "Unexpected payload prefix: ${payload.take(24).ifBlank { "(empty)" }}; expected 6101."
        }
        if (payload.length % 2 != 0 || !payload.all { it in "0123456789ABCDEF" }) {
            return "Malformed/non-hex payload (length ${payload.length} hex chars)."
        }
        val byteCount = payload.length / 2
        if (byteCount < REQUIRED_RESPONSE_BYTES) {
            return "Short response: $byteCount bytes; need at least $REQUIRED_RESPONSE_BYTES."
        }
        return "Response shape valid; decoder returned no sample (unexpected decode failure)."
    }

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
        // CVT7 selector byte 70 uses the four documented range bits.
        // Repeated Sentra captures show bit 0x04 asserted in L; the reference
        // formatter does not name this bit, so retain the raw byte in diagnostics.
        val selector = when {
            bytes[70] and 0x10 != 0 -> "P"
            bytes[70] and 0x20 != 0 -> "R"
            bytes[70] and 0x40 != 0 -> "N"
            bytes[70] and 0x80 != 0 -> "D"
            bytes[70] and 0x04 != 0 -> "L"
            else -> "Unknown"
        }
        val signedTorqueByte = if (bytes[33] > 127) bytes[33] - 256 else bytes[33]
        val engineTorque = signedTorqueByte * 6.4f
        val engineRpm = bytes[21] shl 5
        val gearRatio = bytes[24] * 0.045f
        val torqueMultiplier = when (selector) {
            "P", "N" -> 0.0f
            "R" -> 1.766f
            "D" -> gearRatio
            else -> 0.0f
        }
        return NissanCvt7LiveData(
            vehicleSpeedKph = bytes[15],
            engineRpm = engineRpm,
            slipRpm = if (bytes[22] > 127) bytes[22] - 256 else bytes[22],
            fluidTemperatureC = bytes[37] - 40,
            gearRatio = gearRatio,
            primaryPressureMpa = bytes[44] * 0.025f,
            secondaryPressureMpa = bytes[36] * 0.025f,
            targetSecondaryPressureMpa = bytes[43] * 0.025f,
            // The reference formatter sources both displayed values from byte 43.
            linePressureMpa = bytes[43] * 0.025f,
            accelerator = bytes[27] / 32f,
            torqueConverterRatio = bytes[35] / 64f,
            cvtTemperatureCount = 255 - bytes[12],
            batteryVoltage = bytes[14] * 20f / 255f,
            lockupCurrentAmps = (bytes[50] * 255 + bytes[51]) / 1000f,
            luPressureMpa = (if (bytes[42] > 127) bytes[42] - 256 else bytes[42]) * 0.01f,
            selectorPosition = selector,
            virtualGear = when {
                gearRatio <= 0.44f -> 6
                gearRatio <= 0.69f -> 5
                gearRatio <= 0.86f -> 4
                gearRatio <= 1.11f -> 3
                gearRatio <= 1.53f -> 2
                else -> 1
            },
            dGearRatio = bytes[40] * 0.04f,
            gForce = (if (bytes[26] > 127) bytes[26] - 256 else bytes[26]) / 32f,
            engineTorqueNm = engineTorque,
            enginePowerHp = ((engineTorque * 1.3596f * engineRpm) / 9549f).toInt(),
            outputTorqueNm = (engineTorque * (bytes[35] / 64f) * torqueMultiplier * 5.173f).toInt(),
            selectorStatusFlag = bytes[70] and 0x08 != 0,
        )
    }
}
