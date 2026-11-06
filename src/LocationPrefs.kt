package org.aprsdroid.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.aprsdroid.app.location.PeriodicGPS
import org.aprsdroid.app.ui.screen.LocationPrefsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.LocationPrefsEffect
import org.aprsdroid.app.ui.viewmodel.LocationPrefsIntent
import org.aprsdroid.app.ui.viewmodel.LocationPrefsViewModel

class LocationPrefs : ComponentActivity(), PermissionHelper {

    companion object {
        const val REQUEST_GPS = 101
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    override var pendingPermissionAction: Int? = null
    override var pendingPermissions: Set<String> = emptySet()
    override val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> handlePermissionResult(grants) }

    private val viewModel: LocationPrefsViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    LocationPrefsViewModel(prefs)
                }
            },
        )[LocationPrefsViewModel::class.java]
    }

    private val mapLocationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val lat = data.getFloatExtra("lat", 0.0f).toDouble()
            val lon = data.getFloatExtra("lon", 0.0f).toDouble()
            viewModel.onIntent(LocationPrefsIntent.UpdateManualCoordinates(lat, lon))
        }
    }

    private fun copyGpsToManual() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = PeriodicGPS.bestProvider(lm)
        if (lm != null && provider != null) {
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
            if (loc != null) {
                viewModel.onIntent(
                    LocationPrefsIntent.UpdateManualCoordinates(loc.latitude, loc.longitude)
                )
                Toast.makeText(this, R.string.p_source_got_last, Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, R.string.p_source_no_fix, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)

        setContent {
            AprsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is LocationPrefsEffect.RequestLocationPermission -> {
                                checkPermissions(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                    REQUEST_GPS,
                                )
                            }
                            is LocationPrefsEffect.ShowToast -> {
                                Toast.makeText(this@LocationPrefs, effect.messageRes, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                LocationPrefsScreen(
                    uiState = uiState,
                    onIntent = viewModel::onIntent,
                    onBack = { finish() },
                    onPickOnMap = {
                        val intent = Intent(this@LocationPrefs, MapAct::class.java).apply {
                            action = Intent.ACTION_PICK
                            putExtra("lat", uiState.manualLat.toFloatOrNull() ?: 0.0f)
                            putExtra("lon", uiState.manualLon.toFloatOrNull() ?: 0.0f)
                        }
                        mapLocationPicker.launch(intent)
                    },
                    onUseLastLocation = { copyGpsToManual() },
                )
            }
        }
    }

    override fun getActionName(action: Int): Int = R.string.p__location
    override fun onAllPermissionsGranted(action: Int) {}
    override fun onPermissionsFailedCancel(action: Int) {}
}
