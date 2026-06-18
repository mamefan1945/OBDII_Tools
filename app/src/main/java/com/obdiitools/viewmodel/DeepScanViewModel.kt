package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.obd.AllPidDefinitions
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.guessModuleName
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
class DeepScanViewModel @Inject constructor(
    private val repository: OBDRepository,
) : ViewModel() {

    data class SupportedPid(val code: String, val name: String)

    data class EcuResult(
        val address: String,
        val moduleName: String?,
        val mode01Pids: List<SupportedPid>,
        val mode09Pids: List<SupportedPid>,
    ) {
        val totalPids get() = mode01Pids.size + mode09Pids.size
    }

    data class ScanState(
        val isScanning: Boolean = false,
        val phase: String = "",
        val progress: Pair<Int, Int>? = null,
        val results: List<EcuResult> = emptyList(),
    )

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private var scanJob: Job? = null

    private val mode01Names: Map<String, String> by lazy {
        AllPidDefinitions.ALL.associate { it.command.uppercase() to it.name }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            repository.pausePolling()
            _scanState.value = ScanState(isScanning = true, phase = "Discovering ECUs on CAN bus…")
            delay(300)
            try {
                // ── Phase 1: ECU discovery ────────────────────────────────────
                val foundAddresses = mutableListOf<String>()
                repository.scanEcus(
                    onResult = { result ->
                        // Include ECUs that replied with either a positive (5001) or negative (7F...)
                        // response — both indicate an active module on the bus.
                        foundAddresses.add(result.address)
                    },
                    onProgress = { done, total ->
                        _scanState.value = _scanState.value.copy(
                            phase = "Discovering ECUs… $done / $total addresses",
                            progress = done to total,
                        )
                    },
                )

                if (!isActive) return@launch

                // Always probe broadcast address; add specific ECUs that responded
                val addresses = (listOf("7DF") + foundAddresses).distinct()

                // ── Phase 2: PID bitmask per address ─────────────────────────
                val results = mutableListOf<EcuResult>()
                addresses.forEachIndexed { idx, addr ->
                    if (!isActive) return@forEachIndexed
                    _scanState.value = _scanState.value.copy(
                        phase = "Mode 01 PIDs — ECU 0x$addr",
                        progress = (idx + 1) to addresses.size,
                    )
                    val mode01 = repository.queryModeSupport(addr, "01")
                    if (!isActive) return@forEachIndexed

                    // Mode 09 only makes sense on broadcast or the engine ECU
                    val mode09 = if (addr == "7DF" || addr.uppercase() == "7E0") {
                        _scanState.value = _scanState.value.copy(
                            phase = "Mode 09 (vehicle info) — ECU 0x$addr",
                        )
                        repository.queryModeSupport(addr, "09")
                    } else emptySet()

                    results.add(
                        EcuResult(
                            address = addr,
                            moduleName = if (addr == "7DF") "OBD2 Broadcast" else guessModuleName(addr),
                            mode01Pids = mode01.sorted().map { SupportedPid(it, mode01PidName(it)) },
                            mode09Pids = mode09.sorted().map { SupportedPid(it, mode09PidName(it)) },
                        )
                    )
                    // Emit partial results as we go so the UI updates live
                    _scanState.value = _scanState.value.copy(results = results.toList())
                }

                val total = results.sumOf { it.totalPids }
                _scanState.value = _scanState.value.copy(
                    isScanning = false,
                    phase = "Complete — $total PIDs across ${results.size} address${if (results.size != 1) "es" else ""}",
                    progress = null,
                )
            } catch (_: Exception) {
                _scanState.value = _scanState.value.copy(isScanning = false, phase = "Scan interrupted")
            } finally {
                repository.resumePolling()
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        repository.resumePolling()
        _scanState.value = _scanState.value.copy(isScanning = false, progress = null, phase = "Cancelled")
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    private fun mode01PidName(code: String) = mode01Names[code.uppercase()] ?: "PID $code"

    private fun mode09PidName(code: String) = MODE09_NAMES[code.uppercase()] ?: "Info ${code.takeLast(2)}"

    companion object {
        private val MODE09_NAMES = mapOf(
            "0901" to "VIN Message Count",
            "0902" to "Vehicle Identification Number (VIN)",
            "0903" to "Calibration ID Count",
            "0904" to "Calibration ID",
            "0905" to "CVN Count",
            "0906" to "Calibration Verification Number (CVN)",
            "0907" to "Performance Tracking Count",
            "0908" to "In-Use Performance Tracking",
            "0909" to "ECU Name Count",
            "090A" to "ECU Name",
            "090B" to "Performance Tracking (compression ignition)",
        )
    }
}
