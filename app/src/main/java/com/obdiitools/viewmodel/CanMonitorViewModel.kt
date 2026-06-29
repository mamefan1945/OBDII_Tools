package com.obdiitools.viewmodel

import android.content.Context
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
        _frames.value      = emptyList()
        _decodedMap.value  = emptyMap()
    }

    fun startMonitoring() {
        if (_isMonitoring.value) return
        _isMonitoring.value = true
        repository.pausePolling()
        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.canMonitorFlow().collect { frame ->
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
                _isMonitoring.value = false
                repository.resumePolling()
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
        repository.resumePolling()
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }
}
