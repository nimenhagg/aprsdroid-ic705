package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.repository.StationRepository
import org.aprsdroid.app.model.StationItem

data class HubUiState(
    val stations: List<StationItem> = emptyList(),
    val myLat: Int = 0,
    val myLon: Int = 0,
    val isRunning: Boolean = false,
    val myCall: String = ""
)

class HubViewModel(
    private val repository: StationRepository,
    private val prefs: PrefsWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val myCall = prefs.getCallSsid()
            val running = AprsService.running
            val data = repository.getHubData(myCall, prefs.getShowAge())
            _uiState.update {
                it.copy(
                    stations = data.stations,
                    myLat = data.myLat,
                    myLon = data.myLon,
                    isRunning = running,
                    myCall = myCall
                )
            }
        }
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = AprsService.running) }
    }
}
