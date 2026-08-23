package org.aprsdroid.app.map

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Universal online tile provider for Google Maps SDK.
 * Supports standard web map tile templates with {x}, {y}, {z}, and optional {s} subdomains.
 */
class OnlineTileProvider(
    private val urlPattern: String,
    private val subdomains: String = "",
    private val width: Int = 256,
    private val height: Int = 256
) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val url = getTileUrl(x, y, zoom) ?: return TileProvider.NO_TILE
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "APRSdroid/1.5.4 (Android)")
            conn.setRequestProperty("Accept", "image/webp,image/png,image/*")
            if (conn.responseCode != 200) {
                return TileProvider.NO_TILE
            }
            val bytes = conn.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                input.copyTo(buffer)
                buffer.toByteArray()
            }
            Tile(width, height, bytes)
        } catch (_: Exception) {
            TileProvider.NO_TILE
        }
    }

    fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
        val s = if (subdomains.isNotEmpty()) {
            val idx = Math.floorMod(x + y, subdomains.length)
            subdomains[idx].toString()
        } else {
            ""
        }

        val formattedUrl = urlPattern
            .replace("{s}", s)
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
            .replace("{z}", zoom.toString())

        return try {
            URL(formattedUrl)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * AutoNavi / AMap Standard Vector/Raster Tile Template:
         * https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}
         * subdomains: "1234" (webrd01 ~ webrd04)
         */
        const val AMAP_TILE_URL = "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}"
        const val AMAP_SUBDOMAINS = "1234"

        /**
         * OpenStreetMap Standard Tile Template:
         * https://tile.openstreetmap.org/{z}/{x}/{y}.png
         */
        const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val OSM_SUBDOMAINS = "abc"

        fun createAmap(): OnlineTileProvider =
            OnlineTileProvider(AMAP_TILE_URL, AMAP_SUBDOMAINS)

        fun createOsm(): OnlineTileProvider =
            OnlineTileProvider(OSM_TILE_URL, OSM_SUBDOMAINS)

        fun createCustom(url: String, subdomains: String = ""): OnlineTileProvider =
            OnlineTileProvider(url, subdomains)
    }
}
