package org.aprsdroid.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.aprsdroid.app.R
import org.aprsdroid.app.aprs.AprsPhg

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhgDialogCompose(
    initialEnabled: Boolean,
    initialPower: String,
    initialHeight: String,
    initialHeightUnit: String,
    initialGain: String,
    initialDir: String,
    onDismiss: () -> Unit,
    onSave: (
        enabled: Boolean,
        power: String,
        height: String,
        heightUnit: String,
        gain: String,
        dir: String,
    ) -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var powerText by rememberSaveable { mutableStateOf(initialPower) }
    var heightText by rememberSaveable { mutableStateOf(initialHeight) }
    var heightUnit by rememberSaveable {
        mutableStateOf(if (initialHeightUnit.equals("ft", ignoreCase = true)) "ft" else "m")
    }
    var gainText by rememberSaveable { mutableStateOf(initialGain) }
    var dirCode by rememberSaveable {
        mutableIntStateOf(initialDir.toIntOrNull()?.coerceIn(0, 8) ?: 0)
    }
    var dirExpanded by remember { mutableStateOf(false) }

    val dirOptions = listOf(
        0 to stringResource(R.string.phg_dir_omni),
        1 to stringResource(R.string.phg_dir_45),
        2 to stringResource(R.string.phg_dir_90),
        3 to stringResource(R.string.phg_dir_135),
        4 to stringResource(R.string.phg_dir_180),
        5 to stringResource(R.string.phg_dir_225),
        6 to stringResource(R.string.phg_dir_270),
        7 to stringResource(R.string.phg_dir_315),
        8 to stringResource(R.string.phg_dir_360),
    )

    val powerDouble = powerText.toDoubleOrNull()?.takeIf { it >= 0 }
    val heightDouble = heightText.toDoubleOrNull()?.takeIf { it >= 0 }
    val heightFeet = heightDouble?.let { if (heightUnit == "m") AprsPhg.m2ft(it) else it }
    val gainDouble = gainText.toDoubleOrNull()?.takeIf { it >= 0 }

    val encodedPhg = if (enabled) {
        AprsPhg.encode(
            powerWatts = powerDouble,
            heightFeet = heightFeet,
            gainDb = gainDouble,
            directivityDeg = AprsPhg.DIRECTIVITY_ANGLES[dirCode],
            isOmni = (dirCode == 0),
        )
    } else null
    val decodedPhg = encodedPhg?.let { AprsPhg.decode(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.phg_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Toggle row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enabled = !enabled }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.phg_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                }

                if (enabled) {
                    HorizontalDivider()

                    // Power input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = powerText,
                            onValueChange = { input ->
                                powerText = input.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text(stringResource(R.string.phg_power_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Quick power preset chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("5", "10", "25", "50").forEach { p ->
                                SuggestionChip(
                                    onClick = { powerText = p },
                                    label = { Text("${p}W") },
                                )
                            }
                        }

                        if (powerDouble != null) {
                            val pCode = AprsPhg.powerToCode(powerDouble)
                            val pWatts = AprsPhg.POWER_STEPS[pCode]
                            Text(
                                text = "APRS 101: ${pWatts}W (p=$pCode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Antenna height input (HAAT)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { input ->
                                heightText = input.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text(stringResource(R.string.phg_height_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Unit selector
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = heightUnit == "m",
                                onClick = { heightUnit = "m" },
                                label = { Text(stringResource(R.string.phg_unit_m)) },
                            )
                            FilterChip(
                                selected = heightUnit == "ft",
                                onClick = { heightUnit = "ft" },
                                label = { Text(stringResource(R.string.phg_unit_ft)) },
                            )
                        }

                        Text(
                            text = stringResource(R.string.phg_height_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (heightFeet != null) {
                            val hCode = AprsPhg.heightToCode(heightFeet)
                            val hFeet = AprsPhg.HEIGHT_STEPS_FEET[hCode]
                            val hMeters = (hFeet * 0.3048).roundToInt()
                            Text(
                                text = "APRS 101: ${hFeet}ft (${hMeters}m, h=$hCode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Antenna gain input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = gainText,
                            onValueChange = { input ->
                                gainText = input.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text(stringResource(R.string.phg_gain_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Quick gain chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("0", "3", "6", "9").forEach { g ->
                                SuggestionChip(
                                    onClick = { gainText = g },
                                    label = { Text("${g} dB") },
                                )
                            }
                        }

                        if (gainDouble != null) {
                            val gCode = AprsPhg.gainToCode(gainDouble)
                            Text(
                                text = "APRS 101: ${gCode}dB (g=$gCode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Directivity dropdown
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = dirExpanded,
                            onExpandedChange = { dirExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = dirOptions.firstOrNull { it.first == dirCode }?.second.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.phg_dir_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dirExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = dirExpanded,
                                onDismissRequest = { dirExpanded = false },
                            ) {
                                dirOptions.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            dirCode = code
                                            dirExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Live preview card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.phg_preview_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            if (decodedPhg != null) {
                                Text(
                                    text = decodedPhg.rawCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = AprsPhg.formatDescription(decodedPhg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.setting_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Text(
                                text = stringResource(R.string.phg_preview_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        enabled,
                        powerText.trim(),
                        heightText.trim(),
                        heightUnit,
                        gainText.trim(),
                        dirCode.toString(),
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
