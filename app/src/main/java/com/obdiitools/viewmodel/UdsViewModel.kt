package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.EcuScanResult
import com.obdiitools.obd.KnownEcu
import com.obdiitools.obd.UdsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UdsViewModel @Inject constructor(
    private val repository: OBDRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private val _selectedEcu = MutableStateFlow(KnownEcu.ENGINE)
    val selectedEcu: StateFlow<KnownEcu> = _selectedEcu.asStateFlow()

    private val _customAddress = MutableStateFlow("")
    val customAddress: StateFlow<String> = _customAddress.asStateFlow()

    private val _headerInput = MutableStateFlow("")
    val headerInput: StateFlow<String> = _headerInput.asStateFlow()

    private val _prefixInput = MutableStateFlow("")
    val prefixInput: StateFlow<String> = _prefixInput.asStateFlow()

    private val _didInput = MutableStateFlow("")
    val didInput: StateFlow<String> = _didInput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _responses = MutableStateFlow<List<UdsResponse>>(emptyList())
    val responses: StateFlow<List<UdsResponse>> = _responses.asStateFlow()

    private val _scanResults = MutableStateFlow<List<EcuScanResult>>(emptyList())
    val scanResults: StateFlow<List<EcuScanResult>> = _scanResults.asStateFlow()

    // Pair(done, total); null = not scanning
    private val _scanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val scanProgress: StateFlow<Pair<Int, Int>?> = _scanProgress.asStateFlow()

    fun selectEcu(ecu: KnownEcu) { _selectedEcu.value = ecu }
    fun setCustomAddress(addr: String) { _customAddress.value = addr.uppercase().take(3) }
    fun setHeader(header: String) { _headerInput.value = header.uppercase().take(8) }
    fun setPrefix(prefix: String) { _prefixInput.value = prefix.uppercase().take(8) }
    fun setDid(did: String) { _didInput.value = did.uppercase().take(4) }
    fun clearHistory() { _responses.value = emptyList() }

    fun startEcuScan() {
        if (_scanProgress.value != null) return
        viewModelScope.launch {
            _scanResults.value = emptyList()
            repository.pausePolling()
            try {
                repository.scanEcus(
                    onResult = { result -> _scanResults.value = _scanResults.value + result },
                    onProgress = { done, total -> _scanProgress.value = done to total },
                )
            } finally {
                _scanProgress.value = null
                repository.resumePolling()
            }
        }
    }

    fun selectScanResult(result: EcuScanResult) {
        _selectedEcu.value = KnownEcu.CUSTOM
        _customAddress.value = result.address
    }

    fun sendRequest() {
        val ecu = _selectedEcu.value
        val address = if (ecu == KnownEcu.CUSTOM) _customAddress.value else ecu.address
        val did = _didInput.value
        val header = _headerInput.value.trim()
        val prefix = _prefixInput.value.trim()
        if (address.isBlank() || did.length < 4) return

        viewModelScope.launch {
            _isLoading.value = true
            repository.pausePolling()
            try {
                val response = if (header.isBlank() && prefix.isBlank()) {
                    // No overrides — use the standard ATSH + UDS request path.
                    repository.queryUds(address, did)
                } else {
                    // Custom header/prefix: build and send the raw command ourselves.
                    if (header.isNotBlank()) {
                        repository.queryRaw("AT SH $header")
                    }
                    val command = if (prefix.isNotBlank()) "$prefix$did" else did
                    val raw = repository.queryRaw(command)
                    UdsResponse.parse(address, did, raw)
                }
                _responses.value = listOf(response) + _responses.value
            } finally {
                repository.resumePolling()
                _isLoading.value = false
            }
        }
    }
}