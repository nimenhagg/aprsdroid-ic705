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

    private val refreshQueue = LatestQuery(
        scope = viewModelScope,
        initialRequest = true,
        query = { includeStations ->
            val myCall = prefs.getCallSsid()
            val data = repository.getHubData(myCall, prefs.getShowAge(), includeStations = includeStations)
            myCall to data
        },
        onResult = { (myCall, data), includeStations ->
            _uiState.update {
                it.copy(
                    stations = if (includeStations) data.stations else it.stations,
                    myLat = data.myLat,
                    myLon = data.myLon,
                    isRunning = AprsService.running,
                    myCall = myCall
                )
            }
        },
        onFailure = { Log.e("APRSdroid.HubViewModel", "Station query failed", it) }
    )

    fun refresh(includeStations: Boolean = true) {
        refreshQueue.refresh(includeStations)
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = AprsService.running) }
    }
}
