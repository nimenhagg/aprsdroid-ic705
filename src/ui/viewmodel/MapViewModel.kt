package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.Station
import org.aprsdroid.app.data.repository.MapStationRepository

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val isLoading: Boolean = false,
    val showObjects: Boolean = true
)

class MapViewModel(
    private val repository: MapStationRepository,
    initialShowObjects: Boolean
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState(showObjects = initialShowObjects))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun refresh(showObjects: Boolean = _uiState.value.showObjects) {
        _uiState.update { it.copy(isLoading = true, showObjects = showObjects) }
        viewModelScope.launch {
            val stations = repository.getStations(showObjects)
            _uiState.update { current ->
                current.copy(stations = stations, isLoading = false, showObjects = showObjects)
            }
        }
    }
}
