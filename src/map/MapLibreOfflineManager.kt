package org.aprsdroid.app.map

import android.content.Context
import android.net.Uri
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import java.util.UUID

object MapLibreOfflineManager {
    private const val STYLE_DIR = "offline-map-styles"

    data class RegionMetadata(val name: String, val styleFile: String?)

    fun createRegion(
        context: Context,
        name: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        tileUrls: Array<String>,
        sourceMaxZoom: Float,
        attribution: String?,
        callback: (OfflineRegion?, String?) -> Unit
    ) {
        if (tileUrls.isEmpty()) {
            callback(null, "No tile source is configured")
            return
        }
        val styleDir = File(context.filesDir, STYLE_DIR).apply { mkdirs() }
        val styleFile = File(styleDir, "style-${UUID.randomUUID()}.json")
        try {
            styleFile.writeText(MapLibreOfflineStyle.build(tileUrls, sourceMaxZoom, attribution))
        } catch (error: Exception) {
            callback(null, "Unable to create offline style: ${error.message}")
            return
        }
        val metadata = """{"name":${org.json.JSONObject.quote(name)},"styleFile":${org.json.JSONObject.quote(styleFile.absolutePath)}}"""
        val definition = OfflineTilePyramidRegionDefinition(
            Uri.fromFile(styleFile).toString(), bounds, minZoom, maxZoom, 1.0f, false
        )
        OfflineManager.getInstance(context).createOfflineRegion(
            definition, metadata.toByteArray(Charsets.UTF_8),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    callback(offlineRegion, null)
                }
                override fun onError(error: String) {
                    styleFile.delete()
                    callback(null, error)
                }
            }
        )
    }

    fun listRegions(context: Context, callback: (Array<OfflineRegion>?, String?) -> Unit) {
        OfflineManager.getInstance(context).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) { callback(offlineRegions, null) }
                override fun onError(error: String) { callback(null, error) }
            }
        )
    }

    fun getStatus(context: Context, region: OfflineRegion, callback: (OfflineRegionStatus?, String?) -> Unit) {
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus) { callback(status, null) }
            override fun onError(error: String) { callback(null, error) }
        })
    }

    fun deleteRegion(context: Context, region: OfflineRegion, callback: (String?) -> Unit) {
        val styleFile = parseMetadata(region.metadata)?.styleFile?.let(::File)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                styleFile?.delete()
                callback(null)
            }
            override fun onError(error: String) { callback(error) }
        })
    }

    fun parseMetadata(bytes: ByteArray): RegionMetadata? {
        return try {
            val obj = org.json.JSONObject(String(bytes, Charsets.UTF_8))
            RegionMetadata(obj.optString("name", "Offline map"),
                obj.optString("styleFile").takeIf { it.isNotBlank() })
        } catch (_: Exception) { null }
    }
}
