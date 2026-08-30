package org.aprsdroid.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import org.aprsdroid.app.notification.LiveUpdates
import org.aprsdroid.app.ui.screen.NotificationSettingsScreen
import org.aprsdroid.app.ui.theme.AprsTheme

class NotificationSettingsActivity : ComponentActivity() {

    private val liveUpdatesEnabledState = mutableStateOf(false)
    private val liveUpdatesPermissionDialogVisible = mutableStateOf(false)
    private var pendingLiveUpdatesEnable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                NotificationSettingsScreen(
                    showLiveUpdates = Build.VERSION.SDK_INT >= 36,
                    liveUpdatesEnabled = liveUpdatesEnabledState.value,
                    onToggleLiveUpdates = ::handleLiveUpdatesToggle,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenChannelSettings = ::openChannelSettings,
                )

                if (liveUpdatesPermissionDialogVisible.value) {
                    AlertDialog(
                        onDismissRequest = {
                            liveUpdatesPermissionDialogVisible.value = false
                        },
                        title = {
                            Text(stringResource(R.string.setting_notification_live_updates_permission_title))
                        },
                        text = {
                            Text(stringResource(R.string.setting_notification_live_updates_permission_message))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    liveUpdatesPermissionDialogVisible.value = false
                                    if (Build.VERSION.SDK_INT >= 36) {
                                        openLiveUpdatesPermissionSettings()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.setting_notification_live_updates_permission_open))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    liveUpdatesPermissionDialogVisible.value = false
                                },
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncLiveUpdatesState()
    }

    private fun handleLiveUpdatesToggle(enabled: Boolean) {
        if (!enabled) {
            setLiveUpdatesEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT < 36) return

        if (LiveUpdates.canPost(this)) {
            setLiveUpdatesEnabled(true)
        } else {
            liveUpdatesPermissionDialogVisible.value = true
        }
    }

    private fun syncLiveUpdatesState() {
        if (Build.VERSION.SDK_INT < 36) {
            liveUpdatesEnabledState.value = false
            pendingLiveUpdatesEnable = false
            return
        }

        if (pendingLiveUpdatesEnable) {
            pendingLiveUpdatesEnable = false
            if (LiveUpdates.canPost(this)) {
                setLiveUpdatesEnabled(true)
                return
            }
        }

        val storedEnabled = LiveUpdates.isEnabled(this)
        if (storedEnabled && !LiveUpdates.canPost(this)) {
            LiveUpdates.setEnabled(this, false)
            liveUpdatesEnabledState.value = false
            ServiceNotifier.instance.refresh(this)
        } else {
            liveUpdatesEnabledState.value = storedEnabled
        }
    }

    private fun setLiveUpdatesEnabled(enabled: Boolean) {
        LiveUpdates.setEnabled(this, enabled)
        liveUpdatesEnabledState.value = enabled
        ServiceNotifier.instance.refresh(this)
    }

    @RequiresApi(36)
    private fun openLiveUpdatesPermissionSettings() {
        val promotionSettings = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        pendingLiveUpdatesEnable = true
        try {
            startActivity(promotionSettings)
            return
        } catch (_: ActivityNotFoundException) {
            pendingLiveUpdatesEnable = false
        }

        try {
            pendingLiveUpdatesEnable = true
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                },
            )
        } catch (_: ActivityNotFoundException) {
            pendingLiveUpdatesEnable = false
            Toast.makeText(
                this,
                R.string.setting_notification_live_updates_settings_unavailable,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openChannelSettings(channelId: String) {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            },
        )
    }
}
