package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.preference.PreferenceManager
import org.aprsdroid.app.diagnostic.LogReportManager
import org.aprsdroid.app.ui.screen.SettingsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    val sp = PreferenceManager.getDefaultSharedPreferences(this)
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
    private val ssidState = mutableStateOf("5")
    private val digiPathState = mutableStateOf("WIDE1-1")
    private val userDigiPresetsState = mutableStateOf<Set<String>>(emptySet())
    private val symbolState = mutableStateOf("/$")
    private val backendNameState = mutableStateOf("")
    private val locationSourceNameState = mutableStateOf("")
    private val mapModeTitleState = mutableStateOf("")
    private val showObjectsState = mutableStateOf(true)

    fun exportPrefs() {
        val filename = String.format("profile-%s.aprs", SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()))
        profileExportPicker.launch(filename)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                SettingsScreen(
                    callsign = callsignState.value,
                    ssid = ssidState.value,
                    digiPath = digiPathState.value,
                    userDigiPresets = userDigiPresetsState.value,
                    symbol = symbolState.value,
                    backendName = backendNameState.value,
                    locationSourceName = locationSourceNameState.value,
                    mapModeTitle = mapModeTitleState.value,
                    showObjects = showObjectsState.value,
                    onBack = { finish() },
                    onOpenCallsignDialog = {
                        PasscodeDialog(this, false).apply {
                            setOnDismissListener { refreshPrefsState() }
                        }.show()
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
                        Toast.makeText(this, "已保存预设: $preset", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteDigiPreset = { preset ->
                        val current = HashSet(prefs.getDigiPathPresets())
                        current.remove(preset)
                        prefs.saveDigiPathPresets(current)
                        userDigiPresetsState.value = current
                        Toast.makeText(this, "已删除预设: $preset", Toast.LENGTH_SHORT).show()
                    },
                    onOpenSymbolPicker = {
                        startActivity(Intent(this, PrefSymbolAct::class.java))
                    },
                    onOpenConnectionSetup = {
                        startActivity(Intent(this, BackendPrefs::class.java))
                    },
                    onOpenLocationSetup = {
                        startActivity(Intent(this, LocationPrefs::class.java))
                    },
                    onOpenMapModeSetup = {
                        MapModes.startMap(this, prefs, null)
                    },
                    onToggleShowObjects = {
                        val newState = prefs.toggleBoolean("show_objects", true)
                        showObjectsState.value = newState
                    },
                    onOpenNotificationPrefs = {
                        startActivity(Intent(this, NotificationPrefs::class.java))
                    },
                    onExportProfile = { exportPrefs() },
                    onImportProfile = { profileDocumentPicker.launch(arrayOf("*/*")) },
                    onShareDiagnostics = {
                        LogReportManager.shareDiagnosticReport(this)
                    },
                    onOpenAbout = {
                        AboutDialog(this).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPrefsState()
    }

    private fun refreshPrefsState() {
        callsignState.value = prefs.getCallsign()
        ssidState.value = prefs.getSsid()
        digiPathState.value = prefs.getString("digi_path", "WIDE1-1")
        userDigiPresetsState.value = prefs.getDigiPathPresets()
        symbolState.value = prefs.getString("symbol", "/$")
        backendNameState.value = prefs.getBackendName()
        locationSourceNameState.value = prefs.getLocationSourceName()
        mapModeTitleState.value = MapModes.defaultMapMode(this, prefs).title ?: getString(R.string.app_map)
        showObjectsState.value = prefs.getShowObjects()
    }
}
