package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.data.repository.LogRepository
import org.aprsdroid.app.model.LogPostItem

data class LogUiState(
    val items: List<LogPostItem> = emptyList(),
    val isRunning: Boolean = false
)

class LogViewModel(
    private val repository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    fun refresh(filter: String? = null) {
        viewModelScope.launch {
            val logs = repository.getLogs(filter)
            val running = AprsService.running
            _uiState.update {
                it.copy(
                    items = logs,
                    isRunning = running
                )
            }
        }
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = AprsService.running) }
    }
}
