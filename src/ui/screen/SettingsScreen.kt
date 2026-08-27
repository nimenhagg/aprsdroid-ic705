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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.BuildConfig
import org.aprsdroid.app.R
import org.aprsdroid.app.ui.component.DigiPathDialogCompose
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    callsign: String,
    ssid: String,
    digiPath: String,
    userDigiPresets: Set<String>,
    symbol: String,
    backendName: String,
    locationSourceName: String,
    mapModeTitle: String,
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
    onOpenConnectionSetup: () -> Unit,
    onOpenLocationSetup: () -> Unit,
    onOpenMapModeSetup: () -> Unit,
    onToggleShowObjects: (Boolean) -> Unit,
    onToggleSendBatteryInfo: (Boolean) -> Unit,
    onSaveStationTapAction: (String) -> Unit,
    onOpenNotificationPrefs: () -> Unit,
    onExportProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var showSsidDialog by remember { mutableStateOf(false) }
    var showDigiPathDialog by remember { mutableStateOf(false) }
    var showStationTapDialog by remember { mutableStateOf(false) }

    val ssidOptions = remember {
        listOf(
            "0" to "0 - 主台站 (Primary Station)",
            "1" to "1 - 附加台站 (Digi / WX / Mobile)",
            "5" to "5 - 其他设备 / 智能手机 (Smartphones / IC-705)",
            "7" to "7 - 手持对讲机 (HTs / Walkie-Talkies)",
            "8" to "8 - 船用电台 (Marine)",
            "9" to "9 - 车载移动台 (Primary Mobile)",
            "10" to "10 - 互联网接入点 / 网关 (IGate)",
            "15" to "15 - 备用移动台 (Other Mobile)"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_prefs),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportProfile) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = stringResource(R.string.profile_load)
                        )
                    }
                    IconButton(onClick = onExportProfile) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.profile_export)
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

            // 1. APRS 核心台站配置
            PreferenceCategoryHeader(title = stringResource(R.string.p__aprs))
            PreferenceGroupCard {
                PreferenceValueItem(
                    title = stringResource(R.string.p_callsign_nossid),
                    value = callsign.ifEmpty { "未设置" },
                    summary = stringResource(R.string.p_callsign_summary),
                    icon = Icons.Default.Person,
                    onClick = onOpenCallsignDialog
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_ssid),
                    value = "-$ssid",
                    summary = stringResource(R.string.p_ssid_summary),
                    icon = Icons.Default.Radio,
                    onClick = { showSsidDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceValueItem(
                    title = stringResource(R.string.p_aprs_path),
                    value = digiPath.ifEmpty { "DIRECT (直发)" },
                    summary = stringResource(R.string.p_aprs_path_summary),
                    icon = Icons.Default.Route,
                    onClick = { showDigiPathDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.p_symbol),
                    summary = stringResource(R.string.p_symbol_summary) + ": " + symbol,
                    icon = Icons.Default.LocationOn,
                    onClick = onOpenSymbolPicker
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 连接与通信模式
            PreferenceCategoryHeader(title = stringResource(R.string.p__connection))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.p_connsetup),
                    summary = backendName,
                    icon = Icons.Default.CellTower,
                    onClick = onOpenConnectionSetup
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 定位与信标
            PreferenceCategoryHeader(title = stringResource(R.string.p__position))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.p_locsource),
                    summary = locationSourceName,
                    icon = Icons.Default.LocationOn,
                    onClick = onOpenLocationSetup
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_send_battery),
                    summary = stringResource(R.string.setting_send_battery_summary),
                    icon = Icons.Default.BatteryFull,
                    checked = sendBatteryInfo,
                    onCheckedChange = onToggleSendBatteryInfo
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 地图与显示
            PreferenceCategoryHeader(title = stringResource(R.string.app_map))
            PreferenceGroupCard {
                PreferenceItem(
                    title = stringResource(R.string.p_map_source),
                    summary = mapModeTitle,
                    icon = Icons.Default.Map,
                    onClick = onOpenMapModeSetup
                )
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
                    onClick = { showStationTapDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_show_aprs_objects),
                    summary = stringResource(R.string.setting_show_aprs_objects_summary),
                    icon = Icons.Default.Layers,
                    checked = showObjects,
                    onCheckedChange = onToggleShowObjects
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 更多与诊断
            PreferenceCategoryHeader(title = "应用支持与关于")
            PreferenceGroupCard {
                PreferenceItem(
                    title = "分享系统诊断与运行日志",
                    summary = "导出网络连接、射频链路与数据库运行报告",
                    icon = Icons.Default.BugReport,
                    onClick = onShareDiagnostics,
                    showChevron = false
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "检查更新",
                    summary = "仅在点击时检查 GitHub Releases，不会后台自动检查",
                    icon = Icons.Default.SystemUpdateAlt,
                    onClick = onCheckForUpdates,
                    showChevron = false,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = stringResource(R.string.about),
                    summary = "版本 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.BUILD_TYPE + ")",
                    icon = Icons.Default.Info,
                    onClick = onOpenAbout
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSsidDialog) {
        PreferenceSelectDialog(
            title = stringResource(R.string.p_ssid_entry),
            options = ssidOptions,
            selected = ssid,
            onDismiss = { showSsidDialog = false },
            onSelect = { newSsid ->
                onSaveSsid(newSsid)
            }
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
            }
        )
    }

    if (showDigiPathDialog) {
        DigiPathDialogCompose(
            currentPath = digiPath,
            userPresets = userDigiPresets,
            onSavePath = { newPath ->
                onSaveDigiPath(newPath)
            },
            onAddPreset = { newPreset ->
                onAddDigiPreset(newPreset)
            },
            onDeletePreset = { preset ->
                onDeleteDigiPreset(preset)
            },
            onDismiss = { showDigiPathDialog = false }
        )
    }
}
