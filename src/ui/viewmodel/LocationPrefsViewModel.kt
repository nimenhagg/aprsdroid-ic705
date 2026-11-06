package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.aprsdroid.app.data.preferences.AppPreferenceStore

/**
 * Dialog states for Location preferences.
 */
sealed interface LocationDialog {
    data object LocationSource : LocationDialog
    data object Ambiguity : LocationDialog
    data class EditField(val key: String, val currentValue: String) : LocationDialog
}

/**
 * Immutable UI state for Location preferences screen.
 */
data class LocationPrefsUiState(
    val locSource: String = "smartbeaconing",
    val sbFastSpeed: String = "100",
    val sbFastRate: String = "60",
    val sbSlowSpeed: String = "5",
    val sbSlowRate: String = "1200",
    val sbTurnTime: String = "15",
    val sbTurnMin: String = "10",
    val sbTurnSlope: String = "240",
    val interval: String = "10",
    val manualLat: String = "0.000",
    val manualLon: String = "0.000",
    val periodicPosition: Boolean = true,
    val ambiguity: String = "0",
    val showSpdBear: Boolean = true,
    val showAltitude: Boolean = true,
    val activeDialog: LocationDialog? = null,
)

/**
 * User intents for Location preferences.
 */
sealed interface LocationPrefsIntent {
    data object DismissDialog : LocationPrefsIntent
    data object OpenLocationSourceDialog : LocationPrefsIntent
    data object OpenAmbiguityDialog : LocationPrefsIntent
    data class OpenEditDialog(val key: String) : LocationPrefsIntent
    data class SetLocationSource(val source: String) : LocationPrefsIntent
    data class SetAmbiguity(val ambiguity: String) : LocationPrefsIntent
    data class SaveFieldValue(val key: String, val value: String) : LocationPrefsIntent
    data class SetPeriodicPosition(val enabled: Boolean) : LocationPrefsIntent
    data class SetShowSpdBear(val enabled: Boolean) : LocationPrefsIntent
    data class SetShowAltitude(val enabled: Boolean) : LocationPrefsIntent
    data class UpdateManualCoordinates(val lat: Double, val lon: Double) : LocationPrefsIntent
}

/**
 * One-off side effects emitted to the host Activity.
 */
sealed interface LocationPrefsEffect {
    data object RequestLocationPermission : LocationPrefsEffect
    data class ShowToast(val messageRes: Int) : LocationPrefsEffect
}

class LocationPrefsViewModel(
    private val prefs: AppPreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(readCurrentState())
    val uiState: StateFlow<LocationPrefsUiState> = _uiState.asStateFlow()

    private val _effect = Channel<LocationPrefsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private fun readCurrentState(): LocationPrefsUiState {
        return LocationPrefsUiState(
            locSource = prefs.getString("loc_source", "smartbeaconing"),
            sbFastSpeed = prefs.getString("sb.fastspeed", "100"),
            sbFastRate = prefs.getString("sb.fastrate", "60"),
            sbSlowSpeed = prefs.getString("sb.slowspeed", "5"),
            sbSlowRate = prefs.getString("sb.slowrate", "1200"),
            sbTurnTime = prefs.getString("sb.turntime", "15"),
            sbTurnMin = prefs.getString("sb.turnmin", "10"),
            sbTurnSlope = prefs.getString("sb.turnslope", "240"),
            interval = prefs.getString("interval", "10"),
            manualLat = prefs.getString("manual_lat", "0.000"),
            manualLon = prefs.getString("manual_lon", "0.000"),
            periodicPosition = prefs.getBoolean("periodicposition", true),
            ambiguity = prefs.getString("priv_ambiguity", "0"),
            showSpdBear = prefs.getBoolean("priv_spdbear", true),
            showAltitude = prefs.getBoolean("priv_altitude", true),
            activeDialog = null,
        )
    }

    fun onIntent(intent: LocationPrefsIntent) {
        when (intent) {
            is LocationPrefsIntent.DismissDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            is LocationPrefsIntent.OpenLocationSourceDialog -> {
                _uiState.update { it.copy(activeDialog = LocationDialog.LocationSource) }
            }
            is LocationPrefsIntent.OpenAmbiguityDialog -> {
                _uiState.update { it.copy(activeDialog = LocationDialog.Ambiguity) }
            }
            is LocationPrefsIntent.OpenEditDialog -> {
                val currentValue = prefs.getString(intent.key, "")
                _uiState.update {
                    it.copy(activeDialog = LocationDialog.EditField(intent.key, currentValue))
                }
            }
            is LocationPrefsIntent.SetLocationSource -> {
                prefs.set("loc_source", intent.source)
                _uiState.update { it.copy(locSource = intent.source, activeDialog = null) }
                if (intent.source != "manual") {
                    _effect.trySend(LocationPrefsEffect.RequestLocationPermission)
                }
            }
            is LocationPrefsIntent.SetAmbiguity -> {
                prefs.set("priv_ambiguity", intent.ambiguity)
                _uiState.update { it.copy(ambiguity = intent.ambiguity, activeDialog = null) }
            }
            is LocationPrefsIntent.SaveFieldValue -> {
                prefs.set(intent.key, intent.value)
                _uiState.update {
                    when (intent.key) {
                        "sb.fastspeed" -> it.copy(sbFastSpeed = intent.value, activeDialog = null)
                        "sb.fastrate" -> it.copy(sbFastRate = intent.value, activeDialog = null)
                        "sb.slowspeed" -> it.copy(sbSlowSpeed = intent.value, activeDialog = null)
                        "sb.slowrate" -> it.copy(sbSlowRate = intent.value, activeDialog = null)
                        "sb.turntime" -> it.copy(sbTurnTime = intent.value, activeDialog = null)
                        "sb.turnmin" -> it.copy(sbTurnMin = intent.value, activeDialog = null)
                        "sb.turnslope" -> it.copy(sbTurnSlope = intent.value, activeDialog = null)
                        "interval" -> it.copy(interval = intent.value, activeDialog = null)
                        "manual_lat" -> it.copy(manualLat = intent.value, activeDialog = null)
                        "manual_lon" -> it.copy(manualLon = intent.value, activeDialog = null)
                        else -> it.copy(activeDialog = null)
                    }
                }
            }
            is LocationPrefsIntent.SetPeriodicPosition -> {
                prefs.set("periodicposition", intent.enabled)
                _uiState.update { it.copy(periodicPosition = intent.enabled) }
            }
            is LocationPrefsIntent.SetShowSpdBear -> {
                prefs.set("priv_spdbear", intent.enabled)
                _uiState.update { it.copy(showSpdBear = intent.enabled) }
            }
            is LocationPrefsIntent.SetShowAltitude -> {
                prefs.set("priv_altitude", intent.enabled)
                _uiState.update { it.copy(showAltitude = intent.enabled) }
            }
            is LocationPrefsIntent.UpdateManualCoordinates -> {
                val formattedLat = String.format(Locale.US, "%.5f", intent.lat)
                val formattedLon = String.format(Locale.US, "%.5f", intent.lon)
                prefs.set("manual_lat", formattedLat)
                prefs.set("manual_lon", formattedLon)
                _uiState.update { it.copy(manualLat = formattedLat, manualLon = formattedLon) }
            }
        }
    }
}
