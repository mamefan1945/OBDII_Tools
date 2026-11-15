package com.obdiitools.obd

import kotlin.math.roundToInt

/**
 * Decoded read-only live data for the Nissan CVT7 transmission controller.
 *
 * The 0x21/0x01 response contains a 76-byte data block following the
 * positive response identifier (0x61 0x01). Offsets are relative to that
 * data block, not to the service bytes or transport framing.
 */
data class NissanCvt7DiscoveryResult(
    val ecuAddress: String,
    val liveData: NissanCvt7LiveData,
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
    /**
     * Confirms the known TCM family from its read-only spare-part identifier.
     * The DID payload is ASCII in the ECU response; normalize punctuation and
     * whitespace before matching so transport formatting does not matter.
     */
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

    private const val DATA_LENGTH = 76
    private const val RESPONSE_PREFIX = "6101"

    /**
     * Parses ELM327 output with headers disabled and automatic ISO-TP
     * formatting enabled. The transport may prepend a length/PCI byte;
     * locating the service response avoids treating that framing as data.
     */
    fun parseLiveData(lines: List<String>): NissanCvt7LiveData? {
        if (lines.isEmpty()) return null

        // ELM327 adapters may return a multi-frame response as indexed
        // records (0:, 1:, ... F:) preceded by a total-length token (e.g.
        // "07A"). Those transport labels are not response bytes. Strip them
        // before joining the payload; otherwise the 6101 marker is lost.
        val hexLines = lines.mapNotNull { line ->
            var compact = line.filterNot(Char::isWhitespace).uppercase()
            if (compact.matches(Regex("^[0-9A-F]{2,3}$"))) return@mapNotNull null
            compact = compact.replace(Regex("^[0-9A-F]{3}(?=[0-9A-F]:)"), "")
            compact = compact.replace(Regex("^[0-9A-F]:"), "")
            compact.takeIf {
                it.isNotEmpty() && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' }
            }
        }
        if (hexLines.isEmpty()) return null

        val combined = hexLines.joinToString(separator = "")
        val responseAt = combined.indexOf(RESPONSE_PREFIX)
        if (responseAt < 0) return null

        val dataStart = responseAt + RESPONSE_PREFIX.length
        val requiredEnd = dataStart + DATA_LENGTH * 2
        if (combined.length < requiredEnd) return null

        val data = ArrayList<Int>(DATA_LENGTH)
        for (offset in 0 until DATA_LENGTH) {
            val start = dataStart + offset * 2
            data.add(combined.substring(start, start + 2).toIntOrNull(16) ?: return null)
        }

        val speed = data[12]
        val engineRpm = data[7] * 32
        val fluidTemperature = ((data[31] - 12) * 5f / 9f).roundToInt()
        val ratio = data[18] / 100f

        return NissanCvt7LiveData(
            vehicleSpeedKph = speed,
            engineRpm = engineRpm,
            fluidTemperatureC = fluidTemperature,
            gearRatio = ratio,
            primaryPressureMpa = data[30] * 0.025f,
            secondaryPressureMpa = data[29] * 0.025f,
            targetSecondaryPressureMpa = data[38] * 0.025f,
            linePressureMpa = data[37] * 0.025f,
        )
    }
}
