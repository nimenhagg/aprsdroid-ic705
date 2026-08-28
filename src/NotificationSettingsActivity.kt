package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.screen.NotificationSettingsScreen
import org.aprsdroid.app.ui.theme.AprsTheme

class NotificationSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                NotificationSettingsScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenChannelSettings = ::openChannelSettings,
                )
            }
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
