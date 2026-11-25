package com.obdiitools.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.obdiitools.data.AlertThresholds
import com.obdiitools.data.AlertType
import com.obdiitools.data.CustomPidDefinition
import com.obdiitools.data.CustomPidRepository
import com.obdiitools.data.EiaRepository
import com.obdiitools.data.FuelPriceMode
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.SessionRepository
import com.obdiitools.data.TripSummary
import com.obdiitools.obd.BluetoothDeviceInfo
import com.obdiitools.obd.CanFrame
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.DTC
import com.obdiitools.obd.DTCDatabase
import com.obdiitools.obd.ELM327Protocol
import com.obdiitools.obd.FreezeFrame
import com.obdiitools.obd.OBDData
import com.obdiitools.obd.MazdaCx5DashboardData
import com.obdiitools.obd.MazdaCx5TransmissionData
import com.obdiitools.obd.ObdexDtcDatabase
import com.obdiitools.obd.ReadinessStatus
import com.obdiitools.obd.UdsResponse
import com.obdiitools.obd.parseReadinessStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OBDRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: BluetoothDeviceScanner,
    private val sessionRepository: SessionRepository,
    private val customPidRepository: CustomPidRepository,
    private val preferencesRepository: PreferencesRepository,
    private val eiaRepository: EiaRepository,
) {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _obdData = MutableStateFlow(OBDData())
    val obdData: StateFlow<OBDData> = _obdData.asStateFlow()

    private val _mazdaCx5DashboardData = MutableStateFlow<MazdaCx5DashboardData?>(null)
    val mazdaCx5DashboardData: StateFlow<MazdaCx5DashboardData?> = _mazdaCx5DashboardData.asStateFlow()

    private val _nissanCvt7Data = MutableStateFlow(com.obdiitools.obd.CVTData())
    val nissanCvt7Data: StateFlow<com.obdiitools.obd.CVTData> = _nissanCvt7Data.asStateFlow()
    private val _nissanCvt7Status = MutableStateFlow("Waiting for Nissan vehicle identification…")
    val nissanCvt7Status: StateFlow<String> = _nissanCvt7Status.asStateFlow()
    private val _nissanCvt7Diagnostics = MutableStateFlow("Waiting for first live poll…")
    val nissanCvt7Diagnostics: StateFlow<String> = _nissanCvt7Diagnostics.asStateFlow()
    private val _nissanCvt7EcuAddress = MutableStateFlow<String?>(null)
    val nissanCvt7EcuAddress: StateFlow<String?> = _nissanCvt7EcuAddress.asStateFlow()
    @Volatile private var isNissanVehicle = false
    private var nissanCvt7PollingJob: Job? = null
    private var lastNissanCvt7FailureEventAtMs = 0L

    @Volatile private var mazdaCx5PollingEnabled = false
    private var lastMazdaGearPollAtMs = 0L
    private var lastMazdaTemperaturePollAtMs = 0L
    private var lastMazdaGearFailureAtMs = 0L
    private var lastMazdaTemperatureFailureAtMs = 0L
    private var lastMazdaWheelSpeedPollAtMs = 0L
    private var nextMazdaWheelSpeedDidIndex = 0
    private var lastMazdaWheelSpeedFailureAtMs = mutableMapOf<String, Long>()
    private var mazdaWheelSpeedCapturedAtMs = mutableMapOf<String, Long>()
    // Keep repeated identical TCM failures from creating hundreds of duplicate session rows.
    private val lastMazdaDiagnosticEventAtMs = mutableMapOf<String, Long>()
    private var mazdaGearCapturedAtMs: Long? = null
    private var mazdaTemperatureCapturedAtMs: Long? = null

    fun updateMazdaCx5DashboardData(data: MazdaCx5TransmissionData?, capturedAtMs: Long) {
        mazdaGearCapturedAtMs = capturedAtMs.takeIf { data?.gear != null }
        mazdaTemperatureCapturedAtMs = capturedAtMs.takeIf { data?.transmissionTemperatureC != null }
        _mazdaCx5DashboardData.value = data?.let {
            MazdaCx5DashboardData(
                transmission = it,
                capturedAtMs = capturedAtMs,
                gearCapturedAtMs = capturedAtMs.takeIf { _ -> it.gear != null },
                temperatureCapturedAtMs = capturedAtMs.takeIf { _ -> it.transmissionTemperatureC != null },
            )
        }
    }

    fun setVehicleIdentity(vin: String?, make: String = "", model: String = "") {
        val normalized = vin.orEmpty().uppercase().filter { it.isLetterOrDigit() }
        val key = if (normalized.length == 17 && normalized != "00000000000000000") {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            "vin_" + digest.joinToString("") { "%02x".format(it) }
        } else null
        val vehicleChanged = activeVehicleProfileKey != key
        activeVehicleProfileKey = key
        _previouslyKnownPids.value = key?.let {
            capabilityProfilePreferences.getStringSet(it, emptySet())?.toSet()
        } ?: emptySet()

        val isMazdaCx5 = make.equals("Mazda", ignoreCase = true) &&
            model.contains("CX-5", ignoreCase = true)
        if (mazdaCx5PollingEnabled != isMazdaCx5 || vehicleChanged) {
            mazdaCx5PollingEnabled = isMazdaCx5
            lastMazdaGearPollAtMs = 0L
            lastMazdaTemperaturePollAtMs = 0L
            lastMazdaGearFailureAtMs = 0L
            lastMazdaTemperatureFailureAtMs = 0L
            lastMazdaWheelSpeedPollAtMs = 0L
            nextMazdaWheelSpeedDidIndex = 0
            lastMazdaWheelSpeedFailureAtMs.clear()
            mazdaWheelSpeedCapturedAtMs.clear()
            lastMazdaDiagnosticEventAtMs.clear()
            mazdaGearCapturedAtMs = null
            mazdaTemperatureCapturedAtMs = null
            _mazdaCx5DashboardData.value = null
        }

        isNissanVehicle = make.equals("Nissan", ignoreCase = true)
        if (isNissanVehicle && connection?.isConnected == true) {
            startNissanCvt7SessionPolling()
        } else {
            stopNissanCvt7SessionPolling()
        }
    }

    private val _livePidValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val livePidValues: StateFlow<Map<String, Float>> = _livePidValues.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DTC>>(emptyList())
    val dtcList: StateFlow<List<DTC>> = _dtcList.asStateFlow()

    private val _lowVoltageWarning = MutableStateFlow(false)
    val lowVoltageWarning: StateFlow<Boolean> = _lowVoltageWarning.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

    private val capabilityTracker = com.obdiitools.obd.VehicleCapabilityTracker()
    val vehicleCapabilities = capabilityTracker.capabilities
    private val _confirmedPids = MutableStateFlow<Set<String>>(emptySet())
    val confirmedPids: StateFlow<Set<String>> = _confirmedPids.asStateFlow()

    private val capabilityProfilePreferences =
        context.getSharedPreferences("vehicle_capability_profiles", Context.MODE_PRIVATE)
    @Volatile private var activeVehicleProfileKey: String? = null
    private val _previouslyKnownPids = MutableStateFlow<Set<String>>(emptySet())
    val previouslyKnownPids: StateFlow<Set<String>> = _previouslyKnownPids.asStateFlow()

    private fun persistCapabilityProfile() {
        val key = activeVehicleProfileKey ?: return
        val confirmed = _confirmedPids.value
        // Do not replace a useful prior profile with an empty set while a
        // newly connected vehicle is still being sampled.
        if (confirmed.isEmpty()) return
        capabilityProfilePreferences.edit()
            .putStringSet(key, confirmed.toSet())
            .apply()
    }

    private val _readinessStatus = MutableStateFlow<ReadinessStatus?>(null)
    val readinessStatus: StateFlow<ReadinessStatus?> = _readinessStatus.asStateFlow()

    private val _freezeFrames = MutableStateFlow<List<FreezeFrame>>(emptyList())
    val freezeFrames: StateFlow<List<FreezeFrame>> = _freezeFrames.asStateFlow()

    private val _activeAlerts = MutableStateFlow<Set<AlertType>>(emptySet())
    val activeAlerts: StateFlow<Set<AlertType>> = _activeAlerts.asStateFlow()

    private val _customPidValues = MutableStateFlow<Map<String, Float?>>(emptyMap())
    val customPidValues: StateFlow<Map<String, Float?>> = _customPidValues.asStateFlow()

    private val _lastTripSummary = MutableStateFlow<TripSummary?>(null)
    val lastTripSummary: StateFlow<TripSummary?> = _lastTripSummary.asStateFlow()

    fun clearTripSummary() { _lastTripSummary.value = null }

    private var connection: OBDConnection? = null
    @Volatile private var activeSessionId: Long? = null
    private var pollingJob: Job? = null
    private val adaptivePidScheduler = com.obdiitools.obd.AdaptivePidPollingScheduler(maxRequestsPerPass = 4)
    private val lastAdaptivePollAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notifId = AtomicInteger(2000)

    @Volatile private var pollingPaused = false
    private val pollingPauseCount = AtomicInteger(0)
    @Volatile private var pollCycleCount = 0
    @Volatile private var multiPidCapable: Boolean? = null
    @Volatile private var cachedThresholds = AlertThresholds()

    init {
        scope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                cachedThresholds = prefs.alertThresholds
            }
        }
    }

    /** Reference-counted pause ownership prevents overlapping screens from resuming one another. */
    fun pausePolling() {
        pollingPauseCount.incrementAndGet()
        pollingPaused = true
    }

    fun resumePolling() {
        val remaining = pollingPauseCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
        pollingPaused = remaining > 0
    }

    suspend fun queryRaw(command: String): String =
        try { connection?.query(command) ?: "" } catch (_: java.io.IOException) { "" }

    suspend fun queryRawLines(command: String, timeoutMs: Long = 1000): List<String> =
        try { connection?.queryLines(command, timeoutMs) ?: emptyList() } catch (_: java.io.IOException) { emptyList() }

    suspend fun queryRawUds(ecuAddress: String, did: String): String =
        connection?.queryUds(ecuAddress, did) ?: ""

    /**
     * Sends an overridden UDS header and request as one serialized operation,
     * restoring the normal functional-request header even if the request fails.
     */
    suspend fun queryRawUdsOverride(header: String?, command: String): String {
        val conn = connection ?: return ""
        if (!command.matches(Regex("(?i)^[0-9A-F]{6,10}$")) ||
            (header != null && !header.matches(Regex("(?i)^(?:[0-9A-F]{3}|[0-9A-F]{8})$")))) return ""
        return conn.withTransaction {
            val tx = this
            try {
                if (header != null) tx.query("ATSH $header", 400)
                val lines = tx.queryLines(command, 3000)
                // Keep the complete response. UdsResponse.parse handles ELM
                // frame-index prefixes and validates the assembled DID payload.
                lines.joinToString("\n")
            } finally {
                if (header != null) withContext(NonCancellable) { runCatching { tx.query("ATSH 7DF", 300) } }
            }
        }
    }

    /**
     * Read-only Nissan enhanced-diagnostic discovery.
     * Probes Mode 21 local identifiers without changing ECU session, coding,
     * adaptations, or actuator state. A positive 61 response is evidence that
     * this ECU implements the service; it is not yet a decoded CVT data item.
     */
    suspend fun probeNissanLocalIdentifiers(
        ecuAddress: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> {
        val conn = connection ?: return emptyList()
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) return emptyList()
        return conn.withTransaction {
            val tx = this
            val hits = mutableListOf<String>()
            try {
                tx.query("ATSH ${ecuAddress.uppercase()}", 400)
                for (identifier in 0x00..0xFF) {
                    val id = "%02X".format(identifier)
                    val lines = tx.queryLines("21$id", 350)
                    val response = lines.map { it.replace(" ", "").uppercase() }
                        .firstOrNull { it.startsWith("61$id") || it.startsWith("7F21") }
                    if (response?.startsWith("61$id") == true) {
                        hits.add("21$id -> $response")
                    }
                    onProgress(identifier + 1, 256)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { tx.query("ATSH 7DF", 300) }
                }
            }
            hits
        }
    }

    /**
     * Identifies the transmission with Nissan enhanced diagnostic reads before
     * attempting live-data decoding. Only read/session-selection requests are
     * used; erase, reset, programming, security-access and actuator operations
     * are deliberately excluded.
     */
    suspend fun discoverNissanCvt7(
        onProgress: (String) -> Unit = {},
    ): com.obdiitools.obd.NissanCvt7DiscoveryResult? {
        val conn = connection ?: return null
        return conn.withTransaction {
            try {
                // Do not issue ATZ here: it can reset the Bluetooth radio on
                // BLE ELM327 adapters. The connection initialization already
                // configured the adapter; reassert only safe runtime settings.
                query("ATE0", 300)
                query("ATAL", 300)
                query("ATH0", 300)
                query("ATS0", 300)
                query("ATST32", 300)
                query("ATSW00", 300)
                query("ATSP6", 600)
                query("ATCAF1", 300)

                val trace = mutableListOf<String>()
                for (address in NISSAN_CVT7_ECU_CANDIDATES) {
                    onProgress("Identifying transmission controller at $address…")
                    query("ATSH $address", 400)
                    query("ATFCSH $address", 300)
                    query("ATFCSD 300000", 300)
                    query("ATFCSM1", 300)
                    query("ATCRA 7E9", 300)

                    val session = queryLines("10C0", 1500)
                    trace.add("$address 10C0: ${summarizeDiagnosticResponse(session)}")
                    onProgress(trace.takeLast(3).joinToString("\n"))

                    val statusReply = queryLines("2103", 1800)
                    trace.add("$address 2103: ${summarizeDiagnosticResponse(statusReply)}")
                    onProgress(trace.takeLast(4).joinToString("\n"))

                    val liveReply = queryLines("2101", 3000)
                    trace.add("$address 2101: ${summarizeDiagnosticResponse(liveReply)}")
                    onProgress(trace.takeLast(5).joinToString("\n"))

                    val tcmResponded =
                        com.obdiitools.obd.NissanCvt7Protocol.hasPositiveResponse(session, "50C0") &&
                        com.obdiitools.obd.NissanCvt7Protocol.hasPositiveResponse(statusReply, "6103") &&
                        com.obdiitools.obd.NissanCvt7Protocol.hasPositiveResponse(liveReply, "6101")
                    if (tcmResponded) {
                        val sample = com.obdiitools.obd.NissanCvt7Protocol.parseLiveData(liveReply)
                        onProgress("Nissan CVT7 controller confirmed at $address; live response captured.")
                        return@withTransaction com.obdiitools.obd.NissanCvt7DiscoveryResult(address, sample)
                    }

                    trace.add("$address: required positive TCM responses not received")
                    onProgress(trace.takeLast(8).joinToString("\n"))
                }
                onProgress("TCM NOT CONFIRMED\n" + trace.takeLast(28).joinToString("\n"))
                null
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    // Restore the standard functional OBD-II adapter state.
                    // The CVT transaction temporarily selects ISO 15765-4 CAN 11-bit
                    // (ATSP6), a physical TCM header, a response filter and manual
                    // flow-control settings. Reset all of them before releasing the
                    // serialized transaction so the dashboard poller sees a clean
                    // standard OBD configuration.
                    runCatching { query("ATCRA", 300) }
                    runCatching { query("ATFCSM0", 300) }
                    runCatching { query("ATSH 7DF", 400) }
                    runCatching { query("ATSP0", 600) }
                    runCatching { query("ATCAF1", 300) }
                    runCatching { query("ATH0", 300) }
                    runCatching { query("ATS0", 300) }
                }
            }
        }
    }

    private fun summarizeDiagnosticResponse(lines: List<String>): String {
        val summary = lines
            .map { it.filterNot(Char::isWhitespace).uppercase() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return summary.ifBlank { "empty response" }.take(512)
    }

    /**
     * Captures raw replies for the read identifiers present in the supplied
     * transmission profile. No values are interpreted and no write/control
     * services are sent. Indexed ELM response lines are retained verbatim.
     */
    suspend fun captureNissanCvt7RawData(
        ecuAddress: String,
        onProgress: (String) -> Unit = {},
    ): String {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) return "Invalid TCM address."
        val conn = connection ?: return "OBD adapter is disconnected."
        val requests = buildList {
            addAll(listOf("2100", "2101", "2102", "2110", "2120", "2170"))
            (0x80..0x90).forEach { add("21%02X".format(it)) }
            add("21E0")
            (0xF0..0xFF).forEach { add("21%02X".format(it)) }
            addAll(listOf("22F100", "22F1A0", "22F190"))
        }
        return conn.withTransaction {
            val tx = this
            val report = StringBuilder()
            try {
                // Do not reset the adapter while its Bluetooth link is active.
                // Some BLE ELM327 adapters reset their radio when ATZ is issued.
                tx.query("ATE0", 300)
                tx.query("ATAL", 300)
                tx.query("ATH0", 300)
                tx.query("ATS0", 300)
                tx.query("ATST32", 300)
                tx.query("ATSW00", 300)
                tx.query("ATSP6", 600)
                tx.query("ATCAF1", 300)
                tx.query("ATSH ${ecuAddress.uppercase()}", 400)
                tx.query("ATFCSH ${ecuAddress.uppercase()}", 300)
                tx.query("ATFCSD 300000", 300)
                tx.query("ATFCSM1", 300)
                tx.query("ATCRA 7E9", 300)
                val session = tx.queryLines("10C0", 1500)
                report.append("NISSAN CVT7 RAW CAPTURE — ECU ${ecuAddress.uppercase()}\n")
                report.append("Session 10C0: ").append(session.joinToString(" | ")).append("\n")
                report.append("Requests are read-only profile identifiers; values are not decoded.\n\n")
                requests.forEachIndexed { index, request ->
                    val lines = if (request == "2110") {
                        // Capture this unusually large response as individual CAN frames.
                        // CAF0 keeps ISO-TP frames separate; automatic flow control remains enabled.
                        tx.query("ATCAF0", 300)
                        tx.query("ATH1", 300)
                        tx.query("ATS1", 300)
                        try {
                            tx.queryLines("02 21 10", 8000)
                        } finally {
                            tx.query("ATH0", 300)
                            tx.query("ATS0", 300)
                            tx.query("ATCAF1", 300)
                        }
                    } else {
                        tx.queryLines(request, 4000)
                    }
                    val raw = lines.joinToString(" ")
                    report.append(if (request == "2110") "2110 (raw CAN frames): " else "$request: ")
                    if (lines.isEmpty()) {
                        report.append("NO RESPONSE")
                    } else {
                        report.append(raw)
                        if (raw.contains("BUFFERFULL", ignoreCase = true)) {
                            report.append(" [INCOMPLETE: adapter reported BUFFERFULL; do not decode]")
                        }
                    }
                    report.append("\n")
                    onProgress("Capturing ${index + 1}/${requests.size}: $request")
                }
                report.append("\nCapture finished. BUFFERFULL responses, if any, are incomplete and excluded from decoding.")
                report.toString()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { tx.query("ATCRA", 300) }
                    runCatching { tx.query("ATFCSM0", 300) }
                    runCatching { tx.query("ATSH 7DF", 400) }
                    runCatching { tx.query("ATSP0", 600) }
                }
            }
        }
    }

    /** Reads one sample from the already-discovered physical controller address. */
    suspend fun queryNissanCvt7LiveData(
        ecuAddress: String,
    ): com.obdiitools.obd.NissanCvt7PollResult {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) {
            return com.obdiitools.obd.NissanCvt7PollResult(null, "", "Invalid TCM address.")
        }
        val conn = connection ?: return com.obdiitools.obd.NissanCvt7PollResult(null, "", "Adapter disconnected.")
        return conn.withTransaction {
            var response = emptyList<String>()
            try {
                query("ATE0", 300)
                query("ATAL", 300)
                query("ATH0", 300)
                query("ATS0", 300)
                query("ATST32", 300)
                query("ATSW00", 300)
                query("ATSP6", 600)
                query("ATCAF1", 300)
                query("ATSH ${ecuAddress.uppercase()}", 400)
                query("ATFCSH ${ecuAddress.uppercase()}", 300)
                query("ATFCSD 300000", 300)
                query("ATFCSM1", 300)
                query("ATCRA 7E9", 300)
                query("ATST32", 300)

                // The controller's manufacturer-specific diagnostic session
                // can time out while the monitor is open. Discovery explicitly
                // enters session C0 before reading 2101; re-enter it for each
                // sample and verify the positive 50C0 response.
                val sessionResponse = queryLines("10C0", 1500)
                val sessionOk = com.obdiitools.obd.NissanCvt7Protocol.hasPositiveResponse(
                    sessionResponse, "50C0"
                )
                if (!sessionOk) {
                    val sessionRaw = sessionResponse.joinToString(" | ").take(300)
                    return@withTransaction com.obdiitools.obd.NissanCvt7PollResult(
                        sample = null,
                        rawResponse = "10C0: ${sessionRaw.ifBlank { "(empty)" }}",
                        parseStatus = "Diagnostic session C0 was not accepted; skipped 2101 request.",
                    )
                }

                response = queryLines("2101", 3000)
                val sample = com.obdiitools.obd.NissanCvt7Protocol.parseLiveData(response)
                val status = if (sample != null) "Session 50C0 confirmed; decoded OK." else
                    com.obdiitools.obd.NissanCvt7Protocol.diagnoseLiveData(response)
                com.obdiitools.obd.NissanCvt7PollResult(
                    sample = sample,
                    rawResponse = "10C0: ${sessionResponse.joinToString(" | ").take(300)}  ||  2101: ${response.joinToString(" | ").take(600)}",
                    parseStatus = status,
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                com.obdiitools.obd.NissanCvt7PollResult(
                    sample = null,
                    rawResponse = response.joinToString(" | ").take(900),
                    parseStatus = "Request exception: ${error.javaClass.simpleName}: ${error.message ?: "no detail"}",
                )
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    // Restore standard functional OBD-II adapter state before
                    // the dashboard poller acquires the serialized connection.
                    runCatching { query("ATCRA", 300) }
                    runCatching { query("ATFCSM0", 300) }
                    runCatching { query("ATSH 7DF", 400) }
                    runCatching { query("ATSP0", 600) }
                    runCatching { query("ATCAF1", 300) }
                    runCatching { query("ATH0", 300) }
                    runCatching { query("ATS0", 300) }
                }
            }
        }
    }

    /**
     * Reads the Nissan CVT7 fluid-deterioration counter with the read-only 2103
     * request. It requires a confirmed C0 diagnostic session and restores the
     * standard adapter configuration before releasing the transaction.
     */
    suspend fun readNissanCvt7Deterioration(
        ecuAddress: String,
    ): com.obdiitools.obd.NissanCvt7DeteriorationResult? {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) return null
        val conn = connection ?: return null
        return conn.withTransaction {
            try {
                query("ATE0", 300)
                query("ATAL", 300)
                query("ATH0", 300)
                query("ATS0", 300)
                query("ATST32", 300)
                query("ATSW00", 300)
                query("ATSP6", 600)
                query("ATCAF1", 300)
                query("ATSH ${ecuAddress.uppercase()}", 400)
                query("ATFCSH ${ecuAddress.uppercase()}", 300)
                query("ATFCSD 300000", 300)
                query("ATFCSM1", 300)
                query("ATCRA 7E9", 300)
                val session = queryLines("10C0", 1500)
                if (!com.obdiitools.obd.NissanCvt7Protocol.hasPositiveResponse(session, "50C0")) {
                    return@withTransaction null
                }
                val response = queryLines("2103", 2500)
                com.obdiitools.obd.NissanCvt7Protocol.parseCvtDeteriorationResponse(response)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { query("ATCRA", 300) }
                    runCatching { query("ATFCSM0", 300) }
                    runCatching { query("ATSH 7DF", 400) }
                    runCatching { query("ATSP0", 600) }
                    runCatching { query("ATCAF1", 300) }
                    runCatching { query("ATH0", 300) }
                    runCatching { query("ATS0", 300) }
                }
            }
        }
    }

    /** Reads Nissan CVT7 stored DTCs only after the TCM identity was confirmed. */
    suspend fun readNissanCvt7DTCs(ecuAddress: String): List<DTC>? {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) return null
        val conn = connection ?: return null
        return conn.withTransaction {
            try {
                query("ATE0", 300)
                query("ATH0", 300)
                query("ATS0", 300)
                query("ATCAF1", 300)
                query("ATSP6", 600)
                query("ATSH ${ecuAddress.uppercase()}", 400)
                query("ATFCSH ${ecuAddress.uppercase()}", 300)
                query("ATFCSD 300000", 300)
                query("ATFCSM1", 300)
                query("ATCRA 7E9", 300)
                query("ATST32", 300)
                val response = queryLines("19022F", 4000)
                val codes = com.obdiitools.obd.NissanCvt7Protocol.parseCvtDtcResponse(response)
                    ?: return@withTransaction null
                codes.map { code ->
                    DTC(
                        code = code,
                        description = DTCDatabase.getNissanCvt7Description(code),
                    )
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { query("ATCRA", 300) }
                    runCatching { query("ATFCSM0", 300) }
                    runCatching { query("ATSH 7DF", 400) }
                    runCatching { query("ATSP0", 600) }
                }
            }
        }
    }

    // ── Per-ECU PID support discovery ────────────────────────────────────────
    // Sends availability PIDs (00, 20, 40 …) to the given ECU address and decodes
    // the 32-bit bitmasks to build the full set of supported PID codes.
    // Always restores ATSH 7DF on completion.
    /**
     * Read one advertised Mode 01 PID without requiring an existing decoder.
     * Used by Deep Scan to capture raw evidence for supported-but-unimplemented
     * parameters. The request header is always restored after the read.
     */
    suspend fun queryRawMode01Pid(ecuAddress: String, pid: String): String {
        val conn = connection ?: return ""
        val address = ecuAddress.uppercase()
        val command = pid.uppercase()
        if (!address.matches(Regex("^[0-9A-F]{3}$")) ||
            !command.matches(Regex("^01[0-9A-F]{2}$"))) return ""
        return conn.withTransaction {
            val tx = this
            try {
                tx.query("ATSH $address", 300)
                tx.queryLines(command, 1200).joinToString("\n")
            } finally {
                withContext(NonCancellable) {
                    runCatching { tx.query("ATSH 7DF", 300) }
                }
            }
        }
    }

    suspend fun queryModeSupport(ecuAddress: String, mode: String): Set<String> {
        val conn = connection ?: return emptySet()
        val normalizedMode = mode.uppercase()
        val modeVal = normalizedMode.toIntOrNull(16) ?: return emptySet()
        if (!ecuAddress.uppercase().matches(Regex("^[0-9A-F]{3}$")) ||
            !normalizedMode.matches(Regex("^[0-9A-F]{2}$")) || modeVal !in 1..0x0A) return emptySet()
        val responsePrefix = "%02X".format(modeVal + 0x40)
        return withContext(Dispatchers.IO) {
            conn.withTransaction {
                val tx = this
                val supported = mutableSetOf<String>()
                try {
                    tx.query("ATSH $ecuAddress", 400)
                    delay(150)
                    for (group in listOf("00", "20", "40", "60", "80", "A0", "C0")) {
                        val header = "$responsePrefix$group"
                        val lines = tx.queryLines("$normalizedMode$group", 1200)
                        val cleaned = lines.map { it.replace(" ", "").uppercase() }
                            .firstOrNull { it.startsWith(header) } ?: continue
                        val bitmaskAt = header.length + if (modeVal == 9) 2 else 0
                        if (cleaned.length < bitmaskAt + 8) continue
                        val bitmask = cleaned.substring(bitmaskAt, bitmaskAt + 8).toLongOrNull(16) ?: continue
                        val base = group.toInt(16)
                        for (bit in 0..31) {
                            if ((bitmask and (1L shl (31 - bit))) != 0L) {
                                supported.add("$normalizedMode%02X".format(base + bit + 1))
                            }
                        }
                        if ((bitmask and 1L) == 0L) break
                    }
                } finally {
                    withContext(NonCancellable) { tx.query("ATSH 7DF", 300) }
                }
                supported
            }
        }
    }

    /**
     * Validate a Mode 01 PID by requiring two independently decoded responses.
     * This is read-only and is used by Deep Scan to distinguish advertised
     * support from a value this ECU actually returns.
     */
    suspend fun validateMode01Pid(ecuAddress: String, pid: String): Boolean {
        val conn = connection ?: return false
        val address = ecuAddress.uppercase()
        val command = pid.uppercase()
        if (!address.matches(Regex("^[0-9A-F]{3}$")) ||
            !command.matches(Regex("^01[0-9A-F]{2}$"))) return false
        return conn.withTransaction {
            try {
                query("ATSH $address", 300)
                val first = ELM327Protocol.parseResponse(command, query(command, 1200))
                delay(80)
                val second = ELM327Protocol.parseResponse(command, query(command, 1200))
                val firstValid = isPlausibleMode01Value(command, first)
                val secondValid = isPlausibleMode01Value(command, second)
                if (address == "7DF") {
                    capabilityTracker.record(command, first.takeIf { firstValid })
                    capabilityTracker.record(command, second.takeIf { secondValid })
                    _confirmedPids.value = capabilityTracker.confirmedPids
                    persistCapabilityProfile()
                }
                firstValid && secondValid
            } finally {
                withContext(NonCancellable) { runCatching { query("ATSH 7DF", 300) } }
            }
        }
    }

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceInfo: BluetoothDeviceInfo) {
        mazdaCx5PollingEnabled = false
        _mazdaCx5DashboardData.value = null
        lastMazdaGearPollAtMs = 0L
        lastMazdaTemperaturePollAtMs = 0L
        lastMazdaGearFailureAtMs = 0L
        lastMazdaTemperatureFailureAtMs = 0L
        mazdaGearCapturedAtMs = null
        mazdaTemperatureCapturedAtMs = null
        _connectionState.value = ConnectionState.Connecting(deviceInfo.name, deviceInfo.address)
        withContext(Dispatchers.IO) {
            var candidateConnection: OBDConnection? = null
            try {
                val device = scanner.getRemoteDevice(deviceInfo.address)
                    ?: throw Exception("Device not found")
                scanner.adapter?.cancelDiscovery()

                val conn: OBDConnection = SerializedOBDConnection(
                    if (deviceInfo.isBle) {
                        ELM327BleConnection(context, device)
                    } else {
                        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        try {
                            socket.connect()
                        } catch (error: Exception) {
                            runCatching { socket.close() }
                            throw error
                        }
                        ELM327Connection(socket)
                    }
                )

                candidateConnection = conn
                val initialized = conn.initialize()
                if (!initialized) {
                    conn.close()
                    _connectionState.value = ConnectionState.Error("ELM327 initialization failed")
                    return@withContext
                }

                capabilityTracker.reset()
                _confirmedPids.value = emptySet()
                val supported = conn.querySupportedPids().toMutableSet()

                // Some ECUs return useful live data for PIDs omitted from their
                // support bitmap. Confirm these two fuel PIDs with two direct,
                // read-only requests before deciding not to poll them.
                connection = conn
                for (fuelPid in listOf(ELM327Protocol.PID.MAF, ELM327Protocol.PID.FUEL_LEVEL)) {
                    if (fuelPid !in supported) {
                        val confirmed = try {
                            validateMode01Pid("7DF", fuelPid)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                        if (confirmed) supported.add(fuelPid)
                    }
                }
                _supportedPids.value = supported

                // Refresh EIA gas price at the start of every session
                val prefs = preferencesRepository.userPreferences.first()
                if (prefs.fuelPriceMode == FuelPriceMode.EIA_AVERAGE && prefs.eiaApiKey.isNotBlank()) {
                    val price = eiaRepository.fetchRegularGasolinePriceUsdPerGallon(prefs.eiaApiKey)
                    if (price != null) preferencesRepository.setEiaFuelPrice(price, System.currentTimeMillis())
                }

                connection = conn
                _connectionState.value = ConnectionState.Connected(deviceInfo.name, deviceInfo.address)
                _lastTripSummary.value = null
                activeSessionId = sessionRepository.startSession(deviceInfo.name)
                startPolling()
                if (isNissanVehicle) startNissanCvt7SessionPolling()
                candidateConnection = null
            } catch (cancelled: CancellationException) {
                candidateConnection?.let { candidate ->
                    candidate.close()
                    if (connection === candidate) connection = null
                }
                throw cancelled
            } catch (e: Exception) {
                candidateConnection?.let { candidate ->
                    candidate.close()
                    if (connection === candidate) connection = null
                }
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        stopNissanCvt7SessionPolling()
        mazdaCx5PollingEnabled = false
        scope.launch { sessionRepository.recordDiagnosticEvent("OBD_DISCONNECTED", "Adapter disconnected") }
        pollingJob?.cancel()
        pollingJob = null
        pollCycleCount = 0
        lastAdaptivePollAtMs.clear()
        multiPidCapable = null
        pollingPaused = pollingPauseCount.get() > 0  // outstanding operations retain their pause ownership
        connection?.close()
        connection = null
        _connectionState.value = ConnectionState.Disconnected
        _mazdaCx5DashboardData.value = null
        lastMazdaGearPollAtMs = 0L
        lastMazdaTemperaturePollAtMs = 0L
        lastMazdaGearFailureAtMs = 0L
        lastMazdaTemperatureFailureAtMs = 0L
        mazdaGearCapturedAtMs = null
        mazdaTemperatureCapturedAtMs = null
        _obdData.value = OBDData()
        _livePidValues.value = emptyMap()
        _lowVoltageWarning.value = false
        _supportedPids.value = emptySet()
        capabilityTracker.reset()
        _confirmedPids.value = emptySet()
        _readinessStatus.value = null
        _freezeFrames.value = emptyList()
        _activeAlerts.value = emptySet()
        _customPidValues.value = emptyMap()
        val endingSessionId = activeSessionId
        activeSessionId = null
        if (endingSessionId != null) {
            scope.launch { _lastTripSummary.value = sessionRepository.endSession(endingSessionId) }
        }
    }


    /**
     * Owns Nissan CVT7 acquisition at the OBD connection/session level, not at
     * the CVT screen lifecycle. Leaving the monitor screen must never stop
     * transmission samples from being written to an active trip.
     */
    private fun startNissanCvt7SessionPolling() {
        if (!isNissanVehicle || connection?.isConnected != true || nissanCvt7PollingJob?.isActive == true) return
        val connectedAdapter = connection ?: return
        nissanCvt7PollingJob = scope.launch {
            _nissanCvt7Status.value = "Searching for the Nissan CVT7 transmission controller…"
            var discovery: com.obdiitools.obd.NissanCvt7DiscoveryResult? = null
            while (isActive && connection === connectedAdapter && connectedAdapter.isConnected && discovery == null) {
                if (pollingPaused) {
                    delay(250)
                    continue
                }
                discovery = try {
                    discoverNissanCvt7 { progress -> _nissanCvt7Status.value = progress }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _nissanCvt7Status.value = "Nissan CVT7 discovery error: ${error.message ?: error.javaClass.simpleName}"
                    null
                }
                if (discovery == null && connection === connectedAdapter && connectedAdapter.isConnected) {
                    sessionRepository.recordDiagnosticEvent(
                        "NISSAN_CVT7_DISCOVERY_FAILED",
                        _nissanCvt7Status.value,
                    )
                    delay(30_000)
                }
            }
            val confirmed = discovery ?: return@launch
            if (connection !== connectedAdapter || !connectedAdapter.isConnected) return@launch

            _nissanCvt7EcuAddress.value = confirmed.ecuAddress
            _nissanCvt7Status.value = "Nissan CVT7 controller confirmed at ${confirmed.ecuAddress}. Live acquisition continues during session recording, even when the CVT screen is closed."
            confirmed.liveData?.let { sample ->
                val decoded = toNissanCvtData(sample)
                _nissanCvt7Data.value = decoded
                sessionRepository.recordCvtData(decoded)
            }

            while (isActive && connection === connectedAdapter && connectedAdapter.isConnected) {
                if (pollingPaused) {
                    delay(250)
                    continue
                }
                val result = queryNissanCvt7LiveData(confirmed.ecuAddress)
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val sample = result.sample
                _nissanCvt7Diagnostics.value = buildString {
                    append("Last poll: ").append(time).append(" — ").append(result.parseStatus)
                    append("\nRaw 2101: ").append(result.rawResponse.ifBlank { "(empty)" })
                }
                if (sample != null) {
                    val decoded = toNissanCvtData(sample)
                    _nissanCvt7Data.value = decoded
                    sessionRepository.recordCvtData(decoded)
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNissanCvt7FailureEventAtMs >= 60_000L) {
                        lastNissanCvt7FailureEventAtMs = now
                        sessionRepository.recordDiagnosticEvent(
                            "NISSAN_CVT7_POLL_FAILED",
                            result.parseStatus + " | " + result.rawResponse.take(300),
                        )
                    }
                }
                // Keep transmission sampling useful without monopolizing the
                // serialized adapter transaction needed by standard OBD PIDs.
                delay(10_000)
            }
        }
    }

    private fun stopNissanCvt7SessionPolling() {
        nissanCvt7PollingJob?.cancel()
        nissanCvt7PollingJob = null
        _nissanCvt7Data.value = com.obdiitools.obd.CVTData()
        _nissanCvt7Status.value = "Nissan CVT7 monitor is not active."
        _nissanCvt7Diagnostics.value = "Waiting for first live poll…"
        _nissanCvt7EcuAddress.value = null
        lastNissanCvt7FailureEventAtMs = 0L
    }

    private fun toNissanCvtData(
        sample: com.obdiitools.obd.NissanCvt7LiveData,
    ): com.obdiitools.obd.CVTData {
        fun measured(value: Float) =
            com.obdiitools.obd.CVTReading(value, com.obdiitools.obd.CVTDataSource.SENSOR)
        return com.obdiitools.obd.CVTData(
            speedKph = com.obdiitools.obd.CVTReading(sample.vehicleSpeedKph),
            engineRpm = com.obdiitools.obd.CVTReading(sample.engineRpm),
            inputShaftRpm = com.obdiitools.obd.CVTReading(null),
            slipRpm = com.obdiitools.obd.CVTReading(sample.slipRpm, com.obdiitools.obd.CVTDataSource.SENSOR),
            lockupState = com.obdiitools.obd.CVTReading(
                com.obdiitools.obd.LockupState.UNKNOWN,
                com.obdiitools.obd.CVTDataSource.UNAVAILABLE,
            ),
            cvtTempC = com.obdiitools.obd.CVTReading(sample.fluidTemperatureC),
            cvtRatio = measured(sample.gearRatio),
            primaryPressureMpa = measured(sample.primaryPressureMpa),
            secondaryPressureMpa = measured(sample.secondaryPressureMpa),
            targetSecondaryPressureMpa = measured(sample.targetSecondaryPressureMpa),
            linePressureMpa = measured(sample.linePressureMpa),
            accelerator = measured(sample.accelerator),
            torqueConverterRatio = measured(sample.torqueConverterRatio),
            cvtTemperatureCount = com.obdiitools.obd.CVTReading(sample.cvtTemperatureCount),
            batteryVoltage = measured(sample.batteryVoltage),
            lockupCurrentAmps = measured(sample.lockupCurrentAmps),
            luPressureMpa = measured(sample.luPressureMpa),
            selectorPosition = com.obdiitools.obd.CVTReading(sample.selectorPosition),
            virtualGear = com.obdiitools.obd.CVTReading(sample.virtualGear),
            dGearRatio = measured(sample.dGearRatio),
            gForce = measured(sample.gForce),
            engineTorqueNm = measured(sample.engineTorqueNm),
            enginePowerHp = com.obdiitools.obd.CVTReading(sample.enginePowerHp),
            outputTorqueNm = com.obdiitools.obd.CVTReading(sample.outputTorqueNm),
            selectorStatusFlag = com.obdiitools.obd.CVTReading(sample.selectorStatusFlag),
        )
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var wasResumedFromPause = false
            while (isActive && connection?.isConnected == true) {
                if (pollingPaused) {
                    // Reset the no-response watchdog so pauses (VIN fetch, DTC scan, etc.)
                    // don't count against the adapter silence threshold.
                    connection?.resetActivityTimer()
                    wasResumedFromPause = true
                    delay(100)
                    continue
                }
                if (wasResumedFromPause) {
                    wasResumedFromPause = false
                    // Re-assert ELM327 state after any external operation that used the port
                    // (VIN fetch, DTC scan, etc.). Prevents stale ATSH, echo, or header settings
                    // from breaking response parsing.
                    val conn = connection ?: break
                    runCatching {
                        conn.query("ATSH 7DF", 300)
                        conn.query("ATE0", 300)
                        conn.query("ATH0", 300)
                        conn.query("ATS0", 300)
                    }
                }
                try {
                    pollAllPids()
                } catch (e: Exception) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.Error("Connection lost: ${e.message}")
                        disconnect()
                    }
                    return@launch
                }
                // Watchdog: handles the BT supervision timeout window (typically 5–20 s) where
                // writes still succeed but the adapter sends nothing back. IOException hasn't
                // fired yet, but the silence is a reliable signal that the link is gone.
                val conn = connection
                if (conn != null && System.currentTimeMillis() - conn.lastByteReceivedMs > NO_RESPONSE_TIMEOUT_MS) {
                    _connectionState.value = ConnectionState.Error("Adapter not responding")
                    disconnect()
                    return@launch
                }
                delay(50)
            }
            // Loop exited because isConnected became false (e.g. BLE adapter removed from OBD port).
            // If still nominally connected, treat this as an unplanned loss and clean up.
            if (isActive && _connectionState.value is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Error("Bluetooth connection lost")
                disconnect()
            }
        }
    }

    private suspend fun pollAllPids() {
        val conn = connection ?: return
        conn.withTransaction { pollAllPidsLocked(this) }
    }

    /** Caller owns the adapter transaction for the complete polling cycle. */
    private suspend fun pollAllPidsLocked(conn: OBDConnection) {
        if (pollingPaused) return
        val supported = _supportedPids.value

        fun filterPids(pids: List<String>) =
            if (supported.isEmpty()) pids else pids.filter { supported.contains(it.uppercase()) }

        // --- Fast PIDs every cycle ---
        val fastPids = filterPids(ELM327Protocol.FAST_PIDS)
        if (fastPids.isNotEmpty()) {
            if (multiPidCapable != false) {
                val lines = conn.queryLines(ELM327Protocol.buildMultiPidCommand(fastPids))
                val values = ELM327Protocol.parseMultiPidResponse(lines)
                if (values.isNotEmpty()) {
                    multiPidCapable = true
                    pollMissingPidsIndividually(conn, fastPids, values)
                } else {
                    multiPidCapable = false
                    pollSequential(conn, fastPids)
                }
            } else {
                pollSequential(conn, fastPids)
            }
        }

        // --- Mazda CX-5 TCM data (serialized with all other adapter I/O) ---
        pollMazdaCx5Transmission(conn)

        // --- Slow PIDs every 5th cycle (~1 Hz) ---
        if (pollCycleCount % 5 == 0) {
            if (pollingPaused) return
            val slowPids = filterPids(ELM327Protocol.SLOW_PIDS)
            slowPids.chunked(6).forEach { chunk ->
                if (pollingPaused) return
                if (multiPidCapable == true) {
                    val lines = conn.queryLines(ELM327Protocol.buildMultiPidCommand(chunk))
                    val values = ELM327Protocol.parseMultiPidResponse(lines)
                    if (values.isEmpty()) {
                        pollSequential(conn, chunk)
                    } else {
                        pollMissingPidsIndividually(conn, chunk, values)
                    }
                } else {
                    pollSequential(conn, chunk)
                }
            }

            // Poll additional sensors from the learned capability set. The
            // scheduler bounds work per pass, prioritizes responsive groups, and
            // uses overdue-time fairness so low-priority sensors cannot starve.
            if (supported.isNotEmpty() && !pollingPaused) {
                pollAdaptivePids(conn, supported)
            }

            // Poll custom PIDs
            val customPids = customPidRepository.customPids.first()
            if (customPids.isNotEmpty() && !pollingPaused) {
                pollCustomPids(conn, customPids)
            }

            checkAlerts()
        }

        // --- Battery voltage every 5th cycle (matches data-point cadence, ATRV is an AT command so ~50ms) ---
        if (pollCycleCount % 5 == 0 && !pollingPaused) {
            val voltage = ELM327Protocol.parseResponse(
                ELM327Protocol.PID.VOLTAGE,
                conn.query(ELM327Protocol.PID.VOLTAGE),
            ) as? Float
            if (voltage != null) {
                _obdData.value = _obdData.value.copy(batteryVoltage = voltage)
                _lowVoltageWarning.value = voltage < 12.2f
            }
        }

        // --- Session data point every 5th cycle (~1s) ---
        if (pollCycleCount % 5 == 0) {
            scope.launch { sessionRepository.recordDataPoint(_obdData.value) }
        }

        pollCycleCount = (pollCycleCount + 1) % 150
    }

    private suspend fun recordMazdaDiagnosticEventThrottled(
        key: String,
        eventType: String,
        detail: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastMazdaDiagnosticEventAtMs[key]
        if (previous != null && now - previous < MAZDA_DIAGNOSTIC_EVENT_INTERVAL_MS) return
        lastMazdaDiagnosticEventAtMs[key] = now
        sessionRepository.recordDiagnosticEvent(eventType, detail)
    }

    /**
     * Polls confirmed Mazda CX-5 TCM data and the four documented DSC wheel-speed
     * identifiers. All reads run inside the normal serialized polling transaction.
     */
    private suspend fun pollMazdaCx5Transmission(conn: OBDConnection) {
        if (!mazdaCx5PollingEnabled || pollingPaused) return
        val now = SystemClock.elapsedRealtime()
        val gearDue = now - lastMazdaGearPollAtMs >= MAZDA_GEAR_POLL_INTERVAL_MS &&
            now - lastMazdaGearFailureAtMs >= MAZDA_FAILED_READ_RETRY_MS
        val temperatureDue = now - lastMazdaTemperaturePollAtMs >= MAZDA_TEMPERATURE_POLL_INTERVAL_MS &&
            now - lastMazdaTemperatureFailureAtMs >= MAZDA_FAILED_READ_RETRY_MS
        val wheelSpeedsDue = now - lastMazdaWheelSpeedPollAtMs >= MAZDA_WHEEL_SPEED_POLL_INTERVAL_MS
        if (!gearDue && !temperatureDue && !wheelSpeedsDue) return

        var gear: String? = _mazdaCx5DashboardData.value?.transmission?.gear
        var temperatureC: Double? =
            _mazdaCx5DashboardData.value?.transmission?.transmissionTemperatureC
        var gearRaw = _mazdaCx5DashboardData.value?.transmission?.rawGearResponse.orEmpty()
        var temperatureRaw = _mazdaCx5DashboardData.value?.transmission?.rawTemperatureResponse.orEmpty()
        var frontLeftKph = _mazdaCx5DashboardData.value?.transmission?.frontLeftWheelSpeedKph
        var frontRightKph = _mazdaCx5DashboardData.value?.transmission?.frontRightWheelSpeedKph
        var rearLeftKph = _mazdaCx5DashboardData.value?.transmission?.rearLeftWheelSpeedKph
        var rearRightKph = _mazdaCx5DashboardData.value?.transmission?.rearRightWheelSpeedKph
        var receivedValidData = false

        if (gearDue) {
            lastMazdaGearPollAtMs = now
            try {
                val raw = conn.queryUds(
                    com.obdiitools.obd.MazdaCx5Protocol.TCM_REQUEST_ID,
                    com.obdiitools.obd.MazdaCx5Protocol.GEAR_DID,
                )
                val parsed = UdsResponse.parse(
                    com.obdiitools.obd.MazdaCx5Protocol.TCM_REQUEST_ID,
                    com.obdiitools.obd.MazdaCx5Protocol.GEAR_DID,
                    raw,
                )
                val decoded = com.obdiitools.obd.MazdaCx5Protocol.parseGear(parsed)
                if (decoded != null) {
                    gear = decoded
                    gearRaw = raw
                    mazdaGearCapturedAtMs = System.currentTimeMillis()
                    lastMazdaGearFailureAtMs = 0L
                    receivedValidData = true
                } else {
                    lastMazdaGearFailureAtMs = now
                    recordMazdaDiagnosticEventThrottled(
                        key = "1E12:${raw.trim().uppercase()}",
                        eventType = "MAZDA_TCM_READ_FAILED",
                        detail = "ECU=7E1 DID=1E12 raw=${raw.take(180)}"
                    )
                }
            } catch (error: Exception) {
                lastMazdaGearFailureAtMs = now
                recordMazdaDiagnosticEventThrottled(
                    key = "1E12:ERROR:${error.javaClass.simpleName}",
                    eventType = "MAZDA_TCM_READ_ERROR",
                    detail = "ECU=7E1 DID=1E12 ${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}"
                )
            }
        }

        if (temperatureDue && !pollingPaused) {
            lastMazdaTemperaturePollAtMs = now
            try {
                val raw = conn.queryUds(
                    com.obdiitools.obd.MazdaCx5Protocol.TCM_REQUEST_ID,
                    com.obdiitools.obd.MazdaCx5Protocol.TRANSMISSION_TEMPERATURE_DID,
                )
                val parsed = UdsResponse.parse(
                    com.obdiitools.obd.MazdaCx5Protocol.TCM_REQUEST_ID,
                    com.obdiitools.obd.MazdaCx5Protocol.TRANSMISSION_TEMPERATURE_DID,
                    raw,
                )
                val decoded = com.obdiitools.obd.MazdaCx5Protocol.parseTransmissionTemperatureC(parsed)
                if (decoded != null) {
                    temperatureC = decoded
                    temperatureRaw = raw
                    mazdaTemperatureCapturedAtMs = System.currentTimeMillis()
                    lastMazdaTemperatureFailureAtMs = 0L
                    receivedValidData = true
                } else {
                    lastMazdaTemperatureFailureAtMs = now
                    recordMazdaDiagnosticEventThrottled(
                        key = "1E1C:${raw.trim().uppercase()}",
                        eventType = "MAZDA_TCM_READ_FAILED",
                        detail = "ECU=7E1 DID=1E1C raw=${raw.take(180)}"
                    )
                }
            } catch (error: Exception) {
                lastMazdaTemperatureFailureAtMs = now
                recordMazdaDiagnosticEventThrottled(
                    key = "1E1C:ERROR:${error.javaClass.simpleName}",
                    eventType = "MAZDA_TCM_READ_ERROR",
                    detail = "ECU=7E1 DID=1E1C ${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}"
                )
            }
        }

        if (wheelSpeedsDue && !pollingPaused) {
            lastMazdaWheelSpeedPollAtMs = now
            val wheelDids = listOf(
                com.obdiitools.obd.MazdaCx5Protocol.FRONT_LEFT_WHEEL_SPEED_DID,
                com.obdiitools.obd.MazdaCx5Protocol.FRONT_RIGHT_WHEEL_SPEED_DID,
                com.obdiitools.obd.MazdaCx5Protocol.REAR_LEFT_WHEEL_SPEED_DID,
                com.obdiitools.obd.MazdaCx5Protocol.REAR_RIGHT_WHEEL_SPEED_DID,
            )
            val nextDid = (0 until wheelDids.size)
                .map { (nextMazdaWheelSpeedDidIndex + it) % wheelDids.size }
                .firstOrNull { index ->
                    now - (lastMazdaWheelSpeedFailureAtMs[wheelDids[index]] ?: 0L) >= MAZDA_FAILED_READ_RETRY_MS
                }
            if (nextDid != null) {
                val did = wheelDids[nextDid]
                nextMazdaWheelSpeedDidIndex = (nextDid + 1) % wheelDids.size
                try {
                    val raw = conn.queryUds(com.obdiitools.obd.MazdaCx5Protocol.DSC_REQUEST_ID, did)
                    val parsed = UdsResponse.parse(
                        com.obdiitools.obd.MazdaCx5Protocol.DSC_REQUEST_ID, did, raw
                    )
                    val decoded = com.obdiitools.obd.MazdaCx5Protocol.parseWheelSpeedKph(parsed, did)
                    if (decoded != null) {
                        when (did) {
                            com.obdiitools.obd.MazdaCx5Protocol.FRONT_LEFT_WHEEL_SPEED_DID -> frontLeftKph = decoded
                            com.obdiitools.obd.MazdaCx5Protocol.FRONT_RIGHT_WHEEL_SPEED_DID -> frontRightKph = decoded
                            com.obdiitools.obd.MazdaCx5Protocol.REAR_LEFT_WHEEL_SPEED_DID -> rearLeftKph = decoded
                            com.obdiitools.obd.MazdaCx5Protocol.REAR_RIGHT_WHEEL_SPEED_DID -> rearRightKph = decoded
                        }
                        mazdaWheelSpeedCapturedAtMs[did] = System.currentTimeMillis()
                        lastMazdaWheelSpeedFailureAtMs[did] = 0L
                        receivedValidData = true
                    } else {
                        lastMazdaWheelSpeedFailureAtMs[did] = now
                        recordMazdaDiagnosticEventThrottled(
                            key = "DSC:$did:${raw.trim().uppercase()}",
                            eventType = "MAZDA_DSC_WHEEL_SPEED_READ_FAILED",
                            detail = "ECU=760 DID=$did raw=${raw.take(180)}"
                        )
                    }
                } catch (error: Exception) {
                    lastMazdaWheelSpeedFailureAtMs[did] = now
                    recordMazdaDiagnosticEventThrottled(
                        key = "DSC:$did:ERROR:${error.javaClass.simpleName}",
                        eventType = "MAZDA_DSC_WHEEL_SPEED_READ_ERROR",
                        detail = "ECU=760 DID=$did ${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}"
                    )
                }
            }
        }

        val capturedAt = System.currentTimeMillis()
        val gearFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaGearCapturedAtMs, capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        val temperatureFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaTemperatureCapturedAtMs, capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        if (!gearFresh) gear = null
        if (!temperatureFresh) temperatureC = null
        val frontLeftFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.FRONT_LEFT_WHEEL_SPEED_DID], capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        val frontRightFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.FRONT_RIGHT_WHEEL_SPEED_DID], capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        val rearLeftFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.REAR_LEFT_WHEEL_SPEED_DID], capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        val rearRightFresh = com.obdiitools.obd.MazdaCx5Protocol.isFresh(
            mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.REAR_RIGHT_WHEEL_SPEED_DID], capturedAt, MAZDA_DATA_STALE_AFTER_MS
        )
        if (!frontLeftFresh) frontLeftKph = null
        if (!frontRightFresh) frontRightKph = null
        if (!rearLeftFresh) rearLeftKph = null
        if (!rearRightFresh) rearRightKph = null

        val previousDashboard = _mazdaCx5DashboardData.value
        val staleValueExpired =
            previousDashboard?.transmission?.gear != gear ||
                previousDashboard?.transmission?.transmissionTemperatureC != temperatureC ||
                previousDashboard?.transmission?.frontLeftWheelSpeedKph != frontLeftKph ||
                previousDashboard?.transmission?.frontRightWheelSpeedKph != frontRightKph ||
                previousDashboard?.transmission?.rearLeftWheelSpeedKph != rearLeftKph ||
                previousDashboard?.transmission?.rearRightWheelSpeedKph != rearRightKph
        if (receivedValidData || gearDue || temperatureDue || wheelSpeedsDue || staleValueExpired) {
            val data = MazdaCx5TransmissionData(
                gear = gear,
                transmissionTemperatureC = temperatureC,
                rawGearResponse = gearRaw,
                rawTemperatureResponse = temperatureRaw,
                frontLeftWheelSpeedKph = frontLeftKph,
                frontRightWheelSpeedKph = frontRightKph,
                rearLeftWheelSpeedKph = rearLeftKph,
                rearRightWheelSpeedKph = rearRightKph,
            )
            _mazdaCx5DashboardData.value = if (gearFresh || temperatureFresh || frontLeftFresh || frontRightFresh || rearLeftFresh || rearRightFresh) {
                MazdaCx5DashboardData(
                    transmission = data,
                    capturedAtMs = capturedAt,
                    gearCapturedAtMs = mazdaGearCapturedAtMs.takeIf { gearFresh },
                    temperatureCapturedAtMs = mazdaTemperatureCapturedAtMs.takeIf { temperatureFresh },
                    frontLeftWheelSpeedCapturedAtMs = mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.FRONT_LEFT_WHEEL_SPEED_DID].takeIf { frontLeftFresh },
                    frontRightWheelSpeedCapturedAtMs = mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.FRONT_RIGHT_WHEEL_SPEED_DID].takeIf { frontRightFresh },
                    rearLeftWheelSpeedCapturedAtMs = mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.REAR_LEFT_WHEEL_SPEED_DID].takeIf { rearLeftFresh },
                    rearRightWheelSpeedCapturedAtMs = mazdaWheelSpeedCapturedAtMs[com.obdiitools.obd.MazdaCx5Protocol.REAR_RIGHT_WHEEL_SPEED_DID].takeIf { rearRightFresh },
                )
            } else null
        }

        if (receivedValidData && (gearFresh || temperatureFresh || frontLeftFresh || frontRightFresh || rearLeftFresh || rearRightFresh)) {
            sessionRepository.recordMazdaTcmData(
                gear = gear.takeIf { gearFresh },
                transmissionTemperatureC = temperatureC.takeIf { temperatureFresh },
                frontLeftWheelSpeedKph = frontLeftKph.takeIf { frontLeftFresh },
                frontRightWheelSpeedKph = frontRightKph.takeIf { frontRightFresh },
                rearLeftWheelSpeedKph = rearLeftKph.takeIf { rearLeftFresh },
                rearRightWheelSpeedKph = rearRightKph.takeIf { rearRightFresh },
            )
        }
    }

    private suspend fun pollAdaptivePids(conn: OBDConnection, supported: Set<String>) {
        val now = SystemClock.elapsedRealtime()
        val standardPids = (ELM327Protocol.FAST_PIDS + ELM327Protocol.SLOW_PIDS).toSet()
        val adaptiveSupported = supported.map { it.uppercase() }.toSet() - standardPids
        if (adaptiveSupported.isEmpty()) return
        val confirmed = _confirmedPids.value
        val capabilities = capabilityTracker.capabilities.value
        val due = adaptivePidScheduler.selectDue(
            supportedPids = adaptiveSupported,
            confirmedPids = confirmed,
            capabilities = capabilities,
            lastPolledAtMs = lastAdaptivePollAtMs,
            nowMs = now,
        )
        for (pid in due) {
            if (pollingPaused) return
            // Stamp before the request to prevent a slow/no-response PID from
            // being retried repeatedly in the same connection window.
            lastAdaptivePollAtMs[pid] = SystemClock.elapsedRealtime()
            val raw = conn.query(pid, 700)
            val value = ELM327Protocol.parseResponse(pid, raw)
            applyPidValues(mapOf(pid to value))
        }
    }

    /**
     * Multi-PID replies are not guaranteed to contain every requested PID.
     * Some ELM adapters/ECUs silently omit unsupported or delayed responses
     * while returning the rest of the batch. Retry every missing or malformed
     * item individually so one good PID cannot mask several dead dashboard
     * fields, and so a failed individual read clears any stale value.
     */
    private suspend fun pollMissingPidsIndividually(
        conn: OBDConnection,
        requestedPids: List<String>,
        batchValues: Map<String, Any?>,
    ) {
        val resolved = batchValues.toMutableMap()
        val missingOrMalformed = requestedPids.filter { pid ->
            !batchValues.containsKey(pid) || batchValues[pid] == null
        }
        for (pid in missingOrMalformed) {
            if (pollingPaused) return
            resolved[pid] = ELM327Protocol.parseResponse(pid, conn.query(pid))
        }
        applyPidValues(resolved)
    }

    private suspend fun pollSequential(conn: OBDConnection, pids: List<String>) {
        for (pid in pids) {
            if (pollingPaused) return
            applyPidValues(mapOf(pid to ELM327Protocol.parseResponse(pid, conn.query(pid))))
        }
    }

    private suspend fun pollCustomPids(conn: OBDConnection, pids: List<CustomPidDefinition>) {
        val results = mutableMapOf<String, Float?>()
        for (pid in pids) {
            if (pollingPaused) return
            if (!CustomPidDefinition.isValidCommand(pid.command)) {
                results[pid.id] = null
                continue
            }
            val raw = conn.query(pid.command, 500)
            results[pid.id] = pid.evaluate(raw)
        }
        _customPidValues.value = results
    }

    private fun isPlausibleMode01Value(pid: String, value: Any?): Boolean {
        val number = (value as? Number)?.toFloat() ?: return false
        if (!number.isFinite()) return false
        val definition = com.obdiitools.obd.AllPidDefinitions.ALL
            .firstOrNull { it.command == pid.uppercase() } ?: return true
        return number in definition.minValue..definition.maxValue
    }

    private fun applyPidValues(values: Map<String, Any?>) {
        var current = _obdData.value
        val genericValues = _livePidValues.value.toMutableMap()
        for ((pid, value) in values) {
            val normalizedPid = pid.uppercase()
            val plausible = isPlausibleMode01Value(normalizedPid, value)
            capabilityTracker.record(normalizedPid, value.takeIf { plausible })
            val numericValue = (value as? Number)?.toFloat()
            if (!plausible || numericValue == null) genericValues.remove(normalizedPid)
            else genericValues[normalizedPid] = numericValue
            // A present PID with a null parse result means the adapter returned
            // NO DATA or malformed bytes. Clear the old value instead of
            // retaining a stale dashboard reading indefinitely.
            current = when (pid.uppercase()) {
                ELM327Protocol.PID.RPM          -> current.copy(rpm = value as? Int)
                ELM327Protocol.PID.SPEED        -> current.copy(speedKph = value as? Int)
                ELM327Protocol.PID.THROTTLE     -> current.copy(throttlePercent = value as? Float)
                ELM327Protocol.PID.COOLANT_TEMP -> current.copy(coolantTempC = value as? Int)
                ELM327Protocol.PID.ENGINE_LOAD  -> current.copy(engineLoadPercent = value as? Float)
                ELM327Protocol.PID.MAF          -> current.copy(mafGramsPerSec = value as? Float)
                ELM327Protocol.PID.INTAKE_TEMP  -> current.copy(intakeAirTempC = value as? Int)
                ELM327Protocol.PID.TIMING_ADV   -> current.copy(timingAdvanceDeg = value as? Float)
                ELM327Protocol.PID.FUEL_LEVEL   -> current.copy(fuelLevelPercent = value as? Float)
                ELM327Protocol.PID.AMBIENT_TEMP -> current.copy(ambientTempC = value as? Int)
                ELM327Protocol.PID.STFT_BANK1   -> current.copy(stftBank1Pct = value as? Float)
                ELM327Protocol.PID.LTFT_BANK1   -> current.copy(ltftBank1Pct = value as? Float)
                ELM327Protocol.PID.STFT_BANK2   -> current.copy(stftBank2Pct = value as? Float)
                ELM327Protocol.PID.LTFT_BANK2   -> current.copy(ltftBank2Pct = value as? Float)
                ELM327Protocol.PID.O2_B1S1      -> current.copy(o2Bank1S1Volts = value as? Float)
                ELM327Protocol.PID.O2_B1S2      -> current.copy(o2Bank1S2Volts = value as? Float)
                ELM327Protocol.PID.O2_B2S1      -> current.copy(o2Bank2S1Volts = value as? Float)
                ELM327Protocol.PID.O2_B2S2      -> current.copy(o2Bank2S2Volts = value as? Float)
                ELM327Protocol.PID.OIL_TEMP     -> current.copy(oilTempC = value as? Int)
                ELM327Protocol.PID.BARO_PRESSURE -> current.copy(baroPressureKpa = value as? Int)
                ELM327Protocol.PID.FUEL_PRESSURE -> current.copy(fuelPressureKpa = value as? Int)
                else -> current
            }
        }
        _obdData.value = current
        _livePidValues.value = genericValues
        _confirmedPids.value = capabilityTracker.confirmedPids
        persistCapabilityProfile()
    }

    private fun checkAlerts() {
        val thresholds = cachedThresholds
        val data = _obdData.value
        val prev = _activeAlerts.value
        val current = mutableSetOf<AlertType>()

        data.coolantTempC?.let { if (it > thresholds.coolantMaxC) current.add(AlertType.HIGH_COOLANT) }
        data.fuelLevelPercent?.let { if (it < thresholds.fuelMinPct) current.add(AlertType.LOW_FUEL) }
        data.batteryVoltage?.let { if (it < thresholds.batteryMinV) current.add(AlertType.LOW_BATTERY) }
        data.rpm?.let { if (it > thresholds.rpmMax) current.add(AlertType.HIGH_RPM) }

        // Edge detection: fire notification only on new alerts (ok → alert transitions)
        val newAlerts = current - prev
        if (newAlerts.isNotEmpty()) {
            newAlerts.forEach { postAlertNotification(it) }
        }

        _activeAlerts.value = current
    }

    private fun postAlertNotification(alertType: AlertType) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, "OBD Alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("OBD Alert: ${alertType.label}")
            .setContentText(alertType.label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId.getAndIncrement(), notification)
    }

    // Collects all frames of a Mode 03/07 DTC response, strips ELM327 "N:" frame-index
    // prefixes, and joins the raw hex bytes into one compact string for parseDTCs().
    private suspend fun readDtcRaw(conn: OBDConnection, command: String): String {
        val framePrefix = Regex("^[0-9A-F]:")
        return conn.queryLines(command, 2000)
            .map { it.replace(" ", "").uppercase() }
            .joinToString("") { line ->
                if (framePrefix.containsMatchIn(line)) line.substring(2) else line
            }
    }

    suspend fun scanDTCs() {
        val conn = connection ?: return
        pausePolling()
        try {
            delay(120)
            conn.withTransaction {
                val tx = this
                val stored = readDtcRaw(tx, ELM327Protocol.PID.GET_STORED_DTC)
                val pending = readDtcRaw(tx, ELM327Protocol.PID.GET_PENDING_DTC)
                val storedDtcs = ELM327Protocol.parseDTCs(stored, false)
                val pendingDtcs = ELM327Protocol.parseDTCs(pending, true)
                val allDtcs = (storedDtcs + pendingDtcs).map { dtc ->
                    ObdexDtcDatabase.enrich(context, dtc)
                }.distinctBy { it.code }
                _dtcList.value = allDtcs
                if (storedDtcs.isNotEmpty()) fetchFreezeFrames(tx, storedDtcs)
            }
        } catch (_: java.io.IOException) {
            // Adapter went offline mid-scan; polling loop handles disconnection.
        } finally {
            resumePolling()
        }
    }

    private suspend fun fetchFreezeFrames(conn: OBDConnection, dtcs: List<DTC>) {
        val frames = dtcs.map { dtc ->
            val pids = listOf(
                ELM327Protocol.PID.RPM, ELM327Protocol.PID.SPEED,
                ELM327Protocol.PID.COOLANT_TEMP, ELM327Protocol.PID.THROTTLE,
                ELM327Protocol.PID.ENGINE_LOAD, ELM327Protocol.PID.MAF,
                ELM327Protocol.PID.STFT_BANK1, ELM327Protocol.PID.LTFT_BANK1,
                ELM327Protocol.PID.INTAKE_TEMP, ELM327Protocol.PID.TIMING_ADV,
            )
            val values = mutableMapOf<String, Any?>()
            for (pid in pids) {
                val raw = conn.queryFreezeFrame(pid)
                if (raw.isNotBlank()) {
                    // Freeze frame response uses mode 02 (42 prefix); only replace the first two
                    // chars so data bytes that happen to contain 0x42 are not corrupted.
                    val converted = if (raw.length >= 2) "41" + raw.substring(2) else raw
                    values[pid] = ELM327Protocol.parseResponse(pid, converted)
                }
            }
            FreezeFrame(
                dtcCode           = dtc.code,
                rpm               = values[ELM327Protocol.PID.RPM] as? Int,
                speedKph          = values[ELM327Protocol.PID.SPEED] as? Int,
                coolantTempC      = values[ELM327Protocol.PID.COOLANT_TEMP] as? Int,
                throttlePercent   = values[ELM327Protocol.PID.THROTTLE] as? Float,
                engineLoadPercent = values[ELM327Protocol.PID.ENGINE_LOAD] as? Float,
                mafGramsPerSec    = values[ELM327Protocol.PID.MAF] as? Float,
                stftBank1Pct      = values[ELM327Protocol.PID.STFT_BANK1] as? Float,
                ltftBank1Pct      = values[ELM327Protocol.PID.LTFT_BANK1] as? Float,
                intakeAirTempC    = values[ELM327Protocol.PID.INTAKE_TEMP] as? Int,
                timingAdvanceDeg  = values[ELM327Protocol.PID.TIMING_ADV] as? Float,
            )
        }
        _freezeFrames.value = frames.filter { it.hasData }
    }

    suspend fun fetchReadinessMonitors() {
        val conn = connection ?: return
        pausePolling()
        try {
            delay(120)
            val raw = conn.withTransaction { query(ELM327Protocol.PID.READINESS, 1000) }
            _readinessStatus.value = parseReadinessStatus(raw)
        } catch (_: java.io.IOException) {
            // adapter went offline; polling loop will handle disconnect
        } finally {
            resumePolling()
        }
    }

    fun canMonitorFlow(): Flow<CanFrame> =
        (connection?.canMonitorFlow() ?: emptyFlow()).mapNotNull { CanFrame.parse(it) }

    suspend fun queryUds(ecuAddress: String, did: String): UdsResponse {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$")) ||
            !did.matches(Regex("(?i)^[0-9A-F]{4}$"))) {
            return UdsResponse.parse(ecuAddress, did, "ERROR")
        }
        val conn = connection ?: return UdsResponse.parse(ecuAddress, did, "")
        val raw = conn.queryUds(ecuAddress, did)
        return UdsResponse.parse(ecuAddress, did, raw)
    }

    suspend fun scanEcus(
        onResult: (com.obdiitools.obd.EcuScanResult) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val conn = connection ?: return
        // Nissan and many other vehicles use standard 11-bit diagnostic
        // request IDs 0x7E0-0x7E7 for powertrain ECUs (e.g. ECM/TCM).
        // Keep the manufacturer-specific 0x700 range as well.
        val addresses = ((0x700..0x77F) + (0x7E0..0x7E7)).map { "%03X".format(it) }
        val total = addresses.size
        conn.withTransaction {
            val tx = this
            try {
                withContext(Dispatchers.IO) {
                    for ((index, addr) in addresses.withIndex()) {
                        onProgress(index, total)
                        val raw = tx.pingEcu(addr)
                        val result = com.obdiitools.obd.parseEcuScanResult(addr, raw)
                        if (result != null) onResult(result)
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { tx.query("ATSH 7DF", 300) } }
            }
        }
        onProgress(total, total)
    }

    suspend fun clearDTCs(): Boolean {
        val conn = connection ?: return false
        pausePolling()
        return try {
            delay(120)
            val response = conn.withTransaction { query(ELM327Protocol.PID.CLEAR_DTC) }
            if (response.contains("ERROR", ignoreCase = true) || response.contains("?")) {
                false
            } else {
                _dtcList.value = emptyList()
                _freezeFrames.value = emptyList()
                true
            }
        } catch (e: Exception) {
            false
        } finally {
            resumePolling()
        }
    }

    companion object {
        private const val MAZDA_GEAR_POLL_INTERVAL_MS = 1_000L
        private const val MAZDA_TEMPERATURE_POLL_INTERVAL_MS = 2_500L
        private const val MAZDA_WHEEL_SPEED_POLL_INTERVAL_MS = 250L
        private const val MAZDA_FAILED_READ_RETRY_MS = 5_000L
        private const val MAZDA_DIAGNOSTIC_EVENT_INTERVAL_MS = 60_000L
        private const val MAZDA_DATA_STALE_AFTER_MS = 10_000L

        // Candidate 11-bit Nissan enhanced-diagnostic addresses. Accept only
        // a valid CVT7 0x21/0x01 data block, not merely an ECU response.
        private val NISSAN_CVT7_ECU_CANDIDATES = listOf("7E1")
        private const val ALERT_CHANNEL_ID = "obd_alerts"
        private const val NO_RESPONSE_TIMEOUT_MS = 10_000L
    }
}
