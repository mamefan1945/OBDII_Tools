package com.obdiitools.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.obd.CanFrame
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.DbcMessage
import com.obdiitools.obd.DbcSignal
import com.obdiitools.obd.DecodedSignal
import com.obdiitools.util.CanDecoder
import com.obdiitools.util.DbcParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

private const val MAX_FRAMES = 500

@HiltViewModel
class CanMonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OBDRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private val _frames       = MutableStateFlow<List<CanFrame>>(emptyList())
    private val _filterText   = MutableStateFlow("")
    private val _isMonitoring = MutableStateFlow(false)
    private val _showDecoded  = MutableStateFlow(false)
    private val _showUnknown  = MutableStateFlow(false)
    private val _dbcLoaded    = MutableStateFlow(false)
    private val _decodedMap   = MutableStateFlow<Map<String, DecodedSignal>>(emptyMap())
    private val _captureCount = MutableStateFlow(0)

    val captureCount: StateFlow<Int> = _captureCount.asStateFlow()
    private val captureLock = Any()
    private var captureFile: File? = null
    private var captureWriter: BufferedWriter? = null
    private var captureStartedAtMs = 0L
    private var firstFrameNanos: Long? = null

    val filterText:   StateFlow<String>  = _filterText.asStateFlow()
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    val showDecoded:  StateFlow<Boolean> = _showDecoded.asStateFlow()
    val showUnknown:  StateFlow<Boolean> = _showUnknown.asStateFlow()
    val dbcLoaded:    StateFlow<Boolean> = _dbcLoaded.asStateFlow()

    // Raw frames filtered by text input
    val filteredFrames: StateFlow<List<CanFrame>> = combine(_frames, _filterText) { frames, filter ->
        if (filter.isBlank()) frames
        else {
            val upper = filter.uppercase()
            frames.filter { it.id.contains(upper) || it.dataHex.contains(upper) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Decoded signals filtered by text and unknown-signal visibility
    val decodedSignals: StateFlow<List<DecodedSignal>> =
        combine(_decodedMap, _filterText, _showUnknown) { map, filter, showUnknown ->
            map.values
                .filter { showUnknown || !it.isUnknown }
                .filter {
                    filter.isBlank() ||
                    it.signalName.contains(filter, ignoreCase = true) ||
                    it.messageName.contains(filter, ignoreCase = true)
                }
                .sortedWith(compareBy({ it.messageName }, { it.signalName }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var signalMap: Map<Long, Pair<DbcMessage, List<DbcSignal>>> = emptyMap()
    private var monitorJob: Job? = null

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

    @Synchronized
    private fun finishMonitoring(lease: Long) {
        if (activePauseLease == lease) {
            activePauseLease = null
            _isMonitoring.value = false
            synchronized(captureLock) {
                runCatching { captureWriter?.flush(); captureWriter?.close() }
                captureWriter = null
            }
            repository.resumePolling()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = context.assets.open("mazda_2017.dbc").bufferedReader().readText()
                signalMap = DbcParser.parse(text)
                _dbcLoaded.value = true
            }
        }
    }

    fun setFilter(text: String)  { _filterText.value  = text }
    fun toggleDecoded()          { _showDecoded.value  = !_showDecoded.value }
    fun toggleUnknown()          { _showUnknown.value  = !_showUnknown.value }

    fun clearFrames() {
        _frames.value = emptyList()
        _decodedMap.value = emptyMap()
        synchronized(captureLock) {
            runCatching { captureWriter?.close() }
            captureWriter = null
            captureFile?.delete()
            captureFile = null
            firstFrameNanos = null
            _captureCount.value = 0
        }
    }

    private fun beginCapture() {
        synchronized(captureLock) {
            runCatching { captureWriter?.close() }
            captureFile?.delete()
            captureStartedAtMs = System.currentTimeMillis()
            firstFrameNanos = null
            _captureCount.value = 0
            val file = File(context.cacheDir, "can_capture_${captureStartedAtMs}.csv")
            captureFile = file
            captureWriter = BufferedWriter(FileWriter(file, false)).apply {
                write("timestamp_epoch_ms,elapsed_ms,can_id,dlc,data_hex,data_bytes,raw_line\\n")
                flush()
            }
        }
    }

    private fun recordCaptureFrame(frame: CanFrame) {
        synchronized(captureLock) {
            val writer = captureWriter ?: return
            val first = firstFrameNanos ?: frame.receivedAt.also { firstFrameNanos = it }
            val elapsedMs = ((frame.receivedAt - first) / 1_000_000L).coerceAtLeast(0L)
            val timestampMs = captureStartedAtMs + elapsedMs
            val bytes = frame.data.joinToString(" ") { "%02X".format(it) }
            fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
            writer.append("$timestampMs,$elapsedMs,${frame.id},${frame.data.size},${frame.dataHex},${csv(bytes)},${csv(frame.rawLine)}\\n")
            _captureCount.value += 1
            if (_captureCount.value % 128 == 0) writer.flush()
        }
    }

    fun saveCapture(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                synchronized(captureLock) {
                    val file = captureFile ?: error("No CAN capture to save")
                    require(_captureCount.value > 0) { "No CAN frames captured" }
                    captureWriter?.flush()
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("Unable to open destination")
                    output.use { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
                }
            }
            launch(Dispatchers.Main) {
                val message = if (result.isSuccess) {
                    "CAN capture saved (${_captureCount.value} frames)"
                } else {
                    "Could not save CAN capture: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startMonitoring() {
        if (_isMonitoring.value) return
        _isMonitoring.value = true
        beginCapture()
        val pauseLease = acquirePollingPause()
        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.canMonitorFlow().collect { frame ->
                    recordCaptureFrame(frame)
                    // Raw frame buffer (newest first, capped at MAX_FRAMES)
                    val cur = _frames.value
                    _frames.value = if (cur.size >= MAX_FRAMES) {
                        listOf(frame) + cur.take(MAX_FRAMES - 1)
                    } else {
                        listOf(frame) + cur
                    }

                    // Decode against DBC if loaded
                    if (signalMap.isNotEmpty()) {
                        val decoded = CanDecoder.decode(frame.id, frame.data, signalMap)
                        if (decoded.isNotEmpty()) {
                            val updated = _decodedMap.value.toMutableMap()
                            decoded.forEach { sig ->
                                updated["${sig.messageName}.${sig.signalName}"] = sig
                            }
                            _decodedMap.value = updated
                        }
                    }
                }
            } finally {
                finishMonitoring(pauseLease)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
        releaseActivePollingPause()
        synchronized(captureLock) {
            runCatching { captureWriter?.flush(); captureWriter?.close() }
            captureWriter = null
        }
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }
}
