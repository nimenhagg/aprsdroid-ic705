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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.BuildConfig
import org.aprsdroid.app.R
import org.aprsdroid.app.ui.component.DigiPathDialogCompose
import org.aprsdroid.app.ui.component.PhgDialogCompose
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.viewmodel.SettingsDialog
import org.aprsdroid.app.ui.viewmodel.SettingsIntent
import org.aprsdroid.app.ui.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val callsignDisplay = if (state.callsign.isEmpty()) stringResource(R.string.setting_not_set) else state.callsign
    val ssidDisplay = if (state.ssid == "0") "0" else "-${state.ssid}"
    val digiPathDisplay = if (state.digiPath.isEmpty()) stringResource(R.string.setting_direct_path) else state.digiPath
    val frequencyDisplay = if (state.frequency.isEmpty()) stringResource(R.string.setting_not_set) else state.frequency
    val statusDisplay = if (state.status.isEmpty()) stringResource(R.string.setting_not_set) else state.status
    val phgDisplay = if (state.phgEnabled) {
        val hUnit = if (state.phgHeightUnit.equals("ft", ignoreCase = true)) "ft" else "m"
        val pStr = if (state.phgPower.isNotEmpty()) "${state.phgPower}W" else ""
        val hStr = if (state.phgHeight.isNotEmpty()) "${state.phgHeight}$hUnit" else ""
        val gStr = if (state.phgGain.isNotEmpty()) "${state.phgGain}dB" else ""
        val parts = listOfNotNull(pStr.ifEmpty { null }, hStr.ifEmpty { null }, gStr.ifEmpty { null })
        if (parts.isNotEmpty()) parts.joinToString(" / ") else stringResource(R.string.setting_phg_enabled)
    } else {
        stringResource(R.string.setting_disabled)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_prefs),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(SettingsIntent.NavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(SettingsIntent.ImportProfile) }) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = stringResource(R.string.profile_load),
                        )
                    }
                    IconButton(onClick = { onIntent(SettingsIntent.ExportProfile) }) {
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
                    onClick = { onIntent(SettingsIntent.OpenIdentityDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_ssid),
                    value = ssidDisplay,
                    summary = stringResource(R.string.setting_ssid_summary),
                    icon = Icons.Default.Radio,
                    onClick = { onIntent(SettingsIntent.OpenSsidDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_aprs_path),
                    value = digiPathDisplay,
                    summary = stringResource(R.string.setting_digi_path_summary),
                    icon = Icons.Default.Route,
                    onClick = { onIntent(SettingsIntent.OpenDigiPathDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.p_symbol),
                    summary = stringResource(R.string.setting_symbol_summary_mod) + ": " + state.symbol,
                    icon = Icons.Default.LocationOn,
                    onClick = { onIntent(SettingsIntent.OpenSymbolPicker) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.p__connection))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.p_connsetup),
                    summary = state.backendName,
                    icon = Icons.Default.CellTower,
                    onClick = { onIntent(SettingsIntent.OpenConnectionSetup) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.p__position))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.setting_location_source),
                    summary = state.locationSourceName,
                    icon = Icons.Default.LocationOn,
                    onClick = { onIntent(SettingsIntent.OpenLocationSetup) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_frequency),
                    value = frequencyDisplay,
                    summary = stringResource(R.string.p_frequency_summary),
                    icon = Icons.Default.Radio,
                    onClick = { onIntent(SettingsIntent.OpenFrequencyDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_status),
                    value = statusDisplay,
                    summary = stringResource(R.string.p_status_summary),
                    icon = Icons.Default.Info,
                    onClick = { onIntent(SettingsIntent.OpenStatusDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_phg),
                    value = phgDisplay,
                    summary = stringResource(R.string.p_phg_summary),
                    icon = Icons.Default.SettingsInputAntenna,
                    onClick = { onIntent(SettingsIntent.OpenPhgDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_send_battery),
                    summary = stringResource(R.string.setting_send_battery_summary),
                    icon = Icons.Default.BatteryFull,
                    checked = state.sendBatteryInfo,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleSendBatteryInfo(it)) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.setting_map_display_category))
            PreferenceGroupCard {
                PreferenceValueItem(
                    title = stringResource(R.string.setting_default_map_source),
                    value = state.mapModeTitle,
                    summary = stringResource(R.string.setting_default_map_source_summary),
                    icon = Icons.Default.Map,
                    onClick = { onIntent(SettingsIntent.OpenMapModeDialog) },
                )
                if (state.mapModeTag == "custom") {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PreferenceItem(
                        title = stringResource(R.string.p_map_custom_url),
                        summary = state.mapCustomUrl.ifEmpty { stringResource(R.string.p_map_custom_url_summary) },
                        icon = Icons.Default.Map,
                        onClick = { onIntent(SettingsIntent.OpenMapCustomUrlDialog) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PreferenceItem(
                        title = stringResource(R.string.p_map_custom_subdomains),
                        summary = state.mapCustomSubdomains.ifEmpty {
                            stringResource(R.string.p_map_custom_subdomains_summary)
                        },
                        icon = Icons.Default.Map,
                        onClick = { onIntent(SettingsIntent.OpenMapCustomSubdomainsDialog) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.setting_station_tap_action),
                    value = if (state.stationTapAction == "details") {
                        stringResource(R.string.setting_station_tap_details)
                    } else {
                        stringResource(R.string.setting_station_tap_message)
                    },
                    summary = if (state.stationTapAction == "details") {
                        stringResource(R.string.setting_station_long_press_message)
                    } else {
                        stringResource(R.string.setting_station_long_press_details)
                    },
                    icon = Icons.Default.Person,
                    onClick = { onIntent(SettingsIntent.OpenStationTapDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_show_aprs_objects),
                    summary = stringResource(R.string.setting_show_aprs_objects_summary),
                    icon = Icons.Default.Layers,
                    checked = state.showObjects,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleShowObjects(it)) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_compact_lists),
                    summary = stringResource(R.string.setting_compact_lists_summary),
                    icon = Icons.AutoMirrored.Filled.List,
                    checked = state.compactListMode,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleCompactListMode(it)) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_notifications),
                    summary = stringResource(R.string.setting_notifications_summary),
                    icon = Icons.Default.Notifications,
                    onClick = { onIntent(SettingsIntent.OpenNotificationPrefs) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.setting_support_about_category))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.setting_share_diagnostics),
                    summary = stringResource(R.string.setting_share_diagnostics_summary),
                    icon = Icons.Default.BugReport,
                    onClick = { onIntent(SettingsIntent.ShareDiagnostics) },
                    showChevron = false,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_auto_check_updates),
                    summary = stringResource(R.string.setting_auto_check_updates_summary),
                    icon = Icons.Default.Sync,
                    checked = state.autoCheckUpdates,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleAutoCheckUpdates(it)) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_check_updates),
                    summary = stringResource(R.string.setting_check_updates_summary),
                    icon = Icons.Default.SystemUpdateAlt,
                    onClick = { onIntent(SettingsIntent.CheckForUpdates) },
                    showChevron = false,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.setting_credits_links),
                    summary = stringResource(R.string.setting_credits_links_summary),
                    icon = Icons.Default.Link,
                    onClick = { onIntent(SettingsIntent.OpenCreditsAndLinks) },
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
                    onClick = { onIntent(SettingsIntent.OpenAboutDialog) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (state.activeDialog is SettingsDialog.Ssid) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_ssid_dialog),
            options = ssidOptions,
            selected = state.ssid,
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSelect = { onIntent(SettingsIntent.SaveSsid(it)) },
        )
    }

    if (state.activeDialog is SettingsDialog.Frequency) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_frequency),
            initialValue = state.frequency,
            label = stringResource(R.string.p_frequency_summary),
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSave = { onIntent(SettingsIntent.SaveFrequency(it)) },
        )
    }

    if (state.activeDialog is SettingsDialog.Status) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_status_entry),
            initialValue = state.status,
            label = stringResource(R.string.p_status_summary),
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSave = { onIntent(SettingsIntent.SaveStatus(it.take(42))) },
        )
    }

    if (state.activeDialog is SettingsDialog.MapMode) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_default_map_source),
            options = state.mapModeOptions,
            selected = state.mapModeTag,
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSelect = { tag ->
                val title = state.mapModeOptions.find { it.first == tag }?.second ?: tag
                onIntent(SettingsIntent.SaveMapMode(tag, title))
            },
        )
    }

    if (state.activeDialog is SettingsDialog.MapCustomUrl) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_map_custom_url),
            initialValue = state.mapCustomUrl,
            label = stringResource(R.string.p_map_custom_url_summary),
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSave = { onIntent(SettingsIntent.SaveMapCustomUrl(it)) },
        )
    }

    if (state.activeDialog is SettingsDialog.MapCustomSubdomains) {
        PreferenceEditDialog(
            title = stringResource(R.string.p_map_custom_subdomains),
            initialValue = state.mapCustomSubdomains,
            label = stringResource(R.string.p_map_custom_subdomains_summary),
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSave = { onIntent(SettingsIntent.SaveMapCustomSubdomains(it)) },
        )
    }

    if (state.activeDialog is SettingsDialog.StationTap) {
        PreferenceSelectDialog(
            title = stringResource(R.string.setting_station_tap_action),
            options = listOf(
                "message" to stringResource(R.string.setting_station_tap_message_option),
                "details" to stringResource(R.string.setting_station_tap_details_option),
            ),
            selected = state.stationTapAction,
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSelect = { action ->
                onIntent(SettingsIntent.SaveStationTapAction(action))
            },
        )
    }

    if (state.activeDialog is SettingsDialog.DigiPath) {
        DigiPathDialogCompose(
            currentPath = state.digiPath,
            userPresets = state.userDigiPresets,
            onSavePath = { onIntent(SettingsIntent.SaveDigiPath(it)) },
            onAddPreset = { onIntent(SettingsIntent.AddDigiPreset(it)) },
            onDeletePreset = { onIntent(SettingsIntent.DeleteDigiPreset(it)) },
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
        )
    }

    if (state.activeDialog is SettingsDialog.Phg) {
        PhgDialogCompose(
            initialEnabled = state.phgEnabled,
            initialPower = state.phgPower,
            initialHeight = state.phgHeight,
            initialHeightUnit = state.phgHeightUnit,
            initialGain = state.phgGain,
            initialDir = state.phgDir,
            onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            onSave = { enabled, power, height, heightUnit, gain, dir ->
                onIntent(SettingsIntent.SavePhg(enabled, power, height, heightUnit, gain, dir))
            },
        )
    }
}
