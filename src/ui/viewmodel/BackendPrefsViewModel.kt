package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.AprsBackend
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.aprsdroid.app.radio.RadioProfile
import org.aprsdroid.app.radio.WlanRadioModel

/**
 * Dialog states supported by the Connection / Backend Settings screen.
 */
sealed interface BackendDialog {
    data object Protocol : BackendDialog
    data object WlanModel : BackendDialog
    data object AprsIsLink : BackendDialog
    data object TncLink : BackendDialog
    data object BluetoothDevice : BackendDialog
    data object BaudRate : BackendDialog
    data object AfskOutput : BackendDialog
    data object Passcode : BackendDialog
    data object RadioModel : BackendDialog
    data object RadioBaudRate : BackendDialog
    data object RadioAudioDevice : BackendDialog
    data object RadioTxVolume : BackendDialog
    data class EditValue(val key: String, val initialValue: String) : BackendDialog
}

/**
 * Immutable UI state for BackendPrefs screen.
 */
data class BackendPrefsUiState(
    val proto: String = "aprsis",
    val aprsisLink: String = "tcp",
    val tncLink: String = "bluetooth",
    val tcpServer: String = "china.aprs2.net",
    val tcpFilter: String = "",
    val tcpFilterDist: String = "50",
    val tcpSoTimeout: String = "120",
    val udpServer: String = "srvr.aprs-is.net",
    val httpServer: String = "srvr.aprs-is.net",
    val ic705Model: String = "IC-705",
    val ic705CivAddress: String = "A4",
    val ic705Address: String = "192.168.59.1",
    val ic705Port: String = "50001",
    val ic705Username: String = "",
    val ic705Password: String = "",
    val ic705SyncFreq: Boolean = false,
    val ic705UseGps: Boolean = false,
    val afskBtSco: Boolean = false,
    val afskOutput: String = "0",
    val afskPrefix: String = "200",
    val btClient: Boolean = true,
    val btMac: String = "",
    val btChannel: String = "-1",
    val baudRate: String = "115200",
    val kissInit: String = "",
    val kissDelay: String = "300",
    val kenwoodGps: Boolean = false,
    val kenwoodGpsDebug: Boolean = false,
    val radioModelId: Int = RadioProfile.IC705_USB.hamlibModelId,
    val radioBaudRate: String = "19200",
    val radioCivAddress: String = "A4",
    val radioAudioDeviceOut: String = "-1",
    val radioTxVolume: String = "100",
    val activeDialog: BackendDialog? = null,
)

/**
 * User actions / intents in the MVI architecture.
 */
sealed interface BackendPrefsIntent {
    data object Refresh : BackendPrefsIntent
    data class OpenDialog(val dialog: BackendDialog) : BackendPrefsIntent
    data object DismissDialog : BackendPrefsIntent

    data class SelectProtocol(val proto: String) : BackendPrefsIntent
    data class SelectAprsIsLink(val link: String) : BackendPrefsIntent
    data class SelectTncLink(val link: String) : BackendPrefsIntent
    data class SelectWlanModel(val modelId: String) : BackendPrefsIntent
    data class SelectBluetoothDevice(val address: String) : BackendPrefsIntent
    data class SelectBaudRate(val baudRate: String) : BackendPrefsIntent
    data class SelectAfskOutput(val output: String) : BackendPrefsIntent
    data class SelectRadioModel(val profile: RadioProfile) : BackendPrefsIntent
    data class SelectRadioBaudRate(val baud: String) : BackendPrefsIntent
    data class SelectRadioAudioDevice(val deviceId: String) : BackendPrefsIntent
    data class SelectRadioTxVolume(val volume: String) : BackendPrefsIntent
    data class SavePasscode(val callsign: String, val passcode: String) : BackendPrefsIntent
    data class SaveEditValue(val key: String, val rawValue: String) : BackendPrefsIntent
    data class ToggleBoolean(val key: String, val value: Boolean) : BackendPrefsIntent
}

/**
 * One-time side effects dispatched to the Activity.
 */
