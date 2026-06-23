package com.obdiitools.data

import com.obdiitools.bluetooth.OBDRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VinRepository @Inject constructor(
    private val obdRepository: OBDRepository,
) {
    @Volatile private var cached: VinInfo? = null
    private val mutex = Mutex()

    suspend fun fetchAndDecodeVin(ecuAddress: String = "7E0"): VinInfo? {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }  // double-check after acquiring lock
            obdRepository.pausePolling()
            try {
                delay(150)  // let any in-flight poll complete before we take the serial port
                val vin = fetchVinString(ecuAddress) ?: return@withLock null
                val info = decodeViaNhtsa(vin) ?: VinInfo(vin = vin)
                cached = info
                info
            } finally {
                obdRepository.resumePolling()
            }
        }
    }

    private suspend fun fetchVinString(ecuAddress: String): String? {
        // Try OBD-II Mode 09 PID 02 first — legislated on all OBD-II vehicles.
        // UDS DID F190 works on some ECUs (e.g. Nissan UDS-only modules) but is
        // not universally supported, so it is the fallback.
        val mode09Vin = fetchMode09Vin()
        if (mode09Vin != null) return mode09Vin

        val udsResponse = obdRepository.queryUds(ecuAddress, "F190")
        if (udsResponse.isSuccess) return parseVinFromHex(udsResponse.dataHex)
        return null
    }

    private suspend fun fetchMode09Vin(): String? {
        // Mode 09 VIN is a multi-frame ISO-TP exchange; allow 5 s for the ELM327 to
        // finish protocol detection, reassemble the frames, and return the full response.
        val lines = obdRepository.queryRawLines("0902", timeoutMs = 5000)
        val result = parseMode09Lines(lines)
        return extractVinFromHexStream(result.allHex)
    }

    private data class Mode09Result(val allHex: String, val isFrameFormat: Boolean)

    private fun parseMode09Lines(rawLines: List<String>): Mode09Result {
        val cleaned = rawLines.map { it.replace(" ", "").uppercase().trim() }
        // ELM327 multi-frame format: lines prefixed with a frame index like "0:", "1:", "2:"
        // Frame 0 contains the 4902 mode/PID header; continuation frames are raw VIN bytes.
        val framePattern = Regex("^([0-9A-F]):(.+)$")
        val isFrameFormat = cleaned.any { framePattern.matches(it) }

        val allHex = if (isFrameFormat) {
            cleaned.mapNotNull { line ->
                val m = framePattern.matchEntire(line) ?: return@mapNotNull null
                val frameNum = m.groupValues[1].toIntOrNull(16) ?: return@mapNotNull null
                val data = m.groupValues[2]
                when {
                    frameNum == 0 && data.startsWith("4902") -> data.substring(4) // strip 4902 + keep NODI (filtered later)
                    frameNum == 0 -> null
                    else -> data  // continuation frames: raw VIN bytes, no header
                }
            }.joinToString("")
        } else {
            cleaned
                .filter { it.length > 4 && it.startsWith("4902") }
                .joinToString("") { it.substring(4) }
        }
        return Mode09Result(allHex, isFrameFormat)
    }

    private fun extractVinFromHexStream(hexData: String): String? {
        // Walk byte-by-byte keeping only printable ASCII (0x20–0x7E).
        // NODI and ISO-TP sequence bytes (< 0x20) are silently discarded.
        val vinChars = buildString {
            var i = 0
            while (i + 1 < hexData.length && length < 17) {
                val b = hexData.substring(i, i + 2).toIntOrNull(16) ?: break
                if (b in 0x20..0x7E) append(b.toChar())
                i += 2
            }
        }
        return vinChars.takeIf { it.length == 17 }
    }

    fun clearCache() { cached = null }

    suspend fun runDiagnostic(ecuAddress: String = "7E0"): VinDiagnostic = withContext(Dispatchers.IO) {
        var diag = VinDiagnostic()
        obdRepository.pausePolling()
        try {
            delay(150)
            // ── Mode 09 PID 02 ───────────────────────────────────────────────
            val rawLines = obdRepository.queryRawLines("0902", timeoutMs = 5000)
            val mode09Result = parseMode09Lines(rawLines)
            diag = diag.copy(
                mode09RawLines    = rawLines,
                mode09FrameFormat = mode09Result.isFrameFormat,
                mode09FilteredHex = mode09Result.allHex,
                mode09ParsedVin   = extractVinFromHexStream(mode09Result.allHex),
            )

            // ── UDS DID F190 ─────────────────────────────────────────────────
            diag = diag.copy(udsAttempted = true)
            val udsRaw = obdRepository.queryRawUds(ecuAddress, "F190")
            val udsResponse = com.obdiitools.obd.UdsResponse.parse(ecuAddress, "F190", udsRaw)
            val udsVin = if (udsResponse.isSuccess) parseVinFromHex(udsResponse.dataHex) else null
            diag = diag.copy(udsRaw = udsRaw, udsParsedVin = udsVin)

            // ── Select VIN ────────────────────────────────────────────────────
            val vin = diag.mode09ParsedVin ?: udsVin
            diag = diag.copy(vinSelected = vin)

            // ── NHTSA VPIC ───────────────────────────────────────────────────
            if (vin != null) {
                diag = diag.copy(nhtsaAttempted = true)
                try {
                    val url = URL("https://vpic.nhtsa.dot.gov/api/vehicles/decodevin/$vin?format=json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout   = 6000
                    val status = conn.responseCode
                    diag = diag.copy(nhtsaHttpStatus = status)
                    if (status == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val results = JSONObject(body).getJSONArray("Results")
                        fun field(v: String): String? {
                            for (i in 0 until results.length()) {
                                val o = results.getJSONObject(i)
                                if (o.getString("Variable") == v) {
                                    val s = o.getString("Value")
                                    return if (s == "null" || s.isBlank()) null else s
                                }
                            }
                            return null
                        }
                        diag = diag.copy(
                            nhtsaMake  = field("Make"),
                            nhtsaModel = field("Model"),
                            nhtsaYear  = field("Model Year"),
                        )
                    }
                } catch (e: Exception) {
                    diag = diag.copy(nhtsaError = e.message ?: "Unknown error")
                }
            }
        } finally {
            obdRepository.resumePolling()
        }
        diag
    }

    private fun parseVinFromHex(dataHex: String): String? {
        if (dataHex.length < 34) return null // 17 bytes = 34 hex chars
        return buildString {
            for (i in 0 until 34 step 2) {
                val byte = dataHex.substring(i, i + 2).toIntOrNull(16) ?: return null
                if (byte in 0x20..0x7E) append(byte.toChar())
            }
        }.takeIf { it.length == 17 }
    }

    private suspend fun decodeViaNhtsa(vin: String): VinInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://vpic.nhtsa.dot.gov/api/vehicles/decodevin/$vin?format=json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val results = JSONObject(body).getJSONArray("Results")
            fun field(variable: String): String {
                for (i in 0 until results.length()) {
                    val obj = results.getJSONObject(i)
                    if (obj.getString("Variable") == variable) {
                        val v = obj.getString("Value")
                        return if (v == "null" || v.isBlank()) "" else v
                    }
                }
                return ""
            }
            VinInfo(
                vin   = vin,
                make  = field("Make"),
                model = field("Model"),
                year  = field("Model Year"),
                trim  = field("Trim"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
