package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.data.repository.StationDetailsSource
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.service.ServiceEventBus

/**
 * Immutable UI state for Station details screen.
 */
data class StationUiState(
    val targetCall: String = "",
    val stationItem: StationItem? = null,
    val ssidList: List<StationItem> = emptyList(),
    val postList: List<LogPostItem> = emptyList(),
    val myLat: Int = 0,
    val myLon: Int = 0,
)

/**
 * ViewModel managing station detail data, SSID alternates, and post history.
 * Employs conflated Channel batching to prevent UI jank during high-throughput packet bursts.
 */
class StationViewModel(
    private val repository: StationDetailsSource,
    private val eventBus: ServiceEventBus = ServiceEventBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationUiState())
    val uiState: StateFlow<StationUiState> = _uiState.asStateFlow()

    private val refreshChannel = Channel<Pair<String, String>>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (query in refreshChannel) {
                // 250ms batching window: fold bursts into a single query
                delay(250L)
                var latest = query
                while (true) {
                    val next = refreshChannel.tryReceive().getOrNull() ?: break
                    latest = next
                }
                executeQuery(latest.first, latest.second)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            eventBus.packetEvents.collect { event ->
                val current = _uiState.value
                val target = current.targetCall
                if (target.isNotEmpty()) {
                    val baseTarget = target.split("-")[0]
                    if (event.callsign.isEmpty() ||
                        event.callsign.equals(target, ignoreCase = true) ||
                        event.callsign.startsWith(baseTarget, ignoreCase = true)
                    ) {
                        requestRefresh(target, "")
                    }
                }
            }
        }
    }

    fun requestRefresh(targetCall: String, myCall: String) {
        if (targetCall.isBlank()) return
        refreshChannel.trySend(targetCall to myCall)
    }

    private suspend fun executeQuery(targetCall: String, myCall: String) {
        val details = repository.getStationDetails(targetCall, myCall)
        _uiState.update {
            it.copy(
                targetCall = targetCall,
                stationItem = details.stationItem,
                ssidList = details.ssidList,
                postList = details.postList,
                myLat = details.myLat,
                myLon = details.myLon,
            )
        }
    }
}
