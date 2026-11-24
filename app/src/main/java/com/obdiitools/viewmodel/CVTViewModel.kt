package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.SessionRepository
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.CVTData
import com.obdiitools.obd.CVTDataSource
import com.obdiitools.obd.CVTReading
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.ELM327Protocol
import com.obdiitools.obd.DTC
import com.obdiitools.obd.LockupState
import com.obdiitools.obd.NissanCvt7LiveData
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
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _cvtData = MutableStateFlow(CVTData())
    val cvtData: StateFlow<CVTData> = _cvtData.asStateFlow()

    private val _sourceDescription = MutableStateFlow("Probing vehicle capabilities…")
    private val _rawCapture = MutableStateFlow("")
    private val _livePollDiagnostics = MutableStateFlow("Waiting for first live poll…")
    val livePollDiagnostics: StateFlow<String> = _livePollDiagnostics.asStateFlow()
    private var lastSuccessfulSampleAt: String? = null

    val rawCapture: StateFlow<String> = _rawCapture.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _cvtDtcs = MutableStateFlow<List<DTC>>(emptyList())
    val cvtDtcs: StateFlow<List<DTC>> = _cvtDtcs.asStateFlow()

    private val _isReadingCvtDtcs = MutableStateFlow(false)
    val isReadingCvtDtcs: StateFlow<Boolean> = _isReadingCvtDtcs.asStateFlow()

    private val _cvtDtcStatus = MutableStateFlow("Not scanned yet.")
    val cvtDtcStatus: StateFlow<String> = _cvtDtcStatus.asStateFlow()

    private var cvtDtcJob: Job? = null

    private val _cvtDeterioration = MutableStateFlow<com.obdiitools.obd.NissanCvt7DeteriorationResult?>(null)
    val cvtDeterioration: StateFlow<com.obdiitools.obd.NissanCvt7DeteriorationResult?> = _cvtDeterioration.asStateFlow()
    private val _isReadingCvtDeterioration = MutableStateFlow(false)
    val isReadingCvtDeterioration: StateFlow<Boolean> = _isReadingCvtDeterioration.asStateFlow()
    private val _cvtDeteriorationStatus = MutableStateFlow("Not read yet.")
    val cvtDeteriorationStatus: StateFlow<String> = _cvtDeteriorationStatus.asStateFlow()
    private var cvtDeteriorationJob: Job? = null

    val sourceDescription: StateFlow<String> = _sourceDescription.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val userPreferences: StateFlow<UserPreferences> = prefsRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private var pollJob: Job? = null

    private var pauseGeneration = 0L
    private var activePauseLease: Long? = null

    @Synchronized
    private fun acquirePollingPause(): Long {
        val lease = ++pauseGeneration
        activePauseLease = lease
        repository.pausePolling()
        return lease
    }

    @Synchronized
    private fun releasePollingPause(lease: Long) {
        if (activePauseLease == lease) {
            activePauseLease = null
            repository.resumePolling()
        }
    }

    @Synchronized
    private fun releaseActivePollingPause() {
        activePauseLease?.let { releasePollingPause(it) }
    }

    // Discovered PIDs (null = not supported by this vehicle)
    private var inputShaftPid: String? = null
    private var cvtTempPid: String? = null
    private var probeComplete = false
    private var nissanCvt7Detected = false
    private var nissanCvt7EcuAddress: String? = null

    private val _transmissionModel = MutableStateFlow("Not identified")
    val detectedTransmissionModel: StateFlow<String> = _transmissionModel.asStateFlow()
    private val _transmissionPartNumber = MutableStateFlow<String?>(null)
    val transmissionPartNumber: StateFlow<String?> = _transmissionPartNumber.asStateFlow()
    private val _transmissionEcuAddress = MutableStateFlow<String?>(null)
    val transmissionEcuAddress: StateFlow<String?> = _transmissionEcuAddress.asStateFlow()

    init {
        viewModelScope.launch {
            repository.nissanCvt7Data.collect { _cvtData.value = it }
        }
        viewModelScope.launch {
            repository.nissanCvt7Status.collect { _sourceDescription.value = it }
        }
        viewModelScope.launch {
            repository.nissanCvt7Diagnostics.collect { _livePollDiagnostics.value = it }
        }
        viewModelScope.launch {
            repository.nissanCvt7EcuAddress.collect { address ->
                nissanCvt7EcuAddress = address
                nissanCvt7Detected = address != null
                _transmissionModel.value = if (address != null) "Jatco CVT7" else "Not identified"
                _transmissionPartNumber.value = if (address != null) "31036-4AT0C" else null
                _transmissionEcuAddress.value = address
            }
        }
    }

    // Lockup inference — rolling window of (rpm, speed) samples
    private data class Sample(val rpm: Int, val speed: Int)
    private val window = ArrayDeque<Sample>(WINDOW_SIZE + 1)

    // Acquisition is owned by OBDRepository and follows the connection/session
    // lifecycle. This screen only observes its StateFlows.
    fun start() = Unit

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        cvtDtcJob?.cancel()
        cvtDtcJob = null
        cvtDeteriorationJob?.cancel()
        cvtDeteriorationJob = null
        _isReadingCvtDtcs.value = false
        _isReadingCvtDeterioration.value = false
        _cvtDtcs.value = emptyList()
        _cvtDtcStatus.value = "Not scanned yet."
        _cvtDeterioration.value = null
        _cvtDeteriorationStatus.value = "Not read yet."
        // Do not stop or clear Nissan acquisition here. It must continue while
        // a trip is recording even after this screen leaves the navigation stack.
        releaseActivePollingPause()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Probe — runs once on first start to discover supported optional PIDs
    // ──────────────────────────────────────────────────────────────────────────

    fun captureRawTransmissionData() {
        val address = nissanCvt7EcuAddress ?: return
        if (_isCapturing.value) return
        viewModelScope.launch {
            repository.pausePolling()
            _isCapturing.value = true
            _rawCapture.value = "Starting raw TCM capture…"
            try {
                _rawCapture.value = repository.captureNissanCvt7RawData(address) { progress ->
                    _rawCapture.value = progress
                }
            } catch (error: Exception) {
                _rawCapture.value = "Capture failed: ${error.message ?: "unknown error"}"
            } finally {
                _isCapturing.value = false
                repository.resumePolling()
            }
        }
    }

    fun readCvtDTCs() {
        val address = nissanCvt7EcuAddress ?: return
        if (_isReadingCvtDtcs.value || _isCapturing.value) return
        cvtDtcJob?.cancel()
        cvtDtcJob = viewModelScope.launch {
            _isReadingCvtDtcs.value = true
            _cvtDtcStatus.value = "Reading stored CVT fault codes…"
            try {
                val codes = repository.readNissanCvt7DTCs(address)
                if (codes == null) {
                    _cvtDtcStatus.value = "TCM did not return a valid 5902 response. Codes could not be read."
                } else {
                    _cvtDtcs.value = codes
                    _cvtDtcStatus.value = if (codes.isEmpty()) {
                        "No stored CVT fault codes reported by the TCM."
                    } else {
                        "${codes.size} stored CVT fault code${if (codes.size == 1) "" else "s"} reported by the TCM."
                    }
                }
            } catch (error: Exception) {
                _cvtDtcStatus.value = "CVT code read failed: ${error.message ?: "unknown error"}"
            } finally {
                _isReadingCvtDtcs.value = false
            }
        }
    }

    fun readCvtDeterioration() {
        val address = nissanCvt7EcuAddress ?: return
        if (_isReadingCvtDeterioration.value || _isReadingCvtDtcs.value || _isCapturing.value) return
        cvtDeteriorationJob?.cancel()
        cvtDeteriorationJob = viewModelScope.launch {
            _isReadingCvtDeterioration.value = true
            _cvtDeteriorationStatus.value = "Reading CVT fluid deterioration counter (read-only 2103)…"
            try {
                val result = repository.readNissanCvt7Deterioration(address)
                if (result == null) {
                    _cvtDeterioration.value = null
                    _cvtDeteriorationStatus.value = "TCM did not return a complete 6103 response. Counter could not be read."
                } else {
                    _cvtDeterioration.value = result
                    _cvtDeteriorationStatus.value = "Read from the confirmed CVT7 controller. Nissan's RE0F11A reference threshold is 210,000 points."
                }
            } catch (error: Exception) {
                _cvtDeteriorationStatus.value = "Deterioration read failed: ${error.message ?: "unknown error"}"
            } finally {
                _isReadingCvtDeterioration.value = false
            }
        }
    }

    private suspend fun probe() {
        _sourceDescription.value = "Searching for the Nissan CVT7 transmission controller…"
        val discovery = repository.discoverNissanCvt7 { progress ->
            _sourceDescription.value = progress
        }
        if (discovery != null) {
            nissanCvt7Detected = true
            nissanCvt7EcuAddress = discovery.ecuAddress
            _transmissionModel.value = "Jatco CVT7"
            _transmissionPartNumber.value = "31036-4AT0C"
            _transmissionEcuAddress.value = discovery.ecuAddress
            _cvtData.value = discovery.liveData?.let(::toCvtData) ?: CVTData()
            _sourceDescription.value =
                "Nissan CVT7 controller confirmed at ${discovery.ecuAddress}. Live values come directly from the transmission controller; hydraulic pressures are reported in MPa. Unverified measurements remain N/A rather than displaying guessed values."
            return
        }
        // Do not substitute generic engine data or calculated estimates for
        // transmission-controller telemetry. Until a supported TCM responds,
        // leave every CVT measurement unavailable and keep retrying discovery
        // only when the monitor is restarted.
        _cvtData.value = CVTData()
        // Keep the per-address diagnostic trace supplied by repository discovery.
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Poll loop
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun poll() {
        while (currentCoroutineContext().isActive && repository.isConnected) {
            if (!nissanCvt7Detected) {
                // Discovery failed: never fall through to generic OBD polling.
                delay(1000)
                continue
            }
            if (_isReadingCvtDtcs.value || _isReadingCvtDeterioration.value || _isCapturing.value) {
                delay(100)
                continue
            }
            if (nissanCvt7Detected) {
                // Both pollers use SerializedOBDConnection transactions. Do not
                // pause standard OBD polling here: frequent CVT reads can otherwise
                // starve the slow-PID cycle (including engine oil temperature PID 5C).
                nissanCvt7EcuAddress?.let { address ->
                    val result = repository.queryNissanCvt7LiveData(address)
                    val sample = result.sample
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())
                    if (sample != null) lastSuccessfulSampleAt = time
                    _livePollDiagnostics.value = buildString {
                        append("Last poll: $time — ")
                        append(result.parseStatus)
                        append("  |  Last good sample: ${lastSuccessfulSampleAt ?: "never"}")
                        append("\nRaw 2101: ")
                        append(result.rawResponse.ifBlank { "(empty)" })
                    }
                    if (sample != null) {
                        val decoded = toCvtData(sample)
                        _cvtData.value = decoded
                        sessionRepository.recordCvtData(decoded)
                    }
                }
                delay(300)
                continue
            }

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

    private fun toCvtData(sample: NissanCvt7LiveData): CVTData {
        fun measured(value: Float) = CVTReading(value, CVTDataSource.SENSOR)
        return CVTData(
            speedKph = cvtReading(sample.vehicleSpeedKph),
            engineRpm = cvtReading(sample.engineRpm),
            inputShaftRpm = CVTReading(null, CVTDataSource.UNAVAILABLE),
            slipRpm = CVTReading(sample.slipRpm, CVTDataSource.SENSOR),
            lockupState = CVTReading(LockupState.UNKNOWN, CVTDataSource.UNAVAILABLE),
            cvtTempC = cvtReading(sample.fluidTemperatureC),
            cvtRatio = measured(sample.gearRatio),
            primaryPressureMpa = measured(sample.primaryPressureMpa),
            secondaryPressureMpa = measured(sample.secondaryPressureMpa),
            targetSecondaryPressureMpa = measured(sample.targetSecondaryPressureMpa),
            linePressureMpa = measured(sample.linePressureMpa),
            accelerator = measured(sample.accelerator),
            torqueConverterRatio = measured(sample.torqueConverterRatio),
            cvtTemperatureCount = cvtReading(sample.cvtTemperatureCount),
            batteryVoltage = measured(sample.batteryVoltage),
            lockupCurrentAmps = measured(sample.lockupCurrentAmps),
            luPressureMpa = measured(sample.luPressureMpa),
            selectorPosition = cvtReading(sample.selectorPosition),
            virtualGear = cvtReading(sample.virtualGear),
            dGearRatio = measured(sample.dGearRatio),
            gForce = measured(sample.gForce),
            engineTorqueNm = measured(sample.engineTorqueNm),
            enginePowerHp = cvtReading(sample.enginePowerHp),
            outputTorqueNm = cvtReading(sample.outputTorqueNm),
            selectorStatusFlag = cvtReading(sample.selectorStatusFlag),
        )
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
