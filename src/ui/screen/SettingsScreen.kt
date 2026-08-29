package org.aprsdroid.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.BuildConfig
import org.aprsdroid.app.R
import org.aprsdroid.app.ui.component.DigiPathDialogCompose
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.prefs.rememberCompactListMode
import org.aprsdroid.app.ui.prefs.setCompactListMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    callsign: String,
    ssid: String,
    digiPath: String,
    userDigiPresets: Set<String>,
    symbol: String,
    frequency: String,
    status: String,
    backendName: String,
    locationSourceName: String,
    mapModeTag: String,
    mapModeTitle: String,
    mapModeOptions: List<Pair<String, String>>,
    mapCustomUrl: String,
    mapCustomSubdomains: String,
    showObjects: Boolean,
    sendBatteryInfo: Boolean,
    stationTapAction: String,
    onBack: () -> Unit,
    onOpenCallsignDialog: () -> Unit,
    onSaveSsid: (String) -> Unit,
    onSaveDigiPath: (String) -> Unit,
    onAddDigiPreset: (String) -> Unit,
    onDeleteDigiPreset: (String) -> Unit,
    onOpenSymbolPicker: () -> Unit,
    onSaveFrequency: (String) -> Unit,
    onSaveStatus: (String) -> Unit,
    onOpenConnectionSetup: () -> Unit,
    onOpenLocationSetup: () -> Unit,
    onSaveMapMode: (String) -> Unit,
    onSaveMapCustomUrl: (String) -> Unit,
    onSaveMapCustomSubdomains: (String) -> Unit,
    onToggleShowObjects: (Boolean) -> Unit,
    onToggleSendBatteryInfo: (Boolean) -> Unit,
    onSaveStationTapAction: (String) -> Unit,
    onOpenNotificationPrefs: () -> Unit,
    onExportProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenCreditsAndLinks: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val compactListMode by rememberCompactListMode()
    var showSsidDialog by remember { mutableStateOf(false) }
    var showDigiPathDialog by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showMapModeDialog by remember { mutableStateOf(false) }
    var showMapCustomUrlDialog by remember { mutableStateOf(false) }
    var showMapCustomSubdomainsDialog by remember { mutableStateOf(false) }
    var showStationTapDialog by remember { mutableStateOf(false) }

    val ssidOptions = listOf(
        "0" to stringResource(R.string.setting_ssid_0),
        "1" to stringResource(R.string.setting_ssid_1),
        "2" to stringResource(R.string.setting_ssid_2),
        "3" to stringResource(R.string.setting_ssid_3),
        "4" to stringResource(R.string.setting_ssid_4),
        "5" to stringResource(R.string.setting_ssid_5),
        "6" to stringResource(R.string.setting_ssid_6),
        "7" to stringResource(R.string.setting_ssid_7),
        "8" to stringResource(R.string.setting_ssid_8),
        "9" to stringResource(R.string.setting_ssid_9),
        "10" to stringResource(R.string.setting_ssid_10),
        "11" to stringResource(R.string.setting_ssid_11),
        "12" to stringResource(R.string.setting_ssid_12),
        "13" to stringResource(R.string.setting_ssid_13),
        "14" to stringResource(R.string.setting_ssid_14),
        "15" to stringResource(R.string.setting_ssid_15),
    )
    val callsignDisplay = if (callsign.isEmpty()) stringResource(R.string.setting_not_set) else callsign
    val ssidDisplay = if (ssid == "0") "0" else "-$ssid"
    val digiPathDisplay = if (digiPath.isEmpty()) stringResource(R.string.setting_direct_path) else digiPath
    val frequencyDisplay = if (frequency.isEmpty()) stringResource(R.string.setting_not_set) else frequency
    val statusDisplay = if (status.isEmpty()) stringResource(R.string.setting_not_set) else status

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_prefs),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportProfile) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = stringResource(R.string.profile_load),
                        )
                    }
                    IconButton(onClick = onExportProfile) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.profile_export),
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

            PreferenceCategoryHeader(title = stringResource(R.string.p__aprs))
            PreferenceGroupCard {
                PreferenceValueItem(
                    title = stringResource(R.string.setting_callsign_no_ssid),
                    value = callsignDisplay,
                    summary = stringResource(R.string.setting_callsign_summary_mod),
                    icon = Icons.Default.Person,
                    onClick = onOpenCallsignDialog,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_ssid),
                    value = ssidDisplay,
                    summary = stringResource(R.string.setting_ssid_summary),
                    icon = Icons.Default.Radio,
                    onClick = { showSsidDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_aprs_path),
                    value = digiPathDisplay,
                    summary = stringResource(R.string.setting_digi_path_summary),
                    icon = Icons.Default.Route,
                    onClick = { showDigiPathDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.p_symbol),
                    summary = stringResource(R.string.setting_symbol_summary_mod) + ": " + symbol,
                    icon = Icons.Default.LocationOn,
                    onClick = onOpenSymbolPicker,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.p__connection))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.p_connsetup),
                    summary = backendName,
                    icon = Icons.Default.CellTower,
                    onClick = onOpenConnectionSetup,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.p__position))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.setting_location_source),
                    summary = locationSourceName,
                    icon = Icons.Default.LocationOn,
                    onClick = onOpenLocationSetup,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_frequency),
                    value = frequencyDisplay,
                    summary = stringResource(R.string.p_frequency_summary),
                    icon = Icons.Default.Radio,
                    onClick = { showFrequencyDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_status),
                    value = statusDisplay,
                    summary = stringResource(R.string.p_status_summary),
                    icon = Icons.Default.Info,
                    onClick = { showStatusDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_send_battery),
                    summary = stringResource(R.string.setting_send_battery_summary),
                    icon = Icons.Default.BatteryFull,
                    checked = sendBatteryInfo,
                    onCheckedChange = onToggleSendBatteryInfo,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.setting_map_display_category))
            PreferenceGroupCard {
                PreferenceValueItem(
                    title = stringResource(R.string.setting_default_map_source),
                    value = mapModeTitle,
                    summary = stringResource(R.string.setting_default_map_source_summary),
                    icon = Icons.Default.Map,
                    onClick = { showMapModeDialog = true },
                )
                if (mapModeTag == "custom") {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PreferenceItem(
                        title = stringResource(R.string.p_map_custom_url),
                        summary = mapCustomUrl.ifEmpty { stringResource(R.string.p_map_custom_url_summary) },
                        icon = Icons.Default.Map,
                        onClick = { showMapCustomUrlDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PreferenceItem(
                        title = stringResource(R.string.p_map_custom_subdomains),
                        summary = mapCustomSubdomains.ifEmpty {
                            stringResource(R.string.p_map_custom_subdomains_summary)
                        },
                        icon = Icons.Default.Map,
                        onClick = { showMapCustomSubdomainsDialog = true },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.setting_station_tap_action),
                    value = if (stationTapAction == "details") {
                        stringResource(R.string.setting_station_tap_details)
                    } else {
                        stringResource(R.string.setting_station_tap_message)
                    },
                    summary = if (stationTapAction == "details") {
                        stringResource(R.string.setting_station_long_press_message)
                    } else {
                        stringResource(R.string.setting_station_long_press_details)
                    },
                    icon = Icons.Default.Person,
                    onClick = { showStationTapDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_show_aprs_objects),
                    summary = stringResource(R.string.setting_show_aprs_objects_summary),
                    icon = Icons.Default.Layers,
                    checked = showObjects,
                    onCheckedChange = onToggleShowObjects,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_compact_lists),
                    summary = stringResource(R.string.setting_compact_lists_summary),
                    icon = Icons.Default.List,
                    checked = compactListMode,
                    onCheckedChange = { enabled -> setCompactListMode(context, enabled) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_notifications),
                    summary = stringResource(R.string.setting_notifications_summary),
                    icon = Icons.Default.Notifications,
                    onClick = onOpenNotificationPrefs,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.setting_support_about_category))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.setting_share_diagnostics),
                    summary = stringResource(R.string.setting_share_diagnostics_summary),
                    icon = Icons.Default.BugReport,
                    onClick = onShareDiagnostics,
                    showChevron = false,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_check_updates),
                    summary = stringResource(R.string.setting_check_updates_summary),
                    icon = Icons.Default.SystemUpdateAlt,
                    onClick = onCheckForUpdates,
                    showChevron = false,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_credits_links),
                    summary = stringResource(R.string.setting_credits_links_summary),
                    icon = Icons.Default.Link,
                    onClick = onOpenCreditsAndLinks,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.about),
                    summary = stringResource(
                        R.string.setting_version_summary,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.BUILD_TYPE,
                    ),
                    icon = Icons.Default.Info,
                    onClick = onOpenAbout,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSsidDialog) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_ssid_dialog),
            options = ssidOptions,
            selected = ssid,
            onDismiss = { showSsidDialog = false },
            onSelect = onSaveSsid,
        )
    }

    if (showFrequencyDialog) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_frequency),
            initialValue = frequency,
            label = stringResource(R.string.p_frequency_summary),
            onDismiss = { showFrequencyDialog = false },
            onSave = onSaveFrequency,
        )
    }

    if (showStatusDialog) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_status_entry),
            initialValue = status,
            label = stringResource(R.string.p_status_summary),
            onDismiss = { showStatusDialog = false },
            onSave = { onSaveStatus(it.take(42)) },
        )
    }

    if (showMapModeDialog) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_default_map_source),
            options = mapModeOptions,
            selected = mapModeTag,
            onDismiss = { showMapModeDialog = false },
            onSelect = onSaveMapMode,
        )
    }

    if (showMapCustomUrlDialog) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_map_custom_url),
            initialValue = mapCustomUrl,
            label = stringResource(R.string.p_map_custom_url_summary),
            onDismiss = { showMapCustomUrlDialog = false },
            onSave = onSaveMapCustomUrl,
        )
    }

    if (showMapCustomSubdomainsDialog) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_map_custom_subdomains),
            initialValue = mapCustomSubdomains,
            label = stringResource(R.string.p_map_custom_subdomains_summary),
            onDismiss = { showMapCustomSubdomainsDialog = false },
            onSave = onSaveMapCustomSubdomains,
        )
    }

    if (showStationTapDialog) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_station_tap_action),
            options = listOf(
                "message" to stringResource(R.string.setting_station_tap_message_option),
                "details" to stringResource(R.string.setting_station_tap_details_option),
            ),
            selected = stationTapAction,
            onDismiss = { showStationTapDialog = false },
            onSelect = { action ->
                onSaveStationTapAction(action)
                showStationTapDialog = false
            },
        )
    }

    if (showDigiPathDialog) {
        DigiPathDialogCompose(
            currentPath = digiPath,
            userPresets = userDigiPresets,
            onSavePath = onSaveDigiPath,
            onAddPreset = onAddDigiPreset,
            onDeletePreset = onDeleteDigiPreset,
            onDismiss = { showDigiPathDialog = false },
        )
    }
}
