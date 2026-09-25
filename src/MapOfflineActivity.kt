package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.aprsdroid.app.map.MapLibreOfflineManager
import org.aprsdroid.app.ui.theme.AprsTheme
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus

class MapOfflineActivity : ComponentActivity() {
    companion object {
        const val EXTRA_WEST = "offline_west"
        const val EXTRA_SOUTH = "offline_south"
        const val EXTRA_EAST = "offline_east"
        const val EXTRA_NORTH = "offline_north"
        const val EXTRA_MIN_ZOOM = "offline_min_zoom"
        const val EXTRA_MAX_ZOOM = "offline_max_zoom"
        const val EXTRA_TILE_URLS = "offline_tile_urls"
        const val EXTRA_SOURCE_MAX_ZOOM = "offline_source_max_zoom"
        const val EXTRA_ATTRIBUTION = "offline_attribution"
        const val EXTRA_NAME = "offline_name"
    }

    private val regions = mutableStateListOf<OfflineRegion>()
    private val statuses = mutableMapOf<Long, OfflineRegionStatus>()
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshRegions()
        setContent {
            AprsTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(getString(R.string.map_offline_title)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getString(R.string.action_back))
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (canDownloadCurrentView()) {
                            Button(onClick = ::downloadCurrentView, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Text(getString(R.string.map_offline_download_current), modifier = Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Text(getString(R.string.map_offline_custom_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(regions, key = { it.id }) { region ->
                                val metadata = MapLibreOfflineManager.parseMetadata(region.metadata)
                                val status = statuses[region.id]
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(metadata?.name ?: getString(R.string.map_offline_region), style = MaterialTheme.typography.titleMedium)
                                            Text(statusText(status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { deleteRegion(region) }) {
                                            Icon(Icons.Default.Delete, contentDescription = getString(R.string.action_clear))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            while (!isFinishing && !isDestroyed) {
                refreshStatuses()
                delay(1000)
            }
        }
    }

    private fun canDownloadCurrentView(): Boolean =
        intent.getStringArrayExtra(EXTRA_TILE_URLS)?.isNotEmpty() == true

    private fun downloadCurrentView() {
        val tileUrls = intent.getStringArrayExtra(EXTRA_TILE_URLS) ?: return
        val bounds = LatLngBounds.from(
            LatLng(intent.getDoubleExtra(EXTRA_SOUTH, 0.0), intent.getDoubleExtra(EXTRA_WEST, 0.0)),
            LatLng(intent.getDoubleExtra(EXTRA_NORTH, 0.0), intent.getDoubleExtra(EXTRA_EAST, 0.0))
        )
        MapLibreOfflineManager.createRegion(
            this,
            intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.map_offline_current_area),
            bounds,
            intent.getDoubleExtra(EXTRA_MIN_ZOOM, 8.0),
            intent.getDoubleExtra(EXTRA_MAX_ZOOM, 15.0),
            tileUrls,
            intent.getFloatExtra(EXTRA_SOURCE_MAX_ZOOM, 19f),
            intent.getStringExtra(EXTRA_ATTRIBUTION)
        ) { _, error ->
            runOnUiThread {
                errorMessage = error
                refreshRegions()
            }
        }
    }

    private fun refreshRegions() {
        MapLibreOfflineManager.listRegions(this) { result, error ->
            runOnUiThread {
                errorMessage = error
                regions.clear()
                result?.let(regions::addAll)
                refreshStatuses()
            }
        }
    }

    private fun refreshStatuses() {
        regions.toList().forEach { region ->
            MapLibreOfflineManager.getStatus(this, region) { status, _ ->
                if (status != null) statuses[region.id] = status
            }
        }
    }

    private fun deleteRegion(region: OfflineRegion) {
        MapLibreOfflineManager.deleteRegion(this, region) { error ->
            runOnUiThread {
                errorMessage = error
                if (error == null) {
                    statuses.remove(region.id)
                    regions.removeAll { it.id == region.id }
                }
            }
        }
    }

    private fun statusText(status: OfflineRegionStatus?): String {
        if (status == null) return getString(R.string.map_offline_status_loading)
        if (status.isComplete) {
            return getString(R.string.map_offline_status_complete, status.completedTileCount, formatBytes(status.completedResourceSize))
        }
        return getString(R.string.map_offline_status_progress, status.completedResourceCount, status.requiredResourceCount)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
