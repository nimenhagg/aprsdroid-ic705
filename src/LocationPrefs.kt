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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.location.PeriodicGPS
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.Locale

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

    // SmartBeaconing
    private val sbFastSpeedState = mutableStateOf("100")
    private val sbFastRateState = mutableStateOf("60")
    private val sbSlowSpeedState = mutableStateOf("5")
    private val sbSlowRateState = mutableStateOf("1200")
    private val sbTurnTimeState = mutableStateOf("15")
    private val sbTurnMinState = mutableStateOf("10")
    private val sbTurnSlopeState = mutableStateOf("240")

    // Periodic
    private val intervalState = mutableStateOf("10")
    private val distanceState = mutableStateOf("10")
    private val gpsActivationState = mutableStateOf("med")
    private val netLocState = mutableStateOf(false)

    // Manual
    private val manualLatState = mutableStateOf("0.000")
    private val manualLonState = mutableStateOf("0.000")
    private val periodicPositionState = mutableStateOf(true)

    // Location Privacy (平铺展开在定位设置最下方)
    private val ambiguityState = mutableStateOf("0")
    private val showSpdBearState = mutableStateOf(true)
    private val showAltitudeState = mutableStateOf(true)

    // Dialog control
    private val editDialogKey = mutableStateOf<String?>(null)
    private val showLocSourceDialog = mutableStateOf(false)
    private val showGpsActivationDialog = mutableStateOf(false)
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
        distanceState.value = prefs.getString("distance", "10")
        gpsActivationState.value = prefs.getString("gps_activation", "med")
        netLocState.value = prefs.getBoolean("netloc", false)

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
                val locSourceOptions = remember {
                    listOf(
                        "smartbeaconing" to "SmartBeaconing™ (智能自适应速率)",
                        "periodic" to "周期性 GPS (固定时间/距离间隔)",
                        "manual" to "手动指定静态位置"
                    )
                }

                val gpsActivationOptions = remember {
                    listOf(
                        "low" to "低耗电 (信标前 30 秒开启 GPS)",
                        "med" to "平衡 (信标前 60 秒开启 GPS)",
                        "high" to "高精度 (信标前 120 秒开启 GPS)",
                        "always" to "常开 (始终保持 GPS 锁定)"
                    )
                }

                val ambiguityOptions = remember {
                    listOf(
                        "0" to "精确坐标 (不模糊)",
                        "1" to "模糊 ~0.18 km (1 位)",
                        "2" to "模糊 ~1.8 km (2 位)",
                        "3" to "模糊 ~18 km (3 位)",
                        "4" to "模糊 ~180 km (4 位)"
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.p__location),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(android.R.string.cancel)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. 位置源选择
                        PreferenceCategoryHeader(title = stringResource(R.string.p_locsource))
                        PreferenceGroupCard {
                            val currentSourceTitle = locSourceOptions.find { pair -> pair.first == locSourceState.value }?.second ?: locSourceState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.p_locsource),
                                value = currentSourceTitle,
                                summary = stringResource(R.string.p_locsource_summary),
                                icon = Icons.Default.LocationOn,
                                onClick = { showLocSourceDialog.value = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 根据所选模式展开配置项
                        when (locSourceState.value) {
                            "smartbeaconing" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.p_source_smart))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_sb_fast_speed),
                                        value = "${sbFastSpeedState.value} km/h",
                                        summary = stringResource(R.string.p_sb_fast_speed_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.fastspeed" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_sb_fast_rate),
                                        value = "${sbFastRateState.value} s",
                                        summary = stringResource(R.string.p_sb_fast_rate_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.fastrate" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_sb_slow_speed),
                                        value = "${sbSlowSpeedState.value} km/h",
                                        summary = stringResource(R.string.p_sb_slow_speed_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.slowspeed" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_sb_slow_rate),
                                        value = "${sbSlowRateState.value} s",
                                        summary = stringResource(R.string.p_sb_slow_rate_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.slowrate" }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                PreferenceCategoryHeader(title = stringResource(R.string.p_corner_pegging))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_cp_turn_time),
                                        value = "${sbTurnTimeState.value} s",
                                        summary = stringResource(R.string.p_cp_turn_time_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "sb.turntime" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_cp_turn_angle),
                                        value = "${sbTurnMinState.value}°",
                                        summary = stringResource(R.string.p_cp_turn_angle_summary),
                                        icon = Icons.Default.TurnRight,
                                        onClick = { editDialogKey.value = "sb.turnmin" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_cp_turn_slope),
                                        value = sbTurnSlopeState.value,
                                        summary = stringResource(R.string.p_cp_turn_slope_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "sb.turnslope" }
                                    )
                                }
                            }
                            "periodic" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.p_source_periodic))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_interval),
                                        value = "${intervalState.value} min",
                                        summary = stringResource(R.string.p_interval_summary),
                                        icon = Icons.Default.Timer,
                                        onClick = { editDialogKey.value = "interval" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_distance),
                                        value = "${distanceState.value} km",
                                        summary = stringResource(R.string.p_distance_summary),
                                        icon = Icons.Default.Speed,
                                        onClick = { editDialogKey.value = "distance" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    val currentGpsTitle = gpsActivationOptions.find { pair -> pair.first == gpsActivationState.value }?.second ?: gpsActivationState.value
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_gps),
                                        value = currentGpsTitle,
                                        summary = stringResource(R.string.p_gps_summary),
                                        icon = Icons.Default.GpsFixed,
                                        onClick = { showGpsActivationDialog.value = true }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceSwitchItem(
                                        title = stringResource(R.string.p_netloc),
                                        summary = stringResource(R.string.p_netloc_summary),
                                        icon = Icons.Default.NetworkCheck,
                                        checked = netLocState.value,
                                        onCheckedChange = { checked ->
                                            netLocState.value = checked
                                            prefs.set("netloc", checked)
                                        }
                                    )
                                }
                            }
                            "manual" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.p_source_manual))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_source_lat),
                                        value = manualLatState.value,
                                        summary = stringResource(R.string.p_source_coord),
                                        icon = Icons.Default.LocationOn,
                                        onClick = { editDialogKey.value = "manual_lat" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_source_lon),
                                        value = manualLonState.value,
                                        summary = stringResource(R.string.p_source_coord),
                                        icon = Icons.Default.LocationOn,
                                        onClick = { editDialogKey.value = "manual_lon" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.p_source_from_map),
                                        summary = "在地图上十字准星处点击选取坐标",
                                        icon = Icons.Default.Map,
                                        onClick = {
                                            val intent = Intent(this@LocationPrefs, MapAct::class.java).apply {
                                                action = Intent.ACTION_PICK
                                                putExtra("lat", manualLatState.value.toFloatOrNull() ?: 0.0f)
                                                putExtra("lon", manualLonState.value.toFloatOrNull() ?: 0.0f)
                                            }
                                            mapLocationPicker.launch(intent)
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.p_source_get_last),
                                        summary = "读取系统上一次锁定的 GPS 真实位置",
                                        icon = Icons.Default.AddLocation,
                                        onClick = { copyGpsToManual() }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceSwitchItem(
                                        title = stringResource(R.string.p_source_auto),
                                        summary = stringResource(R.string.p_source_auto_summary),
                                        icon = Icons.Default.Timer,
                                        checked = periodicPositionState.value,
                                        onCheckedChange = { checked ->
                                            periodicPositionState.value = checked
                                            prefs.set("periodicposition", checked)
                                        }
                                    )
                                    if (periodicPositionState.value) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_interval),
                                            value = "${intervalState.value} min",
                                            summary = stringResource(R.string.p_interval_summary),
                                            icon = Icons.Default.Timer,
                                            onClick = { editDialogKey.value = "interval" }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. 位置隐私 (平铺展开在定位设置最下方)
                        PreferenceCategoryHeader(title = stringResource(R.string.p_privacy))
                        PreferenceGroupCard {
                            val currentAmbiguityLabel = ambiguityOptions.find { it.first == ambiguityState.value }?.second ?: ambiguityState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.p_priv_ambiguity),
                                value = currentAmbiguityLabel,
                                summary = stringResource(R.string.p_priv_ambiguity_summary),
                                icon = Icons.Default.BlurOn,
                                onClick = { showAmbiguityDialog.value = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_priv_spdbear),
                                summary = stringResource(R.string.p_priv_spdbear_summary),
                                icon = Icons.Default.Speed,
                                checked = showSpdBearState.value,
                                onCheckedChange = { checked ->
                                    showSpdBearState.value = checked
                                    prefs.set("priv_spdbear", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_priv_altitude),
                                summary = stringResource(R.string.p_priv_altitude_summary),
                                icon = Icons.Default.Height,
                                checked = showAltitudeState.value,
                                onCheckedChange = { checked ->
                                    showAltitudeState.value = checked
                                    prefs.set("priv_altitude", checked)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Dialogs
                    if (showLocSourceDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_locsource),
                            options = locSourceOptions,
                            selected = locSourceState.value,
                            onDismiss = { showLocSourceDialog.value = false },
                            onSelect = { selected ->
                                locSourceState.value = selected
                                prefs.set("loc_source", selected)
                                if (selected != "manual") {
                                    checkPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_GPS)
                                }
                            }
                        )
                    }

                    if (showGpsActivationDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_gps),
                            options = gpsActivationOptions,
                            selected = gpsActivationState.value,
                            onDismiss = { showGpsActivationDialog.value = false },
                            onSelect = { selected ->
                                gpsActivationState.value = selected
                                prefs.set("gps_activation", selected)
                            }
                        )
                    }

                    if (showAmbiguityDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_priv_ambiguity),
                            options = ambiguityOptions,
                            selected = ambiguityState.value,
                            onDismiss = { showAmbiguityDialog.value = false },
                            onSelect = { selected ->
                                ambiguityState.value = selected
                                prefs.set("priv_ambiguity", selected)
                            }
                        )
                    }

                    editDialogKey.value?.let { key ->
                        val currentVal = prefs.getString(key, "")
                        PreferenceEditDialog(
                            title = "修改参数",
                            initialValue = currentVal,
                            onDismiss = { editDialogKey.value = null },
                            onSave = { newValue ->
                                prefs.set(key, newValue)
                                refreshState()
                            }
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
