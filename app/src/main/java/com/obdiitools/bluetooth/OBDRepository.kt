package com.obdiitools.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
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
import com.obdiitools.obd.ReadinessStatus
import com.obdiitools.obd.UdsResponse
import com.obdiitools.obd.parseReadinessStatus
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val _dtcList = MutableStateFlow<List<DTC>>(emptyList())
    val dtcList: StateFlow<List<DTC>> = _dtcList.asStateFlow()

    private val _lowVoltageWarning = MutableStateFlow(false)
    val lowVoltageWarning: StateFlow<Boolean> = _lowVoltageWarning.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

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
    private var pollingJob: Job? = null
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
                lines.firstOrNull { line ->
                    val cleaned = line.filterNot { it.isWhitespace() }.uppercase()
                    cleaned.startsWith("62") || cleaned.startsWith("7F")
                } ?: lines.firstOrNull().orEmpty()
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
     * Searches the known Nissan enhanced-diagnostic CAN addresses for a CVT7
     * controller. A controller is accepted only when it enters the requested
     * diagnostic session and returns a structurally valid 0x21/0x01 data block.
     * All requests are reads/session selection; no clear, reset, coding, or
     * actuator commands are sent.
     */
    suspend fun discoverNissanCvt7(
        onProgress: (String) -> Unit = {},
    ): com.obdiitools.obd.NissanCvt7DiscoveryResult? {
        val conn = connection ?: return null
        return conn.withTransaction {
            try {
                query("ATSP6", 600)
                query("ATE0", 300)
                query("ATH0", 300)
                query("ATS0", 300)
                query("ATST32", 300)

                for (address in NISSAN_CVT7_ECU_CANDIDATES) {
                    onProgress("Checking Nissan transmission address $address…")
                    query("ATSH $address", 400)
                    val session = queryLines("10C0", 1500)
                    val sessionAccepted = session.any { line ->
                        line.filterNot(Char::isWhitespace).uppercase().startsWith("50C0")
                    }
                    if (!sessionAccepted) continue

                    val response = queryLines("2101", 3500)
                    val sample = com.obdiitools.obd.NissanCvt7Protocol.parseLiveData(response)
                    if (sample != null) {
                        onProgress("Nissan CVT7 controller confirmed at $address")
                        return@withTransaction com.obdiitools.obd.NissanCvt7DiscoveryResult(address, sample)
                    }
                }
                null
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { query("ATSH 7DF", 400) }
                    runCatching { query("ATSP0", 600) }
                }
            }
        }
    }

    /** Reads one sample from the already-discovered physical controller address. */
    suspend fun queryNissanCvt7LiveData(
        ecuAddress: String,
    ): com.obdiitools.obd.NissanCvt7LiveData? {
        if (!ecuAddress.matches(Regex("(?i)^[0-9A-F]{3}$"))) return null
        val conn = connection ?: return null
        return conn.withTransaction {
            try {
                query("ATSP6", 600)
                query("ATSH ${ecuAddress.uppercase()}", 400)
                query("ATST32", 300)
                val session = queryLines("10C0", 1200)
                if (session.none { it.filterNot(Char::isWhitespace).uppercase().startsWith("50C0") }) {
                    return@withTransaction null
                }
                val response = queryLines("2101", 3000)
                com.obdiitools.obd.NissanCvt7Protocol.parseLiveData(response)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
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

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceInfo: BluetoothDeviceInfo) {
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
                        socket.connect()
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

                val supported = conn.querySupportedPids()
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
                sessionRepository.startSession(deviceInfo.name)
                startPolling()
                candidateConnection = null
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
        pollingJob?.cancel()
        pollingJob = null
        pollCycleCount = 0
        multiPidCapable = null
        pollingPaused = pollingPauseCount.get() > 0  // outstanding operations retain their pause ownership
        connection?.close()
        connection = null
        _connectionState.value = ConnectionState.Disconnected
        _obdData.value = OBDData()
        _lowVoltageWarning.value = false
        _supportedPids.value = emptySet()
        _readinessStatus.value = null
        _freezeFrames.value = emptyList()
        _activeAlerts.value = emptySet()
        _customPidValues.value = emptyMap()
        scope.launch { _lastTripSummary.value = sessionRepository.endSession() }
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
                    applyPidValues(values)
                } else {
                    multiPidCapable = false
                    pollSequential(conn, fastPids)
                }
            } else {
                pollSequential(conn, fastPids)
            }
        }

        // --- Slow PIDs every 5th cycle (~1 Hz) ---
        if (pollCycleCount % 5 == 0) {
            if (pollingPaused) return
            val slowPids = filterPids(ELM327Protocol.SLOW_PIDS)
            slowPids.chunked(6).forEach { chunk ->
                if (pollingPaused) return
                if (multiPidCapable == true) {
                    val lines = conn.queryLines(ELM327Protocol.buildMultiPidCommand(chunk))
                    applyPidValues(ELM327Protocol.parseMultiPidResponse(lines))
                } else {
                    pollSequential(conn, chunk)
                }
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

    private fun applyPidValues(values: Map<String, Any?>) {
        var current = _obdData.value
        for ((pid, value) in values) {
            current = when (pid.uppercase()) {
                // No stale fallback: adapter returns NO DATA when stopped, which must propagate as null.
                ELM327Protocol.PID.RPM          -> current.copy(rpm = value as? Int)
                ELM327Protocol.PID.SPEED        -> current.copy(speedKph = value as? Int)
                ELM327Protocol.PID.THROTTLE     -> current.copy(throttlePercent = (value as? Float) ?: current.throttlePercent)
                ELM327Protocol.PID.COOLANT_TEMP -> current.copy(coolantTempC = (value as? Int) ?: current.coolantTempC)
                ELM327Protocol.PID.ENGINE_LOAD  -> current.copy(engineLoadPercent = (value as? Float) ?: current.engineLoadPercent)
                ELM327Protocol.PID.MAF          -> current.copy(mafGramsPerSec = (value as? Float) ?: current.mafGramsPerSec)
                ELM327Protocol.PID.INTAKE_TEMP  -> current.copy(intakeAirTempC = (value as? Int) ?: current.intakeAirTempC)
                ELM327Protocol.PID.TIMING_ADV   -> current.copy(timingAdvanceDeg = (value as? Float) ?: current.timingAdvanceDeg)
                ELM327Protocol.PID.FUEL_LEVEL   -> current.copy(fuelLevelPercent = (value as? Float) ?: current.fuelLevelPercent)
                ELM327Protocol.PID.AMBIENT_TEMP -> current.copy(ambientTempC = (value as? Int) ?: current.ambientTempC)
                ELM327Protocol.PID.STFT_BANK1   -> current.copy(stftBank1Pct = (value as? Float) ?: current.stftBank1Pct)
                ELM327Protocol.PID.LTFT_BANK1   -> current.copy(ltftBank1Pct = (value as? Float) ?: current.ltftBank1Pct)
                ELM327Protocol.PID.STFT_BANK2   -> current.copy(stftBank2Pct = (value as? Float) ?: current.stftBank2Pct)
                ELM327Protocol.PID.LTFT_BANK2   -> current.copy(ltftBank2Pct = (value as? Float) ?: current.ltftBank2Pct)
                ELM327Protocol.PID.O2_B1S1      -> current.copy(o2Bank1S1Volts = (value as? Float) ?: current.o2Bank1S1Volts)
                ELM327Protocol.PID.O2_B1S2      -> current.copy(o2Bank1S2Volts = (value as? Float) ?: current.o2Bank1S2Volts)
                ELM327Protocol.PID.O2_B2S1      -> current.copy(o2Bank2S1Volts = (value as? Float) ?: current.o2Bank2S1Volts)
                ELM327Protocol.PID.O2_B2S2      -> current.copy(o2Bank2S2Volts = (value as? Float) ?: current.o2Bank2S2Volts)
                ELM327Protocol.PID.OIL_TEMP      -> current.copy(oilTempC = (value as? Int) ?: current.oilTempC)
                ELM327Protocol.PID.BARO_PRESSURE -> current.copy(baroPressureKpa = (value as? Int) ?: current.baroPressureKpa)
                ELM327Protocol.PID.FUEL_PRESSURE -> current.copy(fuelPressureKpa = (value as? Int) ?: current.fuelPressureKpa)
                else -> current
            }
        }
        _obdData.value = current
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
                    dtc.copy(description = DTCDatabase.getDescription(dtc.code))
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
                        val raw = tx.pingEcu(addr).replace(" ", "").uppercase().trim()
                        val result = when {
                            raw.startsWith("5001") ->
                                com.obdiitools.obd.EcuScanResult(addr, hasPositiveResponse = true)
                            raw.startsWith("7F") && raw.length >= 6 ->
                                com.obdiitools.obd.EcuScanResult(addr, hasPositiveResponse = false, nrc = raw.substring(4, 6))
                            else -> null
                        }
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
        // Candidate 11-bit Nissan enhanced-diagnostic addresses. Accept only
        // a valid CVT7 0x21/0x01 data block, not merely an ECU response.
        private val NISSAN_CVT7_ECU_CANDIDATES = listOf("7E1", "752", "745", "748")
        private const val ALERT_CHANNEL_ID = "obd_alerts"
        private const val NO_RESPONSE_TIMEOUT_MS = 10_000L
    }
}
