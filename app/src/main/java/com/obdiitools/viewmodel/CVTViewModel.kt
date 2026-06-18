package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.CVTData
import com.obdiitools.obd.CVTDataSource
import com.obdiitools.obd.CVTReading
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.ELM327Protocol
import com.obdiitools.obd.LockupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class CVTViewModel @Inject constructor(
    private val repository: OBDRepository,
    private val prefsRepository: PreferencesRepository,
) : ViewModel() {

    private val _cvtData = MutableStateFlow(CVTData())
    val cvtData: StateFlow<CVTData> = _cvtData.asStateFlow()

    private val _sourceDescription = MutableStateFlow("Probing vehicle capabilities…")
    val sourceDescription: StateFlow<String> = _sourceDescription.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val userPreferences: StateFlow<UserPreferences> = prefsRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private var pollJob: Job? = null

    // Discovered PIDs (null = not supported by this vehicle)
    private var inputShaftPid: String? = null
    private var cvtTempPid: String? = null
    private var probeComplete = false

    // Lockup inference — rolling window of (rpm, speed) samples
    private data class Sample(val rpm: Int, val speed: Int)
    private val window = ArrayDeque<Sample>(WINDOW_SIZE + 1)

    fun start() {
        if (!repository.isConnected) return
        repository.pausePolling()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            delay(350)  // let the main polling loop wind down
            if (!probeComplete) {
                probe()
                probeComplete = true
            }
            poll()
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        probeComplete = false
        _cvtData.value = CVTData()
        repository.resumePolling()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Probe — runs once on first start to discover supported optional PIDs
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun probe() {
        // Tier 2: try extended Mode 01 PIDs some manufacturers use for
        // transmission shaft speed (same 2-byte RPM formula as PID 0x0C).
        for (pid in INPUT_SHAFT_CANDIDATES) {
            val raw = repository.queryRaw(pid)
            if (!isNoData(raw) && parseShaftRpm(raw) != null) {
                inputShaftPid = pid
                break
            }
        }

        // CVT temp: standard engine oil temp PID (some vehicles share this
        // with the transmission fluid sensor or oil-to-trans cooler circuit).
        val tempRaw = repository.queryRaw(ELM327Protocol.PID.OIL_TEMP)
        if (!isNoData(tempRaw) &&
            ELM327Protocol.parseResponse(ELM327Protocol.PID.OIL_TEMP, tempRaw) != null) {
            cvtTempPid = ELM327Protocol.PID.OIL_TEMP
        }

        _sourceDescription.value = buildSourceDescription()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Poll loop
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun poll() {
        while (currentCoroutineContext().isActive && repository.isConnected) {
            val rpm     = query(ELM327Protocol.PID.RPM)     as? Int
            val speed   = query(ELM327Protocol.PID.SPEED)   as? Int
            val throttle = query(ELM327Protocol.PID.THROTTLE) as? Float
            val load    = query(ELM327Protocol.PID.ENGINE_LOAD) as? Float

            val inputShaft: Int? = inputShaftPid?.let {
                val raw = repository.queryRaw(it)
                if (isNoData(raw)) null else parseShaftRpm(raw)
            }

            val cvtTemp: Int? = cvtTempPid?.let {
                query(it) as? Int
            }

            val ratio = if (rpm != null && speed != null && speed > 0)
                rpm.toFloat() / speed.toFloat() else null

            if (rpm != null && speed != null) {
                window.addLast(Sample(rpm, speed))
                if (window.size > WINDOW_SIZE) window.removeFirst()
            }

            val lockup = computeLockup(rpm, speed, inputShaft)
            val slip   = computeSlip(rpm, inputShaft)

            _cvtData.value = CVTData(
                speedKph      = cvtReading(speed),
                engineRpm     = cvtReading(rpm),
                inputShaftRpm = cvtReading(inputShaft),
                slipRpm       = slip,
                lockupState   = lockup,
                cvtTempC      = cvtReading(cvtTemp),
                cvtRatio      = if (ratio != null)
                    CVTReading(ratio, CVTDataSource.INFERRED)
                else
                    CVTReading(null, CVTDataSource.UNAVAILABLE),
            )

            delay(250)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lockup state computation
    // Tier 1: direct sensor (input shaft RPM available) — calculates real slip.
    // Tier 3: inference via engine-RPM/vehicle-speed correlation analysis.
    // ──────────────────────────────────────────────────────────────────────────

    private fun computeLockup(rpm: Int?, speed: Int?, inputShaft: Int?): CVTReading<LockupState> {
        if (rpm != null && inputShaft != null) {
            val slip = abs(rpm - inputShaft)
            val state = when {
                slip < 50  -> LockupState.LOCKED
                slip < 300 -> LockupState.SLIPPING
                else       -> LockupState.OPEN
            }
            return CVTReading(state, CVTDataSource.SENSOR)
        }

        // Inference: need min samples and enough vehicle speed for accuracy
        if (window.size < MIN_WINDOW || (speed ?: 0) < 15) {
            return CVTReading(LockupState.UNKNOWN, CVTDataSource.INFERRED)
        }

        // For each consecutive window pair, compute how much the actual RPM
        // change differs from the change predicted by the speed change alone.
        // When the TC is locked, RPM tracks speed exactly (diff ≈ 0).
        // When the TC is open, RPM can change independently of speed (diff large).
        var totalDiff = 0f
        var count = 0
        for (i in 1 until window.size) {
            val prev = window[i - 1]
            val curr = window[i]
            if (prev.speed == 0) continue
            val expectedΔrpm = (curr.speed - prev.speed).toFloat() *
                prev.rpm.toFloat() / prev.speed.toFloat()
            val actualΔrpm = (curr.rpm - prev.rpm).toFloat()
            totalDiff += abs(actualΔrpm - expectedΔrpm)
            count++
        }
        val avgDiff = if (count > 0) totalDiff / count else Float.MAX_VALUE

        val state = when {
            avgDiff < LOCKED_THRESHOLD -> LockupState.LOCKED
            avgDiff > OPEN_THRESHOLD   -> LockupState.OPEN
            else                       -> LockupState.SLIPPING
        }
        return CVTReading(state, CVTDataSource.INFERRED)
    }

    private fun computeSlip(rpm: Int?, inputShaft: Int?): CVTReading<Int> {
        if (rpm == null || inputShaft == null) return CVTReading(null, CVTDataSource.UNAVAILABLE)
        return CVTReading(rpm - inputShaft, CVTDataSource.SENSOR)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun query(pid: String): Any? {
        val raw = repository.queryRaw(pid)
        return if (isNoData(raw)) null else ELM327Protocol.parseResponse(pid, raw)
    }

    // Parse a 2-byte Mode-01-style RPM response from an extended PID.
    // Response format (spaces removed, headers off): 41<PID><A><B>
    // Value = (A*256 + B) / 4, same formula as standard engine RPM PID.
    private fun parseShaftRpm(raw: String): Int? {
        val cleaned = raw.replace(Regex("\\s+"), "").uppercase()
        if (cleaned.length < 8 || !cleaned.startsWith("41")) return null
        val bytes = try {
            (cleaned.indices step 2).map { cleaned.substring(it, it + 2).toInt(16) }
        } catch (_: Exception) { return null }
        if (bytes.size < 4) return null
        val value = (bytes[2] * 256 + bytes[3]) / 4
        return if (value in 50..10_000) value else null  // plausibility gate
    }

    private fun isNoData(raw: String) = raw.isBlank()
        || raw.contains("NODATA",  ignoreCase = true)
        || raw.contains("NO DATA", ignoreCase = true)
        || raw.contains("ERROR",   ignoreCase = true)
        || raw.contains("?")

    private fun <T> cvtReading(value: T?): CVTReading<T> =
        CVTReading(value, if (value != null) CVTDataSource.SENSOR else CVTDataSource.UNAVAILABLE)

    private fun buildSourceDescription(): String = buildString {
        if (inputShaftPid != null) {
            append("Input shaft RPM: OBD sensor detected ($inputShaftPid). ")
            append("Lockup state and slip computed from direct measurement. ")
        } else {
            append("Input shaft RPM: no sensor detected on this vehicle. ")
            append("Lockup state inferred from engine-RPM to vehicle-speed ")
            append("correlation — accurate at steady speeds, less precise during ")
            append("rapid acceleration or heavy braking. ")
        }
        if (cvtTempPid != null) {
            append("CVT temp from PID $cvtTempPid (may reflect engine oil or ")
            append("transmission fluid depending on vehicle plumbing).")
        } else {
            append("CVT temp: not available for this vehicle.")
        }
    }

    companion object {
        private const val WINDOW_SIZE = 8
        private const val MIN_WINDOW  = 4
        private const val LOCKED_THRESHOLD = 60f   // RPM avg deviation → LOCKED
        private const val OPEN_THRESHOLD   = 350f  // RPM avg deviation → OPEN

        // Extended Mode 01 PIDs some manufacturers repurpose for transmission
        // shaft speed (same 2-byte RPM encoding as PID 0x0C).
        // GM Allison uses 0x8E; some Toyota/Lexus variants use 0xA1.
        private val INPUT_SHAFT_CANDIDATES = listOf("018E", "01A1")
    }
}
