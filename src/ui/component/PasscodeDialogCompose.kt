package org.aprsdroid.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.aprsdroid.app.AprsPacket
import org.aprsdroid.app.R

@Composable
fun PasscodeDialogCompose(
    initialCallsign: String,
    initialPasscode: String,
    firstRun: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (callsign: String, passcode: String) -> Unit,
) {
    var rawCall by remember {
        mutableStateOf(initialCallsign.filter { it.isLetterOrDigit() }.uppercase(Locale.US))
    }
    var passcode by remember { mutableStateOf(initialPasscode) }
    val focusManager = LocalFocusManager.current

    val trimmedCall = rawCall.trim().uppercase(Locale.US)
    val isCallValid = trimmedCall.length in 3..7 && trimmedCall.matches(Regex("^[0-9A-Z]{3,7}$"))
    val isPassValid = passcode.isEmpty() || AprsPacket.passcodeAllowed(trimmedCall, passcode, true)
    val canSave = isCallValid && isPassValid

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(
                        if (firstRun) R.string.identity_first_run_title else R.string.identity_dialog_title,
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (firstRun) {
                    Text(
                        text = stringResource(R.string.identity_first_run_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = rawCall,
                    onValueChange = { input ->
                        rawCall = input
                            .filter { it.isLetterOrDigit() }
                            .take(7)
                            .uppercase(Locale.US)
                    },
                    label = { Text(stringResource(R.string.identity_callsign)) },
                    placeholder = { Text("N0CALL") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    isError = rawCall.isNotEmpty() && !isCallValid,
                    supportingText = if (rawCall.isNotEmpty() && !isCallValid) {
                        { Text(stringResource(R.string.identity_callsign_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.trim() },
                    label = { Text(stringResource(R.string.identity_passcode)) },
                    placeholder = { Text(stringResource(R.string.identity_passcode_optional)) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null)
                    },
                    singleLine = true,
                    isError = passcode.isNotEmpty() && !isPassValid,
                    supportingText = {
                        Text(
                            stringResource(
                                if (passcode.isNotEmpty() && !isPassValid) {
                                    R.string.identity_passcode_invalid
                                } else {
                                    R.string.identity_passcode_hint
                                },
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSave) onSave(trimmedCall, passcode)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSave) onSave(trimmedCall, passcode)
                },
                enabled = canSave,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
