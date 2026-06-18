package com.obdiitools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.SessionDataPoint
import com.obdiitools.data.SessionEntity
import com.obdiitools.data.SessionRepository
import com.obdiitools.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionRepository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDataPoints = MutableStateFlow<List<SessionDataPoint>>(emptyList())
    val selectedDataPoints: StateFlow<List<SessionDataPoint>> = _selectedDataPoints

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private var loadJob: Job? = null

    fun loadSession(sessionId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _selectedDataPoints.value = sessionRepository.getDataPoints(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        if (_selectedDataPoints.value.firstOrNull()?.sessionId == sessionId) {
            loadJob?.cancel()
            _selectedDataPoints.value = emptyList()
        }
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
        }
    }
}
