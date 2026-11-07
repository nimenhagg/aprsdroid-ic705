package org.aprsdroid.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.AprsPacket
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.ServiceNotifier
import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.aprsdroid.app.data.repository.HubStationSource
import org.aprsdroid.app.data.repository.LatestQuery
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.service.ServiceEventBus
import java.util.Locale

data class HubUiState(
    val stations: List<StationItem> = emptyList(),
    val myLat: Int = 0,
    val myLon: Int = 0,
    val isRunning: Boolean = false,
    val myCall: String = "",
    val serviceStatus: String = "",
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

class HubViewModel(
    private val repository: HubStationSource,
    private val prefs: AppPreferenceStore,
    private val eventBus: ServiceEventBus = ServiceEventBus,
    private val statusProvider: (() -> String)? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            eventBus.isServiceRunning.collect { running ->
                _uiState.update {
                    it.copy(
                        isRunning = running,
                        serviceStatus = currentServiceStatus(running)
                    )
                }
            }
        }
        viewModelScope.launch {
            eventBus.liveStatus.collect {
                _uiState.update {
                    it.copy(
                        serviceStatus = currentServiceStatus(_uiState.value.isRunning)
                    )
                }
            }
        }
        viewModelScope.launch {
            eventBus.packetEvents.collect {
                refresh()
            }
        }
    }

    private fun callSsid(): String {
        return (prefs as? PrefsWrapper)?.getCallSsid() ?: run {
            val call = prefs.getString("callsign", "").trim().uppercase(Locale.US)
            val ssid = prefs.getString("ssid", "10")
            AprsPacket.formatCallSsid(call, ssid)
        }
    }

    private fun showAgeMs(): Long {
        return (prefs as? PrefsWrapper)?.getShowAge()
            ?: (prefs.getStringInt("show_age", 30) * 60L * 1000L)
    }

    private fun currentServiceStatus(running: Boolean): String {
        if (!running) return ""
        if (statusProvider != null) return statusProvider.invoke()
        val context = (prefs as? PrefsWrapper)?.context ?: return ""
        return ServiceNotifier.instance.currentLiveStatus()?.detailText(context).orEmpty()
    }

    private data class Request(val includeStations: Boolean, val searchQuery: String)

    private val refreshQueue = LatestQuery(
        scope = viewModelScope,
        initialRequest = Request(true, ""),
        query = { request ->
            val myCall = callSsid()
            val data = repository.getHubData(myCall, showAgeMs(), includeStations = request.includeStations, searchQuery = request.searchQuery)
            myCall to data
        },
        onResult = { (myCall, data), request ->
            _uiState.update {
                it.copy(
                    stations = if (request.includeStations) data.stations else it.stations,
                    isSearching = if (request.includeStations) false else it.isSearching,
                    myLat = data.myLat,
                    myLon = data.myLon,
                    isRunning = eventBus.isServiceRunning.value,
                    myCall = myCall,
                    serviceStatus = currentServiceStatus(eventBus.isServiceRunning.value)
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
        val running = eventBus.isServiceRunning.value
        _uiState.update {
            it.copy(
                isRunning = running,
                serviceStatus = currentServiceStatus(running)
            )
        }
    }
}
