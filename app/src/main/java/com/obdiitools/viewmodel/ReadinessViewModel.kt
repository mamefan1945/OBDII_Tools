package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.ReadinessStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadinessViewModel @Inject constructor(
    private val repository: OBDRepository,
) : ViewModel() {

    val readinessStatus: StateFlow<ReadinessStatus?> = repository.readinessStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isConnected: StateFlow<Boolean> = repository.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch { repository.fetchReadinessMonitors() }
    }

    fun refresh() {
        viewModelScope.launch { repository.fetchReadinessMonitors() }
    }
}
