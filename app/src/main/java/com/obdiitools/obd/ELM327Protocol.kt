package com.obdiitools.obd

object ELM327Protocol {

    val INIT_COMMANDS = listOf(
        "ATZ",      // Reset
        "ATE0",     // Echo off
        "ATL0",     // Linefeeds off
        "ATS0",     // Spaces off
        "ATH0",     // Headers off
        "ATAT1",    // Adaptive timing mode 1
        "ATSP0",    // Auto select protocol
    )

    object PID {
        const val ENGINE_LOAD   = "0104"
        const val COOLANT_TEMP  = "0105"
        const val STFT_BANK1    = "0106"
        const val LTFT_BANK1    = "0107"
        const val STFT_BANK2    = "0108"
        const val LTFT_BANK2    = "0109"
        const val FUEL_PRESSURE = "010A"
        const val TIMING_ADV    = "010E"
        const val INTAKE_TEMP   = "010F"
        const val MAF           = "0110"
        const val THROTTLE      = "0111"
        const val O2_SENSORS    = "0113"
        const val O2_B1S1       = "0114"
        const val O2_B1S2       = "0115"
        const val O2_B2S1       = "0118"
        const val O2_B2S2       = "0119"
        const val RPM           = "010C"
        const val SPEED         = "010D"
        const val FUEL_LEVEL    = "012F"
        const val BARO_PRESSURE = "0133"
        const val AMBIENT_TEMP  = "0146"
        const val OIL_TEMP      = "015C"
        const val READINESS     = "0101"

        const val GET_STORED_DTC  = "03"
        const val GET_PENDING_DTC = "07"
        const val CLEAR_DTC       = "04"
        const val VOLTAGE         = "ATRV"
    }

    // High-frequency: sent as a single multi-PID request every cycle (~6-7 Hz)
    val FAST_PIDS = listOf(
        PID.RPM, PID.SPEED, PID.THROTTLE, PID.COOLANT_TEMP, PID.ENGINE_LOAD, PID.MAF,
    )

    // Low-frequency: batched separately every 5th cycle (~1 Hz)
    val SLOW_PIDS = listOf(
        PID.INTAKE_TEMP, PID.TIMING_ADV, PID.FUEL_LEVEL, PID.AMBIENT_TEMP,
        PID.OIL_TEMP, PID.BARO_PRESSURE, PID.FUEL_PRESSURE,
        PID.STFT_BANK1, PID.LTFT_BANK1, PID.STFT_BANK2, PID.LTFT_BANK2,
        PID.O2_B1S1, PID.O2_B1S2, PID.O2_B2S1, PID.O2_B2S2,
    )

    // Lookup: PID byte (e.g. "0C") → full PID constant (e.g. "010C")
    private val PID_BYTE_TO_FULL: Map<String, String> by lazy {
        (FAST_PIDS + SLOW_PIDS).associateBy { it.takeLast(2).uppercase() }
    }

    fun buildMultiPidCommand(pids: List<String>): String =
        "01 " + pids.joinToString(" ") { it.takeLast(2) }

    fun parseMultiPidResponse(lines: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for (line in lines) {
            if (line.length < 4) continue
            if (line.substring(0, 2).uppercase() != "41") continue
            val pidByte = line.substring(2, 4).uppercase()
            val fullPid = PID_BYTE_TO_FULL[pidByte] ?: continue
            result[fullPid] = parseResponse(fullPid, line)
        }
        return result
    }

