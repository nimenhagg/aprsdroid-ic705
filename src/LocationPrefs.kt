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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.aprsdroid.app.location.PeriodicGPS
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.theme.AprsTheme

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

    private val mapLocationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val lat = data.getFloatExtra("lat", 0.0f)
            val lon = data.getFloatExtra("lon", 0.0f)
            prefs.set("manual_lat", String.format(Locale.US, "%.5f", lat))
            prefs.set("manual_lon", String.format(Locale.US, "%.5f", lon))
            refreshState()
        }
    }

    private val locSourceState = mutableStateOf("smartbeaconing")

    private val sbFastSpeedState = mutableStateOf("100")
    private val sbFastRateState = mutableStateOf("60")
    private val sbSlowSpeedState = mutableStateOf("5")
    private val sbSlowRateState = mutableStateOf("1200")
    private val sbTurnTimeState = mutableStateOf("15")
    private val sbTurnMinState = mutableStateOf("10")
    private val sbTurnSlopeState = mutableStateOf("240")

    private val intervalState = mutableStateOf("10")

    private val manualLatState = mutableStateOf("0.000")
    private val manualLonState = mutableStateOf("0.000")
    private val periodicPositionState = mutableStateOf(true)

    private val ambiguityState = mutableStateOf("0")
    private val showSpdBearState = mutableStateOf(true)
    private val showAltitudeState = mutableStateOf(true)

    private val editDialogKey = mutableStateOf<String?>(null)
    private val showLocSourceDialog = mutableStateOf(false)
    private val showAmbiguityDialog = mutableStateOf(false)

    private fun refreshState() {
        locSourceState.value = prefs.getString("loc_source", "smartbeaconing")

        sbFastSpeedState.value = prefs.getString("sb.fastspeed", "100")
        sbFastRateState.value = prefs.getString("sb.fastrate", "60")
        sbSlowSpeedState.value = prefs.getString("sb.slowspeed", "5")
        sbSlowRateState.value = prefs.getString("sb.slowrate", "1200")
        sbTurnTimeState.value = prefs.getString("sb.turntime", "15")
        sbTurnMinState.value = prefs.getString("sb.turnmin", "10")
        sbTurnSlopeState.value = prefs.getString("sb.turnslope", "240")

        intervalState.value = prefs.getString("interval", "10")

        manualLatState.value = prefs.getString("manual_lat", "0.000")
        manualLonState.value = prefs.getString("manual_lon", "0.000")
        periodicPositionState.value = prefs.getBoolean("periodicposition", true)

        ambiguityState.value = prefs.getString("priv_ambiguity", "0")
        showSpdBearState.value = prefs.getBoolean("priv_spdbear", true)
        showAltitudeState.value = prefs.getBoolean("priv_altitude", true)
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
                prefs.set("manual_lat", String.format(Locale.US, "%.5f", loc.latitude))
                prefs.set("manual_lon", String.format(Locale.US, "%.5f", loc.longitude))
                refreshState()
                Toast.makeText(this, R.string.p_source_got_last, Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, R.string.p_source_no_fix, Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)
        refreshState()

        setContent {
            AprsTheme {
                val locSourceOptions = listOf(
                    "smartbeaconing" to stringResource(R.string.setting_location_source_smart_option),
                    "periodic" to stringResource(R.string.setting_location_source_periodic_option),
                    "manual" to stringResource(R.string.setting_location_source_manual_option),
                )
                val ambiguityOptions = listOf(
                    "0" to stringResource(R.string.setting_ambiguity_exact),
                    "1" to stringResource(R.string.setting_ambiguity_1),
                    "2" to stringResource(R.string.setting_ambiguity_2),
                    "3" to stringResource(R.string.setting_ambiguity_3),
                    "4" to stringResource(R.string.setting_ambiguity_4),
                )

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.p__location),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(android.R.string.cancel),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceCategoryHeader(title = stringResource(R.string.setting_location_source))
                        PreferenceGroupCard {
                            val currentSourceTitle = locSourceOptions
                                .firstOrNull { it.first == locSourceState.value }
                                ?.second ?: locSourceState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.setting_location_source),
                                value = currentSourceTitle,
                                summary = stringResource(R.string.setting_location_source_summary),
                                icon = Icons.Default.LocationOn,
                                onClick = { showLocSourceDialog.value = true },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (locSourceState.value) {
                            "smartbeaconing" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_smartbeacon_category))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_sb_fast_speed),
                                        value = "${sbFastSpeedState.value} km/h",
                                        summary = stringResource(R.string.setting_sb_fast_speed_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.fastspeed" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_sb_fast_rate),
                                        value = "${sbFastRateState.value} s",
                                        summary = stringResource(R.string.setting_sb_fast_rate_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.fastrate" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_sb_slow_speed),
                                        value = "${sbSlowSpeedState.value} km/h",
                                        summary = stringResource(R.string.setting_sb_slow_speed_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.slowspeed" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_sb_slow_rate),
                                        value = "${sbSlowRateState.value} s",
                                        summary = stringResource(R.string.setting_sb_slow_rate_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.slowrate" },
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_turn_beacon_category))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_turn_time),
                                        value = "${sbTurnTimeState.value} s",
                                        summary = stringResource(R.string.setting_turn_time_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.turntime" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_turn_angle),
                                        value = "${sbTurnMinState.value}°",
                                        summary = stringResource(R.string.setting_turn_angle_summary),
                                        icon = Icons.Default.TurnRight,
                                        onClick = { editDialogKey.value = "sb.turnmin" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_turn_slope),
                                        value = sbTurnSlopeState.value,
                                        summary = stringResource(R.string.setting_turn_slope_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.turnslope" },
                                    )
                                }
                            }

                            "periodic" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_periodic_gps_category))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_time_interval),
                                        value = "${intervalState.value} min",
                                        summary = stringResource(R.string.setting_time_interval_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "interval" },
                                    )
                                }
                            }

                            "manual" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_manual_position_category))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_latitude),
                                        value = manualLatState.value,
                                        summary = stringResource(R.string.setting_decimal_degrees),
                                        icon = Icons.Default.LocationOn,
                                        onClick = { editDialogKey.value = "manual_lat" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_longitude),
                                        value = manualLonState.value,
                                        summary = stringResource(R.string.setting_decimal_degrees),
                                        icon = Icons.Default.LocationOn,
                                        onClick = { editDialogKey.value = "manual_lon" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.setting_pick_on_map),
                                        summary = stringResource(R.string.setting_pick_on_map_summary),
                                        icon = Icons.Default.Map,
                                        onClick = {
                                            val intent = Intent(this@LocationPrefs, MapAct::class.java).apply {
                                                action = Intent.ACTION_PICK
                                                putExtra("lat", manualLatState.value.toFloatOrNull() ?: 0.0f)
                                                putExtra("lon", manualLonState.value.toFloatOrNull() ?: 0.0f)
                                            }
                                            mapLocationPicker.launch(intent)
                                        },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.setting_use_last_location),
                                        summary = stringResource(R.string.setting_use_last_location_summary),
                                        icon = Icons.Default.AddLocation,
                                        onClick = { copyGpsToManual() },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceSwitchItem(
                                        title = stringResource(R.string.setting_repeat_manual_position),
                                        summary = stringResource(R.string.setting_repeat_manual_position_summary),
                                        icon = Icons.Default.Timer,
                                        checked = periodicPositionState.value,
                                        onCheckedChange = { checked ->
                                            periodicPositionState.value = checked
                                            prefs.set("periodicposition", checked)
                                        },
                                    )
                                    if (periodicPositionState.value) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.setting_time_interval),
                                            value = "${intervalState.value} min",
                                            summary = stringResource(R.string.setting_time_interval_summary),
                                            icon = Icons.Default.Timer,
                                            onClick = { editDialogKey.value = "interval" },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        PreferenceCategoryHeader(title = stringResource(R.string.setting_location_privacy_category))
                        PreferenceGroupCard {
                            val currentAmbiguityLabel = ambiguityOptions
                                .firstOrNull { it.first == ambiguityState.value }
                                ?.second ?: ambiguityState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.setting_position_ambiguity),
                                value = currentAmbiguityLabel,
                                summary = stringResource(R.string.setting_position_ambiguity_summary),
                                icon = Icons.Default.BlurOn,
                                onClick = { showAmbiguityDialog.value = true },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.setting_include_speed_course),
                                summary = stringResource(R.string.setting_include_speed_course_summary),
                                icon = Icons.Default.Speed,
                                checked = showSpdBearState.value,
                                onCheckedChange = { checked ->
                                    showSpdBearState.value = checked
                                    prefs.set("priv_spdbear", checked)
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.setting_include_altitude),
                                summary = stringResource(R.string.setting_include_altitude_summary),
                                icon = Icons.Default.Height,
                                checked = showAltitudeState.value,
                                onCheckedChange = { checked ->
                                    showAltitudeState.value = checked
                                    prefs.set("priv_altitude", checked)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    if (showLocSourceDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.setting_location_source),
                            options = locSourceOptions,
                            selected = locSourceState.value,
                            onDismiss = { showLocSourceDialog.value = false },
                            onSelect = { selected ->
                                locSourceState.value = selected
                                prefs.set("loc_source", selected)
                                if (selected != "manual") {
                                    checkPermissions(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                        REQUEST_GPS,
                                    )
                                }
                            },
                        )
                    }

                    if (showAmbiguityDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.setting_position_ambiguity),
                            options = ambiguityOptions,
                            selected = ambiguityState.value,
                            onDismiss = { showAmbiguityDialog.value = false },
                            onSelect = { selected ->
                                ambiguityState.value = selected
                                prefs.set("priv_ambiguity", selected)
                            },
                        )
                    }

                    editDialogKey.value?.let { key ->
                        PreferenceEditDialog(
                            title = stringResource(R.string.setting_edit_value),
                            initialValue = prefs.getString(key, ""),
                            onDismiss = { editDialogKey.value = null },
                            onSave = { newValue ->
                                prefs.set(key, newValue)
                                refreshState()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun getActionName(action: Int): Int = R.string.p__location
    override fun onAllPermissionsGranted(action: Int) {}
    override fun onPermissionsFailedCancel(action: Int) {}
}