sealed interface BackendPrefsEffect {
    data class RequestPermissions(val permissions: Set<String>) : BackendPrefsEffect
    data object OpenBluetoothSettings : BackendPrefsEffect
}

/**
 * MVI ViewModel managing connection backend settings and preference persistence.
 */
class BackendPrefsViewModel(
    private val prefs: AppPreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(readCurrentState())
    val uiState: StateFlow<BackendPrefsUiState> = _uiState.asStateFlow()

    private val _effect = Channel<BackendPrefsEffect>(Channel.BUFFERED)
    val effect: Flow<BackendPrefsEffect> = _effect.receiveAsFlow()

    private fun readCurrentState(): BackendPrefsUiState {
        val proto = prefs.getString("proto", "aprsis")
        val aprsisLink = prefs.getString("aprsis", "tcp")
        val tncLink = prefs.getString("link", "bluetooth")

        val tcpServer = prefs.getString("tcp.server", "china.aprs2.net")
        val tcpFilter = prefs.getString("tcp.filter", "")
        val tcpFilterDist = prefs.getString("tcp.filterdist", "50")
        val tcpSoTimeout = prefs.getString("tcp.sotimeout", "120")
        val udpServer = prefs.getString("udp.server", "srvr.aprs-is.net")
        val httpServer = prefs.getString("http.server", "srvr.aprs-is.net")

        val ic705Model = prefs.getString("ic705.model", "IC-705")
        val wlanModel = WlanRadioModel.findById(ic705Model)
        val savedCiv = prefs.getString("ic705.civ_address", "")
        val ic705Civ = savedCiv.ifBlank { wlanModel.defaultCivHex }
        val ic705Address = prefs.getString("ic705.address", "192.168.59.1")
        val ic705Port = prefs.getString("ic705.control_port", "50001")
        val ic705User = prefs.getString("ic705.username", "")
        val ic705Pass = prefs.getString("ic705.password", "")
        val ic705SyncFreq = prefs.getBoolean("ic705.sync_freq", false)
        val ic705UseGps = prefs.getBoolean("ic705.use_gps", false)

        val afskBtSco = prefs.getBoolean("afsk.btsco", false)
        val afskOutput = prefs.getString("afsk.output", "0")
        val afskPrefix = prefs.getString("afsk.prefix", "200")

        val btClient = prefs.getBoolean("bt.client", true)
        val btMac = prefs.getString("bt.mac", "")
        val btChannel = prefs.getString("bt.channel", "-1")
        val baudRate = prefs.getString("baudrate", "115200")

        val kissInit = prefs.getString("kiss.init", "")
        val kissDelay = prefs.getString("kiss.delay", "300")
        val kenwoodGps = prefs.getBoolean("kenwood.gps", false)
        val kenwoodGpsDebug = prefs.getBoolean("kenwood.gps_debug", false)

        val radioModelId = prefs.getStringInt("radio.model_id", RadioProfile.IC705_USB.hamlibModelId)
        val currentProfile = RadioProfile.findByModelId(radioModelId) ?: RadioProfile.IC705_USB
        val radioBaudRate = prefs.getString("radio.baudrate", currentProfile.defaultBaudRate.toString())
        val radioCiv = prefs.getString(
            "radio.civ_address",
            currentProfile.defaultCivAddress?.let { Integer.toHexString(it).uppercase() } ?: "",
        )
        val radioAudioDeviceOut = prefs.getString("radio.audio_device_out", "-1")
        val radioTxVolume = prefs.getString("radio.tx_volume", "100")

        return BackendPrefsUiState(
            proto = proto,
            aprsisLink = aprsisLink,
            tncLink = tncLink,
            tcpServer = tcpServer,
            tcpFilter = tcpFilter,
            tcpFilterDist = tcpFilterDist,
            tcpSoTimeout = tcpSoTimeout,
            udpServer = udpServer,
            httpServer = httpServer,
            ic705Model = ic705Model,
            ic705CivAddress = ic705Civ,
            ic705Address = ic705Address,
            ic705Port = ic705Port,
            ic705Username = ic705User,
            ic705Password = ic705Pass,
            ic705SyncFreq = ic705SyncFreq,
            ic705UseGps = ic705UseGps,
            afskBtSco = afskBtSco,
            afskOutput = afskOutput,
            afskPrefix = afskPrefix,
            btClient = btClient,
            btMac = btMac,
            btChannel = btChannel,
            baudRate = baudRate,
            kissInit = kissInit,
            kissDelay = kissDelay,
            kenwoodGps = kenwoodGps,
            kenwoodGpsDebug = kenwoodGpsDebug,
            radioModelId = radioModelId,
            radioBaudRate = radioBaudRate,
            radioCivAddress = radioCiv,
            radioAudioDeviceOut = radioAudioDeviceOut,
            radioTxVolume = radioTxVolume,
            activeDialog = null,
        )
    }

    fun dispatch(intent: BackendPrefsIntent) {
        when (intent) {
            BackendPrefsIntent.Refresh -> {
                _uiState.update { readCurrentState().copy(activeDialog = it.activeDialog) }
            }

            is BackendPrefsIntent.OpenDialog -> {
                _uiState.update { it.copy(activeDialog = intent.dialog) }
            }

            BackendPrefsIntent.DismissDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectProtocol -> {
                prefs.set("proto", intent.proto)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
                triggerPermissionCheck()
            }

            is BackendPrefsIntent.SelectAprsIsLink -> {
                prefs.set("aprsis", intent.link)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectTncLink -> {
                prefs.set("link", intent.link)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
                triggerPermissionCheck()
            }

            is BackendPrefsIntent.SelectWlanModel -> {
                val model = WlanRadioModel.findById(intent.modelId)
                prefs.set("ic705.model", model.id)
                prefs.set("ic705.civ_address", model.defaultCivHex)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectBluetoothDevice -> {
                prefs.set("bt.mac", intent.address)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectBaudRate -> {
                prefs.set("baudrate", intent.baudRate)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectAfskOutput -> {
                prefs.set("afsk.output", intent.output)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectRadioModel -> {
                val profile = intent.profile
                prefs.set("radio.model_id", profile.hamlibModelId.toString())
                prefs.set("radio.baudrate", profile.defaultBaudRate.toString())
                val civHex = profile.defaultCivAddress?.let { Integer.toHexString(it).uppercase() } ?: ""
                prefs.set("radio.civ_address", civHex)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectRadioBaudRate -> {
                prefs.set("radio.baudrate", intent.baud)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectRadioAudioDevice -> {
                prefs.set("radio.audio_device_out", intent.deviceId)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SelectRadioTxVolume -> {
                prefs.set("radio.tx_volume", intent.volume)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SavePasscode -> {
                prefs.saveCredentials(intent.callsign, intent.passcode)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.SaveEditValue -> {
                val clean = if (intent.key == "ic705.civ_address" || intent.key == "radio.civ_address") {
                    intent.rawValue.trim()
                        .removePrefix("0x").removePrefix("0X")
                        .removeSuffix("h").removeSuffix("H")
                        .trim().uppercase()
                } else {
                    intent.rawValue
                }
                prefs.set(intent.key, clean)
                _uiState.update { readCurrentState().copy(activeDialog = null) }
            }

            is BackendPrefsIntent.ToggleBoolean -> {
                prefs.set(intent.key, intent.value)
                _uiState.update { readCurrentState().copy(activeDialog = it.activeDialog) }
                if (intent.key == "kenwood.gps" && intent.value) {
                    triggerPermissionCheck()
                }
            }
        }
    }

    private fun triggerPermissionCheck() {
        (prefs as? PrefsWrapper)?.let { wrapper ->
            val permissions = AprsBackend.defaultBackendPermissions(wrapper)
            if (permissions.isNotEmpty()) {
                viewModelScope.launch {
                    _effect.send(BackendPrefsEffect.RequestPermissions(permissions))
                }
            }
        }
    }
}
