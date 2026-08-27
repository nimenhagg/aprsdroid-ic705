package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
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
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.theme.AprsTheme

class NotificationPrefs : ComponentActivity() {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private var pendingRingtoneKey: String? = null

    private val notifyLedState = mutableStateOf(true)
    private val notifyVibrState = mutableStateOf(true)
    private val notifyRingtoneState = mutableStateOf("")

    private val posNotifyLedState = mutableStateOf(false)
    private val posNotifyVibrState = mutableStateOf(false)
    private val posNotifyRingtoneState = mutableStateOf("")

    private val dgpNotifyLedState = mutableStateOf(false)
    private val dgpNotifyVibrState = mutableStateOf(false)
    private val dgpNotifyRingtoneState = mutableStateOf("")

    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            }
            pendingRingtoneKey?.let { key ->
                prefs.set(key, uri?.toString().orEmpty())
                refreshState()
            }
        }
        pendingRingtoneKey = null
    }

    private fun pickRingtone(key: String) {
        pendingRingtoneKey = key
        val currentUri = prefs.getString(key, "")
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
            if (currentUri.isNotEmpty()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri.toUri())
            }
        }
        ringtonePicker.launch(intent)
    }

    private fun getRingtoneTitle(uriString: String): String {
        if (uriString.isEmpty()) return getString(R.string.setting_notification_sound_default)
        return try {
            RingtoneManager.getRingtone(this, uriString.toUri())?.getTitle(this) ?: uriString
        } catch (_: Exception) {
            uriString
        }
    }

    private fun refreshState() {
        notifyLedState.value = prefs.getBoolean("notify_led", true)
        notifyVibrState.value = prefs.getBoolean("notify_vibr", true)
        notifyRingtoneState.value = getRingtoneTitle(prefs.getString("notify_ringtone", ""))

        posNotifyLedState.value = prefs.getBoolean("pos_notify_led", false)
        posNotifyVibrState.value = prefs.getBoolean("pos_notify_vibr", false)
        posNotifyRingtoneState.value = getRingtoneTitle(prefs.getString("pos_notify_ringtone", ""))

        dgpNotifyLedState.value = prefs.getBoolean("dgp_notify_led", false)
        dgpNotifyVibrState.value = prefs.getBoolean("dgp_notify_vibr", false)
        dgpNotifyRingtoneState.value = getRingtoneTitle(prefs.getString("dgp_notify_ringtone", ""))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()

        setContent {
            AprsTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.p__notification),
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
                        NotificationGroup(
                            title = stringResource(R.string.setting_notification_messages),
                            led = notifyLedState.value,
                            vibration = notifyVibrState.value,
                            sound = notifyRingtoneState.value,
                            onLedChanged = { checked ->
                                notifyLedState.value = checked
                                prefs.set("notify_led", checked)
                            },
                            onVibrationChanged = { checked ->
                                notifyVibrState.value = checked
                                prefs.set("notify_vibr", checked)
                            },
                            onSoundClick = { pickRingtone("notify_ringtone") },
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        NotificationGroup(
                            title = stringResource(R.string.setting_notification_position),
                            led = posNotifyLedState.value,
                            vibration = posNotifyVibrState.value,
                            sound = posNotifyRingtoneState.value,
                            onLedChanged = { checked ->
                                posNotifyLedState.value = checked
                                prefs.set("pos_notify_led", checked)
                            },
                            onVibrationChanged = { checked ->
                                posNotifyVibrState.value = checked
                                prefs.set("pos_notify_vibr", checked)
                            },
                            onSoundClick = { pickRingtone("pos_notify_ringtone") },
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        NotificationGroup(
                            title = stringResource(R.string.setting_notification_digipeated),
                            led = dgpNotifyLedState.value,
                            vibration = dgpNotifyVibrState.value,
                            sound = dgpNotifyRingtoneState.value,
                            onLedChanged = { checked ->
                                dgpNotifyLedState.value = checked
                                prefs.set("dgp_notify_led", checked)
                            },
                            onVibrationChanged = { checked ->
                                dgpNotifyVibrState.value = checked
                                prefs.set("dgp_notify_vibr", checked)
                            },
                            onSoundClick = { pickRingtone("dgp_notify_ringtone") },
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NotificationGroup(
    title: String,
    led: Boolean,
    vibration: Boolean,
    sound: String,
    onLedChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onSoundClick: () -> Unit,
) {
    PreferenceCategoryHeader(title = title)
    PreferenceGroupCard {
        PreferenceSwitchItem(
            title = stringResource(R.string.setting_notification_led),
            summary = stringResource(
                if (led) R.string.setting_notification_led_on else R.string.setting_notification_led_off,
            ),
            icon = Icons.Default.Highlight,
            checked = led,
            onCheckedChange = onLedChanged,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        PreferenceSwitchItem(
            title = stringResource(R.string.setting_notification_vibration),
            summary = stringResource(
                if (vibration) R.string.setting_notification_vibration_on else R.string.setting_notification_vibration_off,
            ),
            icon = Icons.Default.Vibration,
            checked = vibration,
            onCheckedChange = onVibrationChanged,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        PreferenceItem(
            title = stringResource(R.string.setting_notification_sound),
            summary = sound,
            icon = Icons.Default.NotificationsActive,
            onClick = onSoundClick,
        )
    }
}
