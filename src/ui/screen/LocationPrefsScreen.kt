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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.R
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.viewmodel.LocationDialog
import org.aprsdroid.app.ui.viewmodel.LocationPrefsIntent
import org.aprsdroid.app.ui.viewmodel.LocationPrefsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPrefsScreen(
    uiState: LocationPrefsUiState,
    onIntent: (LocationPrefsIntent) -> Unit,
    onBack: () -> Unit,
    onPickOnMap: () -> Unit,
    onUseLastLocation: () -> Unit,
) {
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
                    IconButton(onClick = onBack) {
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
                val currentSourceTitle = when (uiState.locSource) {
                    "smartbeaconing" -> stringResource(R.string.setting_location_source_smart_value)
                    "periodic" -> stringResource(R.string.setting_location_source_periodic_value)
                    "manual" -> stringResource(R.string.setting_location_source_manual_value)
                    else -> uiState.locSource
                }
                PreferenceValueItem(
                    title = stringResource(R.string.setting_location_source),
                    value = currentSourceTitle,
                    summary = stringResource(R.string.setting_location_source_summary),
                    icon = Icons.Default.LocationOn,
                    onClick = { onIntent(LocationPrefsIntent.OpenLocationSourceDialog) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.locSource) {
                "smartbeaconing" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_smartbeacon_category))
                    PreferenceGroupCard {
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_sb_fast_speed),
                            value = "${uiState.sbFastSpeed} km/h",
                            summary = stringResource(R.string.setting_sb_fast_speed_summary),
                            icon = Icons.Default.Speed,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.fastspeed")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_sb_fast_rate),
                            value = "${uiState.sbFastRate} s",
                            summary = stringResource(R.string.setting_sb_fast_rate_summary),
                            icon = Icons.Default.Timer,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.fastrate")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_sb_slow_speed),
                            value = "${uiState.sbSlowSpeed} km/h",
                            summary = stringResource(R.string.setting_sb_slow_speed_summary),
                            icon = Icons.Default.Speed,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.slowspeed")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_sb_slow_rate),
                            value = "${uiState.sbSlowRate} s",
                            summary = stringResource(R.string.setting_sb_slow_rate_summary),
                            icon = Icons.Default.Timer,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.slowrate")) },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_turn_beacon_category))
                    PreferenceGroupCard {
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_turn_time),
                            value = "${uiState.sbTurnTime} s",
                            summary = stringResource(R.string.setting_turn_time_summary),
                            icon = Icons.Default.Timer,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.turntime")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_turn_angle),
                            value = "${uiState.sbTurnMin}°",
                            summary = stringResource(R.string.setting_turn_angle_summary),
                            icon = Icons.Default.TurnRight,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.turnmin")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_turn_slope),
                            value = uiState.sbTurnSlope,
                            summary = stringResource(R.string.setting_turn_slope_summary),
                            icon = Icons.Default.Speed,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("sb.turnslope")) },
                        )
                    }
                }

                "periodic" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_periodic_gps_category))
                    PreferenceGroupCard {
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_time_interval),
                            value = "${uiState.interval} min",
                            summary = stringResource(R.string.setting_time_interval_summary),
                            icon = Icons.Default.Timer,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("interval")) },
                        )
                    }
                }

                "manual" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_manual_position_category))
                    PreferenceGroupCard {
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_latitude),
                            value = uiState.manualLat,
                            summary = stringResource(R.string.setting_decimal_degrees),
                            icon = Icons.Default.LocationOn,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("manual_lat")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_longitude),
                            value = uiState.manualLon,
                            summary = stringResource(R.string.setting_decimal_degrees),
                            icon = Icons.Default.LocationOn,
                            onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("manual_lon")) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceItem(
                            title = stringResource(R.string.setting_pick_on_map),
                            summary = stringResource(R.string.setting_pick_on_map_summary),
                            icon = Icons.Default.Map,
                            onClick = onPickOnMap,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceItem(
                            title = stringResource(R.string.setting_use_last_location),
                            summary = stringResource(R.string.setting_use_last_location_summary),
                            icon = Icons.Default.AddLocation,
                            onClick = onUseLastLocation,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceSwitchItem(
                            title = stringResource(R.string.setting_repeat_manual_position),
                            summary = stringResource(R.string.setting_repeat_manual_position_summary),
                            icon = Icons.Default.Timer,
                            checked = uiState.periodicPosition,
                            onCheckedChange = { checked ->
                                onIntent(LocationPrefsIntent.SetPeriodicPosition(checked))
                            },
                        )
                        if (uiState.periodicPosition) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceValueItem(
                                title = stringResource(R.string.setting_time_interval),
                                value = "${uiState.interval} min",
                                summary = stringResource(R.string.setting_time_interval_summary),
                                icon = Icons.Default.Timer,
                                onClick = { onIntent(LocationPrefsIntent.OpenEditDialog("interval")) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.setting_location_privacy_category))
            PreferenceGroupCard {
                val currentAmbiguityLabel = ambiguityOptions
                    .firstOrNull { it.first == uiState.ambiguity }
                    ?.second ?: uiState.ambiguity
                PreferenceValueItem(
                    title = stringResource(R.string.setting_position_ambiguity),
                    value = currentAmbiguityLabel,
                    summary = stringResource(R.string.setting_position_ambiguity_summary),
                    icon = Icons.Default.BlurOn,
                    onClick = { onIntent(LocationPrefsIntent.OpenAmbiguityDialog) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_include_speed_course),
                    summary = stringResource(R.string.setting_include_speed_course_summary),
                    icon = Icons.Default.Speed,
                    checked = uiState.showSpdBear,
                    onCheckedChange = { checked ->
                        onIntent(LocationPrefsIntent.SetShowSpdBear(checked))
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceSwitchItem(
                    title = stringResource(R.string.setting_include_altitude),
                    summary = stringResource(R.string.setting_include_altitude_summary),
                    icon = Icons.Default.Height,
                    checked = uiState.showAltitude,
                    onCheckedChange = { checked ->
                        onIntent(LocationPrefsIntent.SetShowAltitude(checked))
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        when (val dialog = uiState.activeDialog) {
            is LocationDialog.LocationSource -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_location_source),
                    options = locSourceOptions,
                    selected = uiState.locSource,
                    onDismiss = { onIntent(LocationPrefsIntent.DismissDialog) },
                    onSelect = { selected ->
                        onIntent(LocationPrefsIntent.SetLocationSource(selected))
                    },
                )
            }
            is LocationDialog.Ambiguity -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_position_ambiguity),
                    options = ambiguityOptions,
                    selected = uiState.ambiguity,
                    onDismiss = { onIntent(LocationPrefsIntent.DismissDialog) },
                    onSelect = { selected ->
                        onIntent(LocationPrefsIntent.SetAmbiguity(selected))
                    },
                )
            }
            is LocationDialog.EditField -> {
                PreferenceEditDialog(
                    title = stringResource(R.string.setting_edit_value),
                    initialValue = dialog.currentValue,
                    onDismiss = { onIntent(LocationPrefsIntent.DismissDialog) },
                    onSave = { newValue ->
                        onIntent(LocationPrefsIntent.SaveFieldValue(dialog.key, newValue))
                    },
                )
            }
            null -> Unit
        }
    }
}