    fun parseResponse(pid: String, rawResponse: String): Any? {
        val cleaned = rawResponse
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()

        if (cleaned.contains("NODATA") || cleaned.contains("ERROR") ||
            cleaned.contains("?") || cleaned.isEmpty()) return null

        return try {
            when (pid) {
                PID.RPM -> {
                    val bytes = extractBytes(cleaned, 2)
                    if (bytes.size < 2) null
                    else ((bytes[0] * 256) + bytes[1]) / 4
                }
                PID.SPEED -> extractBytes(cleaned, 1).firstOrNull()
                PID.COOLANT_TEMP -> {
                    val a = extractBytes(cleaned, 1).firstOrNull() ?: return null
                    a - 40
                }
                PID.INTAKE_TEMP -> {
                    val a = extractBytes(cleaned, 1).firstOrNull() ?: return null
                    a - 40
                }
                PID.THROTTLE -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    (a * 100f) / 255f
                }
                PID.ENGINE_LOAD -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    (a * 100f) / 255f
                }
                PID.FUEL_LEVEL -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    (a * 100f) / 255f
                }
                PID.TIMING_ADV -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    (a / 2f) - 64f
                }
                PID.MAF -> {
                    val bytes = extractBytes(cleaned, 2)
                    if (bytes.size < 2) null
                    else ((bytes[0] * 256) + bytes[1]) / 100f
                }
                PID.OIL_TEMP -> {
                    val a = extractBytes(cleaned, 1).firstOrNull() ?: return null
                    a - 40
                }
                PID.FUEL_PRESSURE -> {
                    val a = extractBytes(cleaned, 1).firstOrNull() ?: return null
                    a * 3
                }
                PID.BARO_PRESSURE -> extractBytes(cleaned, 1).firstOrNull()
                PID.AMBIENT_TEMP -> {
                    val a = extractBytes(cleaned, 1).firstOrNull() ?: return null
                    a - 40
                }
                PID.STFT_BANK1, PID.STFT_BANK2, PID.LTFT_BANK1, PID.LTFT_BANK2 -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    (a - 128f) * 100f / 128f
                }
                PID.O2_B1S1, PID.O2_B1S2, PID.O2_B2S1, PID.O2_B2S2 -> {
                    val a = extractBytes(cleaned, 1).firstOrNull()?.toFloat() ?: return null
                    a / 200f
                }
                PID.VOLTAGE -> {
                    val vStr = cleaned.replace("V", "").trim()
                    vStr.toFloatOrNull()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseDTCs(rawResponse: String, isPending: Boolean = false): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        val cleaned = rawResponse
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()

        if (cleaned.contains("NODATA") || cleaned.contains("NO DATA") ||
            cleaned.contains("ERROR")) return emptyList()

        val responsePrefix = if (isPending) "47" else "43"

        // ATS0 (spaces off) is set during init so responses arrive as a compact hex string
        // (e.g. "43020301042000"). Fall back to space-split for adapters that send spaces.
        val hexData: MutableList<String> = if (cleaned.contains(' ')) {
            cleaned.split(" ")
                .filter { it.length == 2 && it.all { c -> c.isLetterOrDigit() } }
                .toMutableList()
        } else {
            (cleaned.indices step 2)
                .mapNotNull { j ->
                    if (j + 1 < cleaned.length)
                        cleaned.substring(j, j + 2)
                            .takeIf { s -> s.all { c -> c.isLetterOrDigit() } }
                    else null
                }
                .toMutableList()
        }

        // Multi-line CAN responses repeat the frame header (e.g. "43 03 …\n43 00 …").
        // Remove any responsePrefix+count pairs beyond the first so they don't decode as phantom DTCs.
        var fi = 2
        while (fi < hexData.size) {
            if (hexData[fi].equals(responsePrefix, ignoreCase = true)) {
                hexData.removeAt(fi)
                if (fi < hexData.size) hexData.removeAt(fi)
            } else {
                fi++
            }
        }

        var i = 0
        while (i < hexData.size - 1) {
            val byte1Str = hexData[i]
            val byte2Str = hexData[i + 1]

            // CAN Mode 03 response: <mode byte 43> <count byte> <DTC pairs...>
            // Only check at position 0 so legitimate 0x43 DTC data bytes are not skipped.
            if (i == 0 && byte1Str.equals(responsePrefix, ignoreCase = true)) {
                i += 2  // skip mode byte + count byte
                continue
            }

            try {
                val byte1 = byte1Str.toInt(16)
                val byte2 = byte2Str.toInt(16)

                if (byte1 == 0 && byte2 == 0) {
                    i += 2
                    continue
                }

                val code = decodeDTCBytes(byte1, byte2)
                if (code.isNotEmpty()) {
                    dtcs.add(DTC(code = code, isPending = isPending))
                }
            } catch (e: Exception) {
                // skip malformed bytes
            }
            i += 2
        }
        return dtcs
    }

    private fun decodeDTCBytes(byte1: Int, byte2: Int): String {
        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "P"
        }
        val digit1 = (byte1 shr 4) and 0x03
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        return "$prefix$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }

    private fun extractBytes(response: String, count: Int): List<Int> {
        // Skip mode byte (41) + PID byte = 4 hex chars total
        val modeStripped = if (response.length >= 4) response.substring(4) else response
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < modeStripped.length && bytes.size < count) {
            try {
                bytes.add(modeStripped.substring(i, i + 2).toInt(16))
            } catch (e: Exception) {
                break
            }
            i += 2
        }
        return bytes
    }
}
