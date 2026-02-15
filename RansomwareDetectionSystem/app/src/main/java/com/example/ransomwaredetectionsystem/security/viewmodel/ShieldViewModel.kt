package com.example.ransomwaredetectionsystem.security.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ransomwaredetectionsystem.security.data.SecurityEventEntity
import com.example.ransomwaredetectionsystem.security.repository.ShieldRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShieldUiState(
    val enabled: Boolean = false,
    val recentEvents: List<SecurityEventEntity> = emptyList()
)

class ShieldViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ShieldRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        ShieldUiState(enabled = repository.isShieldEnabled())
    )
    val uiState: StateFlow<ShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentEvents().collect { events ->
                _uiState.update {
                    it.copy(
                        enabled = repository.isShieldEnabled(),
                        recentEvents = events
                    )
                }
            }
        }
    }

    fun enableShield() {
        repository.startShield()
        _uiState.update { it.copy(enabled = true) }
    }

    fun disableShield() {
        repository.stopShield()
        _uiState.update { it.copy(enabled = false) }
    }

    fun accessibilitySettingsIntent() = repository.accessibilitySettingsIntent()
    fun usageAccessSettingsIntent() = repository.usageAccessSettingsIntent()
}
