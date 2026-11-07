package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.aprsdroid.app.ui.viewmodel.SettingsDialog
import org.aprsdroid.app.ui.viewmodel.SettingsEffect
import org.aprsdroid.app.ui.viewmodel.SettingsIntent
import org.aprsdroid.app.ui.viewmodel.SettingsViewModel
import org.aprsdroid.app.update.GitHubUpdateChecker
import org.aprsdroid.app.update.UpdateCheckResult
import org.json.JSONArray
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
                    val json = JSONObject()
                    for ((key, value) in sp.all) {
                        if (!ProfileImportSchema.isImportableKey(key)) continue
                        when (value) {
                            is Set<*> -> {
                                val strings = value.filterIsInstance<String>()
                                if (strings.size == value.size) {
                                    json.put(key, JSONArray(strings))
                                }
                            }
                            else -> json.put(key, value)
                        }
                    }
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

    private val viewModel: SettingsViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        store = prefs,
                        defaultStatus = getString(R.string.default_status),
                        defaultMapTitle = getString(R.string.app_map),
                    )
                }
            },
        )[SettingsViewModel::class.java]
    }

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
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is SettingsEffect.DigiPresetAdded -> {
                                Toast.makeText(
                                    this@PrefsAct,
                                    getString(R.string.digi_path_preset_saved, effect.preset),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is SettingsEffect.DigiPresetDeleted -> {
                                Toast.makeText(
                                    this@PrefsAct,
                                    getString(R.string.digi_path_preset_deleted, effect.preset),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is SettingsEffect.TriggerManualUpdateCheck -> {
                                checkForUpdatesManually()
                            }
                        }
                    }
                }

                val availableMapModes = remember {
                    MapModes.all_mapmodes
                        .filter { it.isAvailable(this@PrefsAct) }
                        .map { mode -> mode.tag to (mode.title ?: mode.tag) }
                }

                val handleIntent: (SettingsIntent) -> Unit = { intent ->
                    when (intent) {
                        is SettingsIntent.NavigateBack -> onBackPressedDispatcher.onBackPressed()
                        is SettingsIntent.OpenSymbolPicker -> startActivity(Intent(this@PrefsAct, PrefSymbolAct::class.java))
                        is SettingsIntent.OpenConnectionSetup -> startActivity(Intent(this@PrefsAct, BackendPrefs::class.java))
                        is SettingsIntent.OpenLocationSetup -> startActivity(Intent(this@PrefsAct, LocationPrefs::class.java))
                        is SettingsIntent.OpenNotificationPrefs -> startActivity(Intent(this@PrefsAct, NotificationSettingsActivity::class.java))
                        is SettingsIntent.ExportProfile -> exportPrefs()
                        is SettingsIntent.ImportProfile -> profileDocumentPicker.launch(arrayOf("*/*"))
                        is SettingsIntent.ShareDiagnostics -> LogReportManager.shareDiagnosticReport(this@PrefsAct)
                        is SettingsIntent.CheckForUpdates -> checkForUpdatesManually()
                        is SettingsIntent.OpenCreditsAndLinks -> startActivity(Intent(this@PrefsAct, CreditsAndLinksActivity::class.java))
                        is SettingsIntent.SaveMapMode -> {
                            MapModes.setDefault(prefs, intent.tag)
                            val title = MapModes.defaultMapMode(this@PrefsAct, prefs).title ?: getString(R.string.app_map)
                            viewModel.onIntent(SettingsIntent.SaveMapMode(intent.tag, title))
                        }
                        else -> viewModel.onIntent(intent)
                    }
                }

                SettingsScreen(
                    state = uiState.copy(
                        mapModeOptions = availableMapModes,
                        backendName = prefs.getBackendName(),
                        locationSourceName = prefs.getLocationSourceName(),
                        mapModeTitle = uiState.mapModeTitle.ifEmpty {
                            MapModes.defaultMapMode(this, prefs).title ?: getString(R.string.app_map)
                        },
                    ),
                    onIntent = handleIntent,
                )
                when (val dialog = uiState.activeDialog) {
                    is SettingsDialog.Identity -> {
                        PasscodeDialogCompose(
                            initialCallsign = prefs.getCallsign(),
                            initialPasscode = prefs.getString("passcode", ""),
                            firstRun = false,
                            onDismiss = {
                                viewModel.onIntent(SettingsIntent.DismissDialog)
                            },
                            onSave = { call, pass ->
                                viewModel.onIntent(SettingsIntent.SaveCredentials(call, pass))
                            },
                        )
                    }
                    is SettingsDialog.About -> {
                        AboutDialogContent(
                            onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                            onOpenGithub = {
                                UrlOpener.open(
                                    this@PrefsAct,
                                    "https://github.com/nimenhagg/aprsdroid-ic705",
                                )
                            },
                        )
                    }
                    is SettingsDialog.Update -> {
                        val update = dialog.update
                        AlertDialog(
                            onDismissRequest = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                            title = {
                                Text(stringResource(R.string.update_available_title, update.latest))
                            },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.update_available_message,
                                        update.current,
                                        update.deviceArchitecture,
                                        update.releaseNotes,
                                    ),
                                    modifier = Modifier
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.onIntent(SettingsIntent.DismissDialog)
                                        UrlOpener.open(this@PrefsAct, update.releaseUrl)
                                    },
                                ) {
                                    Text(stringResource(R.string.update_open_release))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.onIntent(SettingsIntent.DismissDialog) }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            },
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onIntent(SettingsIntent.RefreshState)
    }

    private fun checkForUpdatesManually() {
        if (updateCheckInFlight) return
        updateCheckInFlight = true
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
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        AppLog.i(
                            "UPDATE",
                            "manual_check_update_available",
                            mapOf("current" to result.current, "latest" to result.latest),
                        )
                        viewModel.onIntent(SettingsIntent.SetUpdateAvailable(result))
                    }
                    is UpdateCheckResult.Failure -> {
                        AppLog.w(
                            "UPDATE",
                            "manual_check_failed",
                            mapOf("reason" to result.message),
                        )
                    }
                }
            }
        }
    }
}
