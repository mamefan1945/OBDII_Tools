package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.FreezeFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FreezeFrameViewModel @Inject constructor(
    repository: OBDRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val freezeFrames: StateFlow<List<FreezeFrame>> = repository.freezeFrames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())
}
