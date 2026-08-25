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
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            pendingRingtoneKey?.let { key ->
                val uriString = uri?.toString().orEmpty()
                prefs.set(key, uriString)
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
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            if (currentUri.isNotEmpty()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri.toUri())
            }
        }
        ringtonePicker.launch(intent)
    }

    private fun getRingtoneTitle(uriString: String): String {
        if (uriString.isEmpty()) return getString(R.string.p_msg_ring_summary)
        return try {
            val ringtone = RingtoneManager.getRingtone(this, uriString.toUri())
            ringtone?.getTitle(this) ?: uriString
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

                        // 1. 短消息通知
                        PreferenceCategoryHeader(title = stringResource(R.string.p_msg))
                        PreferenceGroupCard {
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_led),
                                summary = if (notifyLedState.value) stringResource(R.string.p_msg_led_on) else stringResource(R.string.p_msg_led_off),
                                icon = Icons.Default.Highlight,
                                checked = notifyLedState.value,
                                onCheckedChange = { checked ->
                                    notifyLedState.value = checked
                                    prefs.set("notify_led", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_vibr),
                                summary = if (notifyVibrState.value) stringResource(R.string.p_msg_vibr_on) else stringResource(R.string.p_msg_vibr_off),
                                icon = Icons.Default.Vibration,
                                checked = notifyVibrState.value,
                                onCheckedChange = { checked ->
                                    notifyVibrState.value = checked
                                    prefs.set("notify_vibr", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceItem(
                                title = stringResource(R.string.p_msg_ring),
                                summary = notifyRingtoneState.value,
                                icon = Icons.Default.NotificationsActive,
                                onClick = { pickRingtone("notify_ringtone") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 位置数据包通知
                        PreferenceCategoryHeader(title = stringResource(R.string.p_pos))
                        PreferenceGroupCard {
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_led),
                                summary = if (posNotifyLedState.value) stringResource(R.string.p_msg_led_on) else stringResource(R.string.p_msg_led_off),
                                icon = Icons.Default.Highlight,
                                checked = posNotifyLedState.value,
                                onCheckedChange = { checked ->
                                    posNotifyLedState.value = checked
                                    prefs.set("pos_notify_led", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_vibr),
                                summary = if (posNotifyVibrState.value) stringResource(R.string.p_msg_vibr_on) else stringResource(R.string.p_msg_vibr_off),
                                icon = Icons.Default.Vibration,
                                checked = posNotifyVibrState.value,
                                onCheckedChange = { checked ->
                                    posNotifyVibrState.value = checked
                                    prefs.set("pos_notify_vibr", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceItem(
                                title = stringResource(R.string.p_pos_ring),
                                summary = posNotifyRingtoneState.value,
                                icon = Icons.Default.NotificationsActive,
                                onClick = { pickRingtone("pos_notify_ringtone") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. 中继包通知
                        PreferenceCategoryHeader(title = stringResource(R.string.p_dgp))
                        PreferenceGroupCard {
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_led),
                                summary = if (dgpNotifyLedState.value) stringResource(R.string.p_msg_led_on) else stringResource(R.string.p_msg_led_off),
                                icon = Icons.Default.Highlight,
                                checked = dgpNotifyLedState.value,
                                onCheckedChange = { checked ->
                                    dgpNotifyLedState.value = checked
                                    prefs.set("dgp_notify_led", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_msg_vibr),
                                summary = if (dgpNotifyVibrState.value) stringResource(R.string.p_msg_vibr_on) else stringResource(R.string.p_msg_vibr_off),
                                icon = Icons.Default.Vibration,
                                checked = dgpNotifyVibrState.value,
                                onCheckedChange = { checked ->
                                    dgpNotifyVibrState.value = checked
                                    prefs.set("dgp_notify_vibr", checked)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceItem(
                                title = stringResource(R.string.p_dgp_ring),
                                summary = dgpNotifyRingtoneState.value,
                                icon = Icons.Default.NotificationsActive,
                                onClick = { pickRingtone("dgp_notify_ringtone") }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
