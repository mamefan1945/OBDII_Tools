package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.SessionRepository
import com.obdiitools.obd.AllPidDefinitions
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.MazdaCx5Protocol
import com.obdiitools.obd.MazdaCx5TransmissionData
import com.obdiitools.obd.UdsResponse
import com.obdiitools.obd.guessModuleName
import com.obdiitools.obd.parseUdsVin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    data class SupportedPid(val code: String, val name: String)

    data class EcuResult(
        val address: String,
        val addressHint: String?,
        val mode01Pids: List<SupportedPid>,
        val mode09Pids: List<SupportedPid>,
        val ecuVin: String? = null,
        val identityStatus: String? = null,
        val validatedMode01Pids: List<SupportedPid> = emptyList(),
    ) {
        val totalPids get() = mode01Pids.size + mode09Pids.size
        val validatedCount get() = validatedMode01Pids.size
    }

    data class MazdaDidResult(val did: String, val description: String, val status: String, val rawResponse: String)

    data class ScanState(
        val isScanning: Boolean = false,
        val phase: String = "",
        val progress: Pair<Int, Int>? = null,
        val results: List<EcuResult> = emptyList(),
        val isProbingMazda: Boolean = false,
        val mazdaProbeStatus: String? = null,
        val mazdaTransmission: MazdaCx5TransmissionData? = null,
        val isDiscoveringMazdaDids: Boolean = false,
        val mazdaDidProgress: Pair<Int, Int>? = null,
        val mazdaDidResults: List<MazdaDidResult> = emptyList(),
    )

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private var scanJob: Job? = null
    private val scanMutex = Mutex()

    private val mode01Names: Map<String, String> by lazy {
        AllPidDefinitions.ALL.associate { it.command.uppercase() to it.name }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanMutex.withLock {
                repository.pausePolling()
                _scanState.value = ScanState(isScanning = true, phase = "Discovering ECUs on CAN bus…")
                try {
                    delay(300)
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

                    // Bitmaps are only advertised capability. Validate every
                    // advertised Mode 01 PID with two decoded reads from this ECU.
                    val validatedMode01 = mutableListOf<SupportedPid>()
                    mode01.sorted().forEachIndexed pidLoop@ { pidIndex, pid ->
                        if (!isActive) return@pidLoop
                        _scanState.value = _scanState.value.copy(
                            phase = "Validating 0x$addr — $pid (" + (pidIndex + 1) + "/" + mode01.size + ")",
                        )
                        if (repository.validateMode01Pid(addr, pid)) {
                            validatedMode01 += SupportedPid(pid, mode01PidName(pid))
                        }
                    }
                    if (!isActive) return@forEachIndexed

                    // Mode 09 is queried on the broadcast address and conventional
                    // engine request address. A response proves support, not ECU identity.
                    val mode09 = if (addr == "7DF" || addr.uppercase() == "7E0") {
                        _scanState.value = _scanState.value.copy(
                            phase = "Mode 09 (vehicle info) — ECU 0x$addr",
                        )
                        repository.queryModeSupport(addr, "09")
                    } else emptySet()
                    if (!isActive) return@forEachIndexed

                    // Read standardized UDS VIN DID F190 from each responding ECU.
                    // Unsupported DIDs remain explicitly unavailable, never inferred.
                    val identity = if (addr != "7DF") {
                        _scanState.value = _scanState.value.copy(
                            phase = "UDS VIN (F190) — ECU 0x$addr",
                        )
                        val response = repository.queryUds(addr, "F190")
                        response to parseUdsVin(response)
                    } else null
                    if (!isActive) return@forEachIndexed

                    results.add(
                        EcuResult(
                            address = addr,
                            addressHint = if (addr == "7DF") "OBD2 Broadcast" else guessModuleName(addr),
                            mode01Pids = mode01.sorted().map { SupportedPid(it, mode01PidName(it)) },
                            mode09Pids = mode09.sorted().map { SupportedPid(it, mode09PidName(it)) },
                            ecuVin = identity?.second,
                            identityStatus = identity?.let { (response, vin) ->
                                when {
                                    vin != null -> "VIN read from UDS DID F190"
                                    response.errorCode == "11" || response.errorCode == "31" ->
                                        "F190 unsupported (NRC ${response.errorCode})"
                                    response.errorCode == "NC" || response.errorCode == "ND" ->
                                        "No F190 response"
                                    response.errorCode != null -> "F190 unavailable (${response.errorCode})"
                                    else -> "F190 response was not a valid 17-character VIN"
                                }
                            },
                            validatedMode01Pids = validatedMode01,
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
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _scanState.value = _scanState.value.copy(isScanning = false, phase = "Scan interrupted")
                } finally {
                    repository.resumePolling()
                }
            }
        }
    }

    /** Explicit, read-only Mode 22 probe for the 2016 CX-5 automatic TCM. */
    fun probeMazdaCx5Transmission() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            scanMutex.withLock {
                repository.pausePolling()
                _scanState.value = _scanState.value.copy(
                    isProbingMazda = true,
                    mazdaProbeStatus = "Probing Mazda TCM at 0x7E1…",
                    mazdaTransmission = null,
                )
                try {
                    val rawGear = repository.queryRawUds(MazdaCx5Protocol.TCM_REQUEST_ID, MazdaCx5Protocol.GEAR_DID)
                    val rawTemp = repository.queryRawUds(MazdaCx5Protocol.TCM_REQUEST_ID, MazdaCx5Protocol.TRANSMISSION_TEMPERATURE_DID)
                    val gear = UdsResponse.parse(MazdaCx5Protocol.TCM_REQUEST_ID, MazdaCx5Protocol.GEAR_DID, rawGear)
                    val temp = UdsResponse.parse(MazdaCx5Protocol.TCM_REQUEST_ID, MazdaCx5Protocol.TRANSMISSION_TEMPERATURE_DID, rawTemp)
                    val data = MazdaCx5Protocol.parseTransmissionData(gear, temp, rawGear, rawTemp)
                    val found = data.gear != null || data.transmissionTemperatureC != null
                    if (found) {
                        sessionRepository.recordMazdaTcmData(data.gear, data.transmissionTemperatureC)
                    }
                    _scanState.value = _scanState.value.copy(
                        isProbingMazda = false,
                        mazdaProbeStatus = if (found) "Read-only Mazda TCM response received" else
                            "No recognized Mazda TCM data; values remain unavailable",
                        mazdaTransmission = data,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _scanState.value = _scanState.value.copy(
                        isProbingMazda = false,
                        mazdaProbeStatus = "Mazda TCM probe failed: " + (error.message ?: "communication error"),
                        mazdaTransmission = null,
                    )
                } finally {
                    _scanState.value = _scanState.value.copy(isProbingMazda = false)
                    repository.resumePolling()
                }
            }
        }
    }

    /**
     * Bounded, read-only Mode 22 discovery against the confirmed 2016 CX-5 TCM.
     * Candidate identifiers come from the CX-5 parameter catalogue; positive
     * replies are reported as raw data only until their layouts are independently
     * validated on this vehicle. No writes, session changes, or actuator commands.
     */
    fun discoverMazdaTcmDids() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            scanMutex.withLock {
                repository.pausePolling()
                _scanState.value = _scanState.value.copy(
                    isDiscoveringMazdaDids = true,
                    mazdaDidProgress = 0 to MazdaCx5Protocol.TCM_DISCOVERY_CANDIDATES.size,
                    mazdaDidResults = emptyList(),
                )
                val results = mutableListOf<MazdaDidResult>()
                try {
                    MazdaCx5Protocol.TCM_DISCOVERY_CANDIDATES.forEachIndexed { index, candidate ->
                        val raw = repository.queryRawUds(MazdaCx5Protocol.TCM_REQUEST_ID, candidate.did)
                        val parsed = UdsResponse.parse(MazdaCx5Protocol.TCM_REQUEST_ID, candidate.did, raw)
                        val status = when {
                            parsed.isSuccess -> "POSITIVE RESPONSE — raw only"
                            parsed.errorCode != null && parsed.errorCode !in setOf("NC", "ND", "ERR", "UNK") ->
                                "NEGATIVE RESPONSE — NRC 0x${parsed.errorCode}"
                            parsed.errorCode == "ND" -> "NO DATA"
                            parsed.errorCode == "ERR" -> "ADAPTER ERROR"
                            parsed.errorCode == "UNK" -> "MALFORMED RESPONSE"
                            else -> "NO RESPONSE"
                        }
                        results += MazdaDidResult(candidate.did, candidate.description, status, raw.ifBlank { "(no response)" })
                        _scanState.value = _scanState.value.copy(
                            mazdaDidProgress = (index + 1) to MazdaCx5Protocol.TCM_DISCOVERY_CANDIDATES.size,
                            mazdaDidResults = results.toList(),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    results += MazdaDidResult("----", "Discovery error", error.message ?: "Communication error", "")
                    _scanState.value = _scanState.value.copy(mazdaDidResults = results.toList())
                } finally {
                    _scanState.value = _scanState.value.copy(
                        isDiscoveringMazdaDids = false,
                        mazdaDidProgress = null,
                    )
                    repository.resumePolling()
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = _scanState.value.copy(isScanning = false, isProbingMazda = false, isDiscoveringMazdaDids = false, progress = null, mazdaDidProgress = null, phase = "Cancelled")
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
