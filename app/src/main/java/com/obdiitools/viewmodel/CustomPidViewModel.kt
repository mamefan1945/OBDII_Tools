package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.CustomPidDefinition
import com.obdiitools.data.CustomPidRepository
import com.obdiitools.data.FormulaType
import com.obdiitools.data.newCustomPidId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomPidViewModel @Inject constructor(
    private val customPidRepository: CustomPidRepository,
    repository: OBDRepository,
) : ViewModel() {

    val customPids: StateFlow<List<CustomPidDefinition>> = customPidRepository.customPids
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveValues: StateFlow<Map<String, Float?>> = repository.customPidValues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val showAddDialog = MutableStateFlow(false)

    fun addPid(name: String, command: String, unit: String, formula: FormulaType) {
        viewModelScope.launch {
            customPidRepository.addPid(
                CustomPidDefinition(
                    id      = newCustomPidId(),
                    name    = name,
                    command = command.uppercase().trim(),
                    unit    = unit,
                    formula = formula,
                )
            )
            showAddDialog.value = false
        }
    }

    fun removePid(id: String) {
        viewModelScope.launch { customPidRepository.removePid(id) }
    }
}
