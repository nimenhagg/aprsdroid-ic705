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
    val myCall: String = "",
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

class HubViewModel(
    private val repository: StationRepository,
    private val prefs: PrefsWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    private data class Request(val includeStations: Boolean, val searchQuery: String)

    private val refreshQueue = LatestQuery(
        scope = viewModelScope,
        initialRequest = Request(true, ""),
        query = { request ->
            val myCall = prefs.getCallSsid()
            val data = repository.getHubData(myCall, prefs.getShowAge(), includeStations = request.includeStations, searchQuery = request.searchQuery)
            myCall to data
        },
        onResult = { (myCall, data), request ->
            _uiState.update {
                it.copy(
                    stations = if (request.includeStations) data.stations else it.stations,
                    isSearching = if (request.includeStations) false else it.isSearching,
                    myLat = data.myLat,
                    myLon = data.myLon,
                    isRunning = AprsService.running,
                    myCall = myCall
                )
            }
        },
        onFailure = { error ->
            Log.e("APRSdroid.HubViewModel", "Station query failed", error)
            _uiState.update { it.copy(isSearching = false) }
        }
    )

    fun refresh(includeStations: Boolean = true) {
        refreshQueue.refresh(Request(includeStations, _uiState.value.searchQuery.trim()))
    }

    fun setSearchQuery(query: String) {
        if (query == _uiState.value.searchQuery) return
        val changed = query.trim() != _uiState.value.searchQuery.trim()
        _uiState.update {
            it.copy(searchQuery = query, stations = if (changed) emptyList() else it.stations,
                isSearching = changed || it.isSearching)
        }
        if (changed) refresh()
    }

    fun updateServiceState() {
        _uiState.update { it.copy(isRunning = AprsService.running) }
    }
}
