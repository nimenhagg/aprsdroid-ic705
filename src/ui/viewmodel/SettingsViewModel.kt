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
import org.aprsdroid.app.update.UpdateCheckResult

/**
 * Dialog states for Settings screen.
 */
sealed interface SettingsDialog {
    data object Identity : SettingsDialog
    data object About : SettingsDialog
    data class Update(val update: UpdateCheckResult.UpdateAvailable) : SettingsDialog
    data object Ssid : SettingsDialog
    data object DigiPath : SettingsDialog
    data object Frequency : SettingsDialog
    data object Status : SettingsDialog
    data object Phg : SettingsDialog
    data object MapMode : SettingsDialog
    data object MapCustomUrl : SettingsDialog
    data object MapCustomSubdomains : SettingsDialog
    data object StationTap : SettingsDialog
}

/**
 * Immutable UI state for Settings screen.
 */
data class SettingsUiState(
    val callsign: String = "",
    val ssid: String = "10",
    val digiPath: String = "WIDE1-1",
    val userDigiPresets: Set<String> = emptySet(),
    val symbol: String = "/$",
    val frequency: String = "",
    val status: String = "",
    val backendName: String = "",
    val locationSourceName: String = "",
    val mapModeTag: String = "amap",
    val mapModeTitle: String = "",
    val mapCustomUrl: String = "",
    val mapCustomSubdomains: String = "",
    val showObjects: Boolean = true,
    val sendBatteryInfo: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val stationTapAction: String = "message",
    val phgEnabled: Boolean = false,
    val phgPower: String = "",
    val phgHeight: String = "",
    val phgHeightUnit: String = "m",
    val phgGain: String = "",
    val phgDir: String = "0",
    val activeDialog: SettingsDialog? = null,
    val mapModeOptions: List<Pair<String, String>> = emptyList(),
    val compactListMode: Boolean = false,
)

/**
 * User intents for Settings screen.
 */
sealed interface SettingsIntent {
    data object RefreshState : SettingsIntent
    data object DismissDialog : SettingsIntent
    data object OpenIdentityDialog : SettingsIntent
    data object OpenAboutDialog : SettingsIntent
    data object OpenSsidDialog : SettingsIntent
    data object OpenDigiPathDialog : SettingsIntent
    data object OpenFrequencyDialog : SettingsIntent
    data object OpenStatusDialog : SettingsIntent
    data object OpenPhgDialog : SettingsIntent
    data object OpenMapModeDialog : SettingsIntent
    data object OpenMapCustomUrlDialog : SettingsIntent
    data object OpenMapCustomSubdomainsDialog : SettingsIntent
    data object OpenStationTapDialog : SettingsIntent

    data class SaveCredentials(val callsign: String, val passcode: String) : SettingsIntent
    data class SaveSsid(val ssid: String) : SettingsIntent
    data class SaveDigiPath(val path: String) : SettingsIntent
    data class AddDigiPreset(val preset: String) : SettingsIntent
    data class DeleteDigiPreset(val preset: String) : SettingsIntent
    data class SaveFrequency(val freq: String) : SettingsIntent
    data class SaveStatus(val status: String) : SettingsIntent
    data class SavePhg(
        val enabled: Boolean,
        val power: String,
        val height: String,
        val unit: String,
        val gain: String,
        val dir: String,
    ) : SettingsIntent
    data class SaveMapMode(val tag: String, val title: String) : SettingsIntent
    data class SaveMapCustomUrl(val url: String) : SettingsIntent
    data class SaveMapCustomSubdomains(val subdomains: String) : SettingsIntent
    data class ToggleShowObjects(val enabled: Boolean) : SettingsIntent
    data class ToggleSendBatteryInfo(val enabled: Boolean) : SettingsIntent
    data class ToggleAutoCheckUpdates(val enabled: Boolean) : SettingsIntent
    data class ToggleCompactListMode(val enabled: Boolean) : SettingsIntent
    data class SaveStationTapAction(val action: String) : SettingsIntent
    data class SetUpdateAvailable(val update: UpdateCheckResult.UpdateAvailable?) : SettingsIntent

    data object NavigateBack : SettingsIntent
    data object OpenSymbolPicker : SettingsIntent
    data object OpenConnectionSetup : SettingsIntent
    data object OpenLocationSetup : SettingsIntent
    data object OpenNotificationPrefs : SettingsIntent
    data object ExportProfile : SettingsIntent
    data object ImportProfile : SettingsIntent
    data object ShareDiagnostics : SettingsIntent
    data object CheckForUpdates : SettingsIntent
    data object OpenCreditsAndLinks : SettingsIntent
}

