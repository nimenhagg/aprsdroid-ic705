package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.diagnostic.LogReportManager
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
import org.aprsdroid.app.ui.screen.SettingsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.update.GitHubUpdateChecker
import org.aprsdroid.app.update.UpdateCheckResult
import org.json.JSONObject

class PrefsAct : ComponentActivity() {

    private val profileDocumentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            startActivity(
                Intent(this, ProfileImportActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private val profileExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException(getString(R.string.config_export_open_error))
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    val sp = PrefsWrapper.defaultSharedPreferences(this)
                    val json = JSONObject(sp.all)
                    writer.write(json.toString(2))
                    writer.newLine()
                }
                Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    private val callsignState = mutableStateOf("")
    private val ssidState = mutableStateOf("10")
    private val digiPathState = mutableStateOf("WIDE1-1")
    private val userDigiPresetsState = mutableStateOf<Set<String>>(emptySet())
    private val symbolState = mutableStateOf("/$")
    private val frequencyState = mutableStateOf("")
    private val statusState = mutableStateOf("")
    private val backendNameState = mutableStateOf("")
    private val locationSourceNameState = mutableStateOf("")
    private val mapModeTagState = mutableStateOf("amap")
    private val mapModeTitleState = mutableStateOf("")
    private val mapCustomUrlState = mutableStateOf("")
    private val mapCustomSubdomainsState = mutableStateOf("")
    private val showObjectsState = mutableStateOf(true)
    private val sendBatteryInfoState = mutableStateOf(false)
    private val stationTapActionState = mutableStateOf("message")
    private val identityDialogVisible = mutableStateOf(false)
    private val aboutDialogVisible = mutableStateOf(false)
    private val updateAvailableState = mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null)
    private var updateCheckInFlight = false

    fun exportPrefs() {
        val filename = String.format(
            "profile-%s.aprs",
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()),
        )
        profileExportPicker.launch(filename)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapModes.initialize(this)

