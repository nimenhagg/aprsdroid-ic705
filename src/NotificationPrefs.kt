package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.theme.AprsTheme

class NotificationPrefs : ComponentActivity() {

    private fun openChannelSettings(channelId: String) {
        ServiceNotifier.instance.setupChannels(this)
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceNotifier.instance.setupChannels(this)

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
                        PreferenceCategoryHeader(
                            title = stringResource(R.string.setting_notification_channels_category),
                        )
                        PreferenceGroupCard {
                            PreferenceItem(
                                title = stringResource(R.string.setting_notification_messages_channel),
                                summary = stringResource(R.string.setting_notification_messages_channel_summary),
                                icon = Icons.Default.NotificationsActive,
                                onClick = { openChannelSettings("msg") },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceItem(
                                title = stringResource(R.string.setting_notification_status_channel),
                                summary = stringResource(R.string.setting_notification_status_channel_summary),
                                icon = Icons.Default.NotificationsActive,
                                onClick = { openChannelSettings("status") },
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
