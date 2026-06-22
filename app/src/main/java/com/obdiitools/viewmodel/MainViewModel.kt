package com.obdiitools.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.BluetoothDeviceScanner
import com.obdiitools.bluetooth.OBDForegroundService
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.AlertThresholds
import com.obdiitools.data.AlertType
import com.obdiitools.data.AirMassUnit
import com.obdiitools.data.FuelEconomyUnit
import com.obdiitools.data.CustomPidDefinition
import com.obdiitools.data.CustomPidRepository
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.TripSummary
import com.obdiitools.data.PressureUnit
import com.obdiitools.data.SpeedUnit
import com.obdiitools.data.TemperatureUnit
import com.obdiitools.data.TorqueUnit
import com.obdiitools.data.UserPreferences
import com.obdiitools.data.SessionRepository
import com.obdiitools.data.VinDiagnostic
import com.obdiitools.data.VinInfo
import com.obdiitools.data.VinRepository
import com.obdiitools.obd.BluetoothDeviceInfo
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.DTC
import com.obdiitools.obd.FreezeFrame
import com.obdiitools.obd.OBDData
import com.obdiitools.obd.ReadinessStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: OBDRepository,
    private val scanner: BluetoothDeviceScanner,
    private val prefsRepository: PreferencesRepository,
    private val vinRepository: VinRepository,
    private val sessionRepository: SessionRepository,
    private val customPidRepository: CustomPidRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val obdData: StateFlow<OBDData> = repository.obdData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OBDData())

    val dtcList: StateFlow<List<DTC>> = repository.dtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowVoltageWarning: StateFlow<Boolean> = repository.lowVoltageWarning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val userPreferences: StateFlow<UserPreferences> = prefsRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val readinessStatus: StateFlow<ReadinessStatus?> = repository.readinessStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val freezeFrames: StateFlow<List<FreezeFrame>> = repository.freezeFrames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAlerts: StateFlow<Set<AlertType>> = repository.activeAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val customPidValues: StateFlow<Map<String, Float?>> = repository.customPidValues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val lastTripSummary: StateFlow<TripSummary?> = repository.lastTripSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _vinInfo = MutableStateFlow<VinInfo?>(null)
    val vinInfo: StateFlow<VinInfo?> = _vinInfo

    private val _vinLoading = MutableStateFlow(false)
    val vinLoading: StateFlow<Boolean> = _vinLoading

    private val _vinDiagnostic = MutableStateFlow<VinDiagnostic?>(null)
    val vinDiagnostic: StateFlow<VinDiagnostic?> = _vinDiagnostic

    private val _vinDiagRunning = MutableStateFlow(false)
    val vinDiagRunning: StateFlow<Boolean> = _vinDiagRunning

    private var vinFetchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        vinFetchJob?.cancel()
                        vinFetchJob = viewModelScope.launch {
                            _vinLoading.value = true
                            try {
                                val info = vinRepository.fetchAndDecodeVin()
                                _vinInfo.value = info
                                if (info != null && (info.make.isNotBlank() || info.model.isNotBlank())) {
                                    sessionRepository.updateCurrentSessionMakeModel(info.make, info.model)
                                }
                            } finally {
                                _vinLoading.value = false
                            }
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        vinFetchJob?.cancel()
                        _vinInfo.value = null
                        _vinLoading.value = false
                        vinRepository.clearCache()
                    }
                    else -> Unit
                }
            }
        }
    }

    val customPids: StateFlow<List<CustomPidDefinition>> = customPidRepository.customPids
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isBluetoothEnabled: Boolean get() = scanner.isBluetoothEnabled

    fun getPairedDevices(): List<BluetoothDeviceInfo> = scanner.getPairedDevices()

    private val _bleDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val bleDevices: StateFlow<List<BluetoothDeviceInfo>> = _bleDevices

    private val _isBleScanRunning = MutableStateFlow(false)
    val isBleScanRunning: StateFlow<Boolean> = _isBleScanRunning

    private var bleScanJob: Job? = null

    fun startBleScan() {
        bleScanJob?.cancel()
        bleScanJob = viewModelScope.launch {
            _bleDevices.value = emptyList()
            _isBleScanRunning.value = true
            try {
                scanner.startBleScan().collect { device ->
                    _bleDevices.value = (_bleDevices.value + device).distinctBy { it.address }
                }
            } finally {
                _isBleScanRunning.value = false
            }
        }
    }

    fun stopBleScan() {
        bleScanJob?.cancel()
        bleScanJob = null
    }

    fun connect(device: BluetoothDeviceInfo) {
        viewModelScope.launch {
            startForegroundService()
            repository.connect(device)
        }
    }

    fun disconnect() {
        repository.disconnect()
    }

    suspend fun scanDTCs() = repository.scanDTCs()

    suspend fun clearDTCs() = repository.clearDTCs()

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch { prefsRepository.setSpeedUnit(unit) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { prefsRepository.setTemperatureUnit(unit) }
    }

    fun setPressureUnit(unit: PressureUnit) {
        viewModelScope.launch { prefsRepository.setPressureUnit(unit) }
    }

    fun setTorqueUnit(unit: TorqueUnit) {
        viewModelScope.launch { prefsRepository.setTorqueUnit(unit) }
    }

    fun setAirMassUnit(unit: AirMassUnit) {
        viewModelScope.launch { prefsRepository.setAirMassUnit(unit) }
    }

    fun setFuelEconomyUnit(unit: FuelEconomyUnit) {
        viewModelScope.launch { prefsRepository.setFuelEconomyUnit(unit) }
    }

    fun setAlertThresholds(thresholds: AlertThresholds) {
        viewModelScope.launch { prefsRepository.setAlertThresholds(thresholds) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setKeepScreenOn(enabled) }
    }

    fun fetchReadinessMonitors() {
        viewModelScope.launch { repository.fetchReadinessMonitors() }
    }

    fun fetchVin() {
        vinFetchJob?.cancel()
        vinFetchJob = viewModelScope.launch {
            vinRepository.clearCache()
            _vinLoading.value = true
            try {
                _vinInfo.value = vinRepository.fetchAndDecodeVin()
            } finally {
                _vinLoading.value = false
            }
        }
    }

    fun runVinDiagnostic() {
        viewModelScope.launch {
            _vinDiagnostic.value = null
            _vinDiagRunning.value = true
            _vinDiagnostic.value = vinRepository.runDiagnostic()
            _vinDiagRunning.value = false
        }
    }

    fun addCustomPid(definition: CustomPidDefinition) {
        viewModelScope.launch { customPidRepository.addPid(definition) }
    }

    fun removeCustomPid(id: String) {
        viewModelScope.launch { customPidRepository.removePid(id) }
    }

    fun dismissTripSummary() { repository.clearTripSummary() }

    private fun startForegroundService() {
        val intent = Intent(context, OBDForegroundService::class.java)
        context.startForegroundService(intent)
    }
}