        setContent {
            AprsTheme {
                val availableMapModes = MapModes.all_mapmodes
                    .filter { it.isAvailable(this) }
                    .map { mode -> mode.tag to (mode.title ?: mode.tag) }

                SettingsScreen(
                    callsign = callsignState.value,
                    ssid = ssidState.value,
                    digiPath = digiPathState.value,
                    userDigiPresets = userDigiPresetsState.value,
                    symbol = symbolState.value,
                    frequency = frequencyState.value,
                    status = statusState.value,
                    backendName = backendNameState.value,
                    locationSourceName = locationSourceNameState.value,
                    mapModeTag = mapModeTagState.value,
                    mapModeTitle = mapModeTitleState.value,
                    mapModeOptions = availableMapModes,
                    mapCustomUrl = mapCustomUrlState.value,
                    mapCustomSubdomains = mapCustomSubdomainsState.value,
                    showObjects = showObjectsState.value,
                    sendBatteryInfo = sendBatteryInfoState.value,
                    stationTapAction = stationTapActionState.value,
                    onBack = { finish() },
                    onOpenCallsignDialog = {
                        identityDialogVisible.value = true
                    },
                    onSaveSsid = { newSsid ->
                        prefs.set("ssid", newSsid)
                        refreshPrefsState()
                    },
                    onSaveDigiPath = { newPath ->
                        prefs.set("digi_path", newPath)
                        refreshPrefsState()
                    },
                    onAddDigiPreset = { preset ->
                        val current = HashSet(prefs.getDigiPathPresets())
                        current.add(preset)
                        prefs.saveDigiPathPresets(current)
                        userDigiPresetsState.value = current
                        Toast.makeText(
                            this,
                            getString(R.string.digi_path_preset_saved, preset),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onDeleteDigiPreset = { preset ->
                        val current = HashSet(prefs.getDigiPathPresets())
                        current.remove(preset)
                        prefs.saveDigiPathPresets(current)
                        userDigiPresetsState.value = current
                        Toast.makeText(
                            this,
                            getString(R.string.digi_path_preset_deleted, preset),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onOpenSymbolPicker = {
                        startActivity(Intent(this, PrefSymbolAct::class.java))
                    },
                    onSaveFrequency = { value ->
                        prefs.set("frequency", value.trim())
                        refreshPrefsState()
                    },
                    onSaveStatus = { value ->
                        prefs.set("status", value.take(42))
                        refreshPrefsState()
                    },
                    onOpenConnectionSetup = {
                        startActivity(Intent(this, BackendPrefs::class.java))
                    },
                    onOpenLocationSetup = {
                        startActivity(Intent(this, LocationPrefs::class.java))
                    },
                    onSaveMapMode = { tag ->
                        MapModes.setDefault(prefs, tag)
                        refreshPrefsState()
                    },
                    onSaveMapCustomUrl = { value ->
                        prefs.set("map_custom_url", value.trim())
                        refreshPrefsState()
                    },
                    onSaveMapCustomSubdomains = { value ->
                        prefs.set("map_custom_subdomains", value.trim())
                        refreshPrefsState()
                    },
                    onToggleShowObjects = { enabled ->
                        prefs.setBoolean("show_objects", enabled)
                        showObjectsState.value = enabled
                    },
                    onToggleSendBatteryInfo = { enabled ->
                        prefs.setBoolean("send_battery_aprsis", enabled)
                        sendBatteryInfoState.value = enabled
                    },
                    onSaveStationTapAction = { action ->
                        prefs.set("station_tap_action", action)
                        stationTapActionState.value = action
                    },
                    onOpenNotificationPrefs = {
                        startActivity(Intent(this, NotificationPrefs::class.java))
                    },
                    onExportProfile = { exportPrefs() },
                    onImportProfile = { profileDocumentPicker.launch(arrayOf("*/*")) },
                    onShareDiagnostics = {
                        LogReportManager.shareDiagnosticReport(this)
                    },
                    onCheckForUpdates = { checkForUpdatesManually() },
                    onOpenAbout = {
                        aboutDialogVisible.value = true
                    },
                )

                if (identityDialogVisible.value) {
                    PasscodeDialogCompose(
                        initialCallsign = prefs.getCallsign(),
                        initialPasscode = prefs.getString("passcode", ""),
                        firstRun = false,
                        onDismiss = {
                            identityDialogVisible.value = false
                            refreshPrefsState()
                        },
                        onSave = { call, pass ->
                            prefs.prefs.edit {
                                putString("callsign", call)
                                putString("passcode", pass)
                                putBoolean("firstrun", false)
                            }
                            identityDialogVisible.value = false
                            refreshPrefsState()
                        },
                    )
                }

                if (aboutDialogVisible.value) {
                    AboutDialogContent(
                        onDismiss = { aboutDialogVisible.value = false },
                        onOpenGithub = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/nimenhagg/aprsdroid-ic705".toUri(),
                                ),
                            )
                        },
                    )
                }

                updateAvailableState.value?.let { update ->
                    AlertDialog(
                        onDismissRequest = { updateAvailableState.value = null },
                        title = {
                            Text(stringResource(R.string.update_available_title, update.latest))
                        },
                        text = {
                            Text(stringResource(R.string.update_available_message, update.current))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    updateAvailableState.value = null
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri()),
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.update_open_release))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateAvailableState.value = null }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPrefsState()
    }

    private fun checkForUpdatesManually() {
        if (updateCheckInFlight) {
            Toast.makeText(this, R.string.update_check_in_progress, Toast.LENGTH_SHORT).show()
            return
        }
        updateCheckInFlight = true
        Toast.makeText(this, R.string.update_check_github, Toast.LENGTH_SHORT).show()
        AppLog.i("UPDATE", "manual_check_started", mapOf("current" to BuildConfig.VERSION_NAME))

        GitHubUpdateChecker.check(BuildConfig.VERSION_NAME) { result ->
            runOnUiThread {
                updateCheckInFlight = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        AppLog.i(
                            "UPDATE",
                            "manual_check_up_to_date",
                            mapOf("current" to result.current, "latest" to result.latest),
                        )
                        Toast.makeText(
                            this,
                            getString(R.string.update_up_to_date, result.current),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        AppLog.i(
                            "UPDATE",
                            "manual_check_update_available",
                            mapOf("current" to result.current, "latest" to result.latest),
                        )
                        updateAvailableState.value = result
                    }
                    is UpdateCheckResult.Failure -> {
                        AppLog.w(
                            "UPDATE",
                            "manual_check_failed",
                            mapOf("reason" to result.message),
                        )
                        Toast.makeText(
                            this,
                            getString(R.string.update_check_failed, result.message),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun refreshPrefsState() {
        callsignState.value = prefs.getCallsign()
        ssidState.value = prefs.getSsid()
        digiPathState.value = prefs.getString("digi_path", "WIDE1-1")
        userDigiPresetsState.value = prefs.getDigiPathPresets()
        symbolState.value = prefs.getString("symbol", "/$")
        frequencyState.value = prefs.getString("frequency", "")
        statusState.value = prefs.getString("status", getString(R.string.default_status))
        backendNameState.value = prefs.getBackendName()
        locationSourceNameState.value = prefs.getLocationSourceName()
        val mapMode = MapModes.defaultMapMode(this, prefs)
        mapModeTagState.value = mapMode.tag
        mapModeTitleState.value = mapMode.title ?: getString(R.string.app_map)
        mapCustomUrlState.value = prefs.getString("map_custom_url", "")
        mapCustomSubdomainsState.value = prefs.getString("map_custom_subdomains", "")
        showObjectsState.value = prefs.getShowObjects()
        sendBatteryInfoState.value = prefs.getSendBatteryAprsIs()
        stationTapActionState.value = prefs.getStationTapAction()
    }
}
