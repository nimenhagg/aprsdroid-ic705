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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
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
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem

private const val APRSLOCUS_URL = "https://github.com/dariondong/APRSLocus"
private const val APRSDROID_URL = "https://github.com/ge0rg/aprsdroid"
private const val GRAYWOLF_URL = "https://github.com/chrissnell/graywolf"
private const val DIRE_WOLF_URL = "https://github.com/wb2osz/direwolf"
private const val MAPLIBRE_URL = "https://github.com/maplibre/maplibre-native"
private const val OKHTTP_URL = "https://github.com/square/okhttp"
private const val ANDROIDX_URL = "https://github.com/androidx/androidx"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsAndLinksScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.credits_links_title),
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

            PreferenceCategoryHeader(title = stringResource(R.string.credits_related_apps))
            PreferenceGroupCard {
                PreferenceItem(
                    title = "APRSLocus",
                    summary = stringResource(R.string.credits_aprslocus_summary),
                    icon = Icons.Default.Apps,
                    onClick = { onOpenUrl(APRSLOCUS_URL) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreferenceCategoryHeader(title = stringResource(R.string.credits_open_source))
            PreferenceGroupCard {
                PreferenceItem(
                    title = "APRSdroid",
                    summary = stringResource(R.string.credits_aprsdroid_summary),
                    icon = Icons.Default.Radio,
                    onClick = { onOpenUrl(APRSDROID_URL) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "Graywolf",
                    summary = stringResource(R.string.credits_graywolf_summary),
                    icon = Icons.Default.Radio,
                    onClick = { onOpenUrl(GRAYWOLF_URL) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "Dire Wolf",
                    summary = stringResource(R.string.credits_direwolf_summary),
                    icon = Icons.Default.Code,
                    onClick = { onOpenUrl(DIRE_WOLF_URL) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "MapLibre Native",
                    summary = stringResource(R.string.credits_maplibre_summary),
                    icon = Icons.Default.Map,
                    onClick = { onOpenUrl(MAPLIBRE_URL) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "OkHttp",
                    summary = stringResource(R.string.credits_okhttp_summary),
                    icon = Icons.Default.Http,
                    onClick = { onOpenUrl(OKHTTP_URL) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PreferenceItem(
                    title = "AndroidX / Jetpack Compose",
                    summary = stringResource(R.string.credits_androidx_summary),
                    icon = Icons.Default.Code,
                    onClick = { onOpenUrl(ANDROIDX_URL) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
