package org.aprsdroid.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity
import org.aprsdroid.app.ui.screen.BackendPrefsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.BackendPrefsEffect
import org.aprsdroid.app.ui.viewmodel.BackendPrefsIntent
import org.aprsdroid.app.ui.viewmodel.BackendPrefsViewModel

class BackendPrefs : ComponentActivity(), PermissionHelper {

    companion object {
        const val BACKEND_PERMISSION = 1000
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    override var pendingPermissionAction: Int? = null
    override var pendingPermissions: Set<String> = emptySet()
    override val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> handlePermissionResult(grants) }

    private val viewModel: BackendPrefsViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    BackendPrefsViewModel(PrefsWrapper(applicationContext))
                }
            },
        )[BackendPrefsViewModel::class.java]
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, AprsBackend.BLUETOOTH_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun bondedBluetoothDevices(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val devices = manager?.adapter?.bondedDevices.orEmpty()
            devices
                .sortedWith(compareBy({ it.name?.lowercase().orEmpty() }, { it.address }))
                .map { device ->
                    val address = device.address.orEmpty()
                    val label = device.name?.takeIf { it.isNotBlank() }
                        ?.let { "$it · $address" }
                        ?: address
                    address to label
                }
                .filter { it.first.isNotBlank() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)

        setContent {
            AprsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel.effect) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is BackendPrefsEffect.RequestPermissions -> {
                                checkPermissions(effect.permissions.toTypedArray(), BACKEND_PERMISSION)
                            }

                            BackendPrefsEffect.OpenBluetoothSettings -> {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        }
                    }
                }

                BackendPrefsScreen(
                    state = state,
                    onIntent = viewModel::dispatch,
                    onNavigateBack = { finish() },
                    bondedBluetoothDevices = bondedBluetoothDevices(),
                    hasBluetoothPermission = hasBluetoothPermission(),
                    onRequestBluetoothPermission = {
                        checkPermissions(
                            arrayOf(AprsBackend.BLUETOOTH_PERMISSION),
                            BACKEND_PERMISSION,
                        )
                    },
                    onOpenBluetoothSettings = {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    onOpenDiagnostic = {
                        startActivity(Intent(this@BackendPrefs, Ic705RxDiagnosticActivity::class.java))
                    },
                    onOpenHelpUrl = { url ->
                        UrlOpener.open(this@BackendPrefs, url)
                    },
                    audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager,
                    initialCallsign = prefs.getCallsign(),
                    initialPasscode = prefs.getString("passcode", ""),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.dispatch(BackendPrefsIntent.Refresh)
    }

    override fun getActionName(action: Int): Int = R.string.p__connection
    override fun onAllPermissionsGranted(action: Int) {
        viewModel.dispatch(BackendPrefsIntent.Refresh)
    }
    override fun onPermissionsFailedCancel(action: Int) {}
}
