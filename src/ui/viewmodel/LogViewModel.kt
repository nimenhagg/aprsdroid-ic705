package org.aprsdroid.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.data.repository.LatestQuery
import org.aprsdroid.app.data.repository.LogSource
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.service.ServiceEventBus

data class LogUiState(
    val items: List<LogPostItem> = emptyList(),
    val isRunning: Boolean = false
)

class LogViewModel(
    private val repository: LogSource,
    private val bus: ServiceEventBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bus.isServiceRunning.collect { running ->
                _uiState.update { it.copy(isRunning = running) }
            }
        }
        viewModelScope.launch {
            bus.packetEvents.collect {
                refresh()
            }
        }
    }

    private val refreshQueue = LatestQuery<String?, List<LogPostItem>>(
        scope = viewModelScope,
        initialRequest = null,
        query = { filter -> repository.getLogs(filter) },
        onResult = { logs, _ ->
            _uiState.update {
                it.copy(
                    items = logs,
                    isRunning = bus.isServiceRunning.value
                )
            }
        },
        onFailure = { Log.e("APRSdroid.LogViewModel", "Log query failed", it) }
    )

    fun refresh(filter: String? = null) {
        refreshQueue.refresh(filter)
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = bus.isServiceRunning.value) }
    }
}
