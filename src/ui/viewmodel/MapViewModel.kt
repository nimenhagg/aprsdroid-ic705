package org.aprsdroid.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.aprsdroid.app.data.repository.MapStationRepository
import org.aprsdroid.app.data.repository.LatestQuery
import org.aprsdroid.app.map.MapStation

data class MapUiState(
    val stations: List<MapStation> = emptyList(),
    val isLoading: Boolean = false,
    val showObjects: Boolean = true
)

class MapViewModel(
    private val repository: MapStationRepository,
    initialShowObjects: Boolean
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState(showObjects = initialShowObjects))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var loaded = false
    private val refreshQueue = LatestQuery(
        scope = viewModelScope,
        initialRequest = initialShowObjects,
        query = { repository.getStations(it) },
        onResult = { stations, showObjects ->
            loaded = true
            _uiState.update { it.copy(stations = stations, isLoading = false, showObjects = showObjects) }
        },
        onFailure = { error ->
            Log.e("APRSdroid.MapViewModel", "Map station query failed", error)
            _uiState.update { it.copy(isLoading = false) }
        }
    )

    fun refresh(showObjects: Boolean = _uiState.value.showObjects) {
        _uiState.update {
            it.copy(isLoading = it.isLoading || !loaded || it.showObjects != showObjects, showObjects = showObjects)
        }
        refreshQueue.refresh(showObjects)
    }
}