/**
 * One-off side effects emitted from SettingsViewModel.
 */
sealed interface SettingsEffect {
    data class DigiPresetAdded(val preset: String) : SettingsEffect
    data class DigiPresetDeleted(val preset: String) : SettingsEffect
    data object TriggerManualUpdateCheck : SettingsEffect
}

class SettingsViewModel(
    private val store: AppPreferenceStore,
    private val defaultStatus: String = "APRSdroid Mod",
    private val defaultMapTitle: String = "APRSdroid Map",
) : ViewModel() {

    private val _uiState = MutableStateFlow(readCurrentState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private fun readCurrentState(): SettingsUiState {
        val presets = store.getStringSet("digi_path_user_presets", emptySet())
        return SettingsUiState(
            callsign = store.getString("callsign", "").trim().uppercase(Locale.US),
            ssid = store.getString("ssid", "10"),
            digiPath = store.getString("digi_path", "WIDE1-1"),
            userDigiPresets = presets,
            symbol = store.getString("symbol", "/$"),
            frequency = store.getString("frequency", ""),
            status = store.getString("status", defaultStatus),
            backendName = store.getString("backend_name", ""),
            locationSourceName = store.getString("loc_source", "smartbeaconing"),
            mapModeTag = store.getString("map_mode", "amap"),
            mapModeTitle = defaultMapTitle,
            mapCustomUrl = store.getString("map_custom_url", ""),
            mapCustomSubdomains = store.getString("map_custom_subdomains", ""),
            showObjects = store.getBoolean("show_objects", true),
            sendBatteryInfo = store.getBoolean("send_battery_aprsis", false),
            autoCheckUpdates = store.getBoolean("check_updates_automatically", true),
            stationTapAction = store.getString("station_tap_action", "message"),
            phgEnabled = store.getBoolean("phg_enabled", false),
            phgPower = store.getString("phg_power", ""),
            phgHeight = store.getString("phg_height", ""),
            phgHeightUnit = store.getString("phg_height_unit", "m"),
            phgGain = store.getString("phg_gain", ""),
            phgDir = store.getString("phg_dir", "0"),
            activeDialog = null,
            compactListMode = store.getBoolean("ui.compact_lists", false),
        )
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.RefreshState -> {
                _uiState.update {
                    readCurrentState().copy(
                        mapModeTitle = it.mapModeTitle.ifEmpty { defaultMapTitle },
                        mapModeOptions = it.mapModeOptions,
                    )
                }
            }
            is SettingsIntent.DismissDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            is SettingsIntent.OpenIdentityDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.Identity) }
            }
            is SettingsIntent.OpenAboutDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.About) }
            }
            is SettingsIntent.OpenSsidDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.Ssid) }
            }
            is SettingsIntent.OpenDigiPathDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.DigiPath) }
            }
            is SettingsIntent.OpenFrequencyDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.Frequency) }
            }
            is SettingsIntent.OpenStatusDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.Status) }
            }
            is SettingsIntent.OpenPhgDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.Phg) }
            }
            is SettingsIntent.OpenMapModeDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.MapMode) }
            }
            is SettingsIntent.OpenMapCustomUrlDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.MapCustomUrl) }
            }
            is SettingsIntent.OpenMapCustomSubdomainsDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.MapCustomSubdomains) }
            }
            is SettingsIntent.OpenStationTapDialog -> {
                _uiState.update { it.copy(activeDialog = SettingsDialog.StationTap) }
            }
            is SettingsIntent.SaveCredentials -> {
                val cleanCall = intent.callsign.trim().uppercase(Locale.US)
                val cleanPass = intent.passcode.trim()
                store.saveCredentials(cleanCall, cleanPass)
                _uiState.update {
                    it.copy(
                        callsign = cleanCall,
                        activeDialog = null,
                    )
                }
            }
            is SettingsIntent.SaveSsid -> {
                store.set("ssid", intent.ssid)
                _uiState.update { it.copy(ssid = intent.ssid, activeDialog = null) }
            }
            is SettingsIntent.SaveDigiPath -> {
                store.set("digi_path", intent.path)
                _uiState.update { it.copy(digiPath = intent.path, activeDialog = null) }
            }
            is SettingsIntent.AddDigiPreset -> {
                val current = HashSet(_uiState.value.userDigiPresets)
                current.add(intent.preset)
                store.setStringSet("digi_path_user_presets", current)
                _uiState.update { it.copy(userDigiPresets = current) }
                _effect.trySend(SettingsEffect.DigiPresetAdded(intent.preset))
            }
            is SettingsIntent.DeleteDigiPreset -> {
                val current = HashSet(_uiState.value.userDigiPresets)
                current.remove(intent.preset)
                store.setStringSet("digi_path_user_presets", current)
                _uiState.update { it.copy(userDigiPresets = current) }
                _effect.trySend(SettingsEffect.DigiPresetDeleted(intent.preset))
            }
            is SettingsIntent.SaveFrequency -> {
                val trimmed = intent.freq.trim()
                store.set("frequency", trimmed)
                _uiState.update { it.copy(frequency = trimmed, activeDialog = null) }
            }
            is SettingsIntent.SaveStatus -> {
                val trimmed = intent.status.take(42)
                store.set("status", trimmed)
                _uiState.update { it.copy(status = trimmed, activeDialog = null) }
            }
            is SettingsIntent.SavePhg -> {
                store.set("phg_enabled", intent.enabled)
                store.set("phg_power", intent.power)
                store.set("phg_height", intent.height)
                store.set("phg_height_unit", intent.unit)
                store.set("phg_gain", intent.gain)
                store.set("phg_dir", intent.dir)
                _uiState.update {
                    it.copy(
                        phgEnabled = intent.enabled,
                        phgPower = intent.power,
                        phgHeight = intent.height,
                        phgHeightUnit = intent.unit,
                        phgGain = intent.gain,
                        phgDir = intent.dir,
                        activeDialog = null,
                    )
                }
            }
            is SettingsIntent.SaveMapMode -> {
                store.set("map_mode", intent.tag)
                _uiState.update { it.copy(mapModeTag = intent.tag, mapModeTitle = intent.title, activeDialog = null) }
            }
            is SettingsIntent.SaveMapCustomUrl -> {
                val trimmed = intent.url.trim()
                store.set("map_custom_url", trimmed)
                _uiState.update { it.copy(mapCustomUrl = trimmed, activeDialog = null) }
            }
            is SettingsIntent.SaveMapCustomSubdomains -> {
                val trimmed = intent.subdomains.trim()
                store.set("map_custom_subdomains", trimmed)
                _uiState.update { it.copy(mapCustomSubdomains = trimmed, activeDialog = null) }
            }
            is SettingsIntent.ToggleShowObjects -> {
                store.set("show_objects", intent.enabled)
                _uiState.update { it.copy(showObjects = intent.enabled) }
            }
            is SettingsIntent.ToggleSendBatteryInfo -> {
                store.set("send_battery_aprsis", intent.enabled)
                _uiState.update { it.copy(sendBatteryInfo = intent.enabled) }
            }
            is SettingsIntent.ToggleAutoCheckUpdates -> {
                store.set("check_updates_automatically", intent.enabled)
                _uiState.update { it.copy(autoCheckUpdates = intent.enabled) }
            }
            is SettingsIntent.ToggleCompactListMode -> {
                store.set("ui.compact_lists", intent.enabled)
                _uiState.update { it.copy(compactListMode = intent.enabled) }
            }
            is SettingsIntent.SaveStationTapAction -> {
                store.set("station_tap_action", intent.action)
                _uiState.update { it.copy(stationTapAction = intent.action, activeDialog = null) }
            }
            is SettingsIntent.SetUpdateAvailable -> {
                _uiState.update {
                    it.copy(
                        activeDialog = if (intent.update != null) SettingsDialog.Update(intent.update) else null,
                    )
                }
            }
            is SettingsIntent.NavigateBack,
            is SettingsIntent.OpenSymbolPicker,
            is SettingsIntent.OpenConnectionSetup,
            is SettingsIntent.OpenLocationSetup,
            is SettingsIntent.OpenNotificationPrefs,
            is SettingsIntent.ExportProfile,
            is SettingsIntent.ImportProfile,
            is SettingsIntent.ShareDiagnostics,
            is SettingsIntent.CheckForUpdates,
            is SettingsIntent.OpenCreditsAndLinks -> {
                // UI-level navigation / action intents handled by host
            }
        }
    }
}
