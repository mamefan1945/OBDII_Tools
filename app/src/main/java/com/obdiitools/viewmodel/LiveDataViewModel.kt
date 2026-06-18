package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.AllPidDefinitions
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.LivePidValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val repository: OBDRepository,
    private val prefsRepository: PreferencesRepository,
) : ViewModel() {

    val totalPids = AllPidDefinitions.ALL.size

    private val _pidValues = MutableStateFlow(
        AllPidDefinitions.ALL.map { LivePidValue(it) }
    )
    val pidValues: StateFlow<List<LivePidValue>> = _pidValues.asStateFlow()

    private val _scanIndex = MutableStateFlow(-1)
    val scanIndex: StateFlow<Int> = _scanIndex.asStateFlow()

    private val _discoveryComplete = MutableStateFlow(false)
    val discoveryComplete: StateFlow<Boolean> = _discoveryComplete.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val userPreferences: StateFlow<UserPreferences> = prefsRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private var scanJob: Job? = null

    fun startLiveScan() {
        repository.pausePolling()
        _discoveryComplete.value = false
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(400) // let the dashboard polling loop finish any in-flight query

            // ── Phase 1: Discovery ────────────────────────────────────────────
            // Query every PID once to learn which are supported. Progress bar is
            // visible during this phase only.
            val snapshot = _pidValues.value.toMutableList()
            AllPidDefinitions.ALL.forEachIndexed { index, pidDef ->
                if (!isActive || !repository.isConnected) return@forEachIndexed
                _scanIndex.value = index
                val raw = repository.queryRaw(pidDef.command)
                val noData = isNoData(raw)
                snapshot[index] = snapshot[index].copy(
                    value    = if (noData) null else pidDef.parse(raw),
                    supported = if (raw.isBlank()) snapshot[index].supported else !noData,
                )
            }
            _pidValues.value = snapshot.toList()
            _scanIndex.value = -1  // hides progress bar
            _discoveryComplete.value = true

            // ── Phase 2: Continuous update ────────────────────────────────────
            // Only re-query PIDs that are supported. Values update in real-time
            // without the scan restarting from scratch.
            while (isActive && repository.isConnected) {
                val current = _pidValues.value.toMutableList()
                current.forEachIndexed { index, pidValue ->
                    if (!isActive || !repository.isConnected) return@forEachIndexed
                    if (pidValue.supported != true) return@forEachIndexed
                    val raw = repository.queryRaw(pidValue.definition.command)
                    current[index] = current[index].copy(
                        value = if (isNoData(raw)) null else pidValue.definition.parse(raw)
                    )
                }
                _pidValues.value = current.toList()
            }
        }
    }

    private fun isNoData(raw: String) = raw.isBlank()
        || raw.contains("NODATA",  ignoreCase = true)
        || raw.contains("NO DATA", ignoreCase = true)
        || raw.contains("ERROR",   ignoreCase = true)
        || raw.contains("?")

    fun stopLiveScan() {
        scanJob?.cancel()
        scanJob = null
        _scanIndex.value = -1
        repository.resumePolling()
    }

    override fun onCleared() {
        stopLiveScan()
        super.onCleared()
    }
}
