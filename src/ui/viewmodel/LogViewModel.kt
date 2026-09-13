package org.aprsdroid.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.aprsdroid.app.data.repository.LatestQuery
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

    private val refreshQueue = LatestQuery<String?, List<LogPostItem>>(
        scope = viewModelScope,
        initialRequest = null,
        query = { filter -> repository.getLogs(filter) },
        onResult = { logs, _ ->
            _uiState.update {
                it.copy(
                    items = logs,
                    isRunning = AprsService.running
                )
            }
        },
        onFailure = { Log.e("APRSdroid.LogViewModel", "Log query failed", it) }
    )

    fun refresh(filter: String? = null) {
        refreshQueue.refresh(filter)
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = AprsService.running) }
    }
}
