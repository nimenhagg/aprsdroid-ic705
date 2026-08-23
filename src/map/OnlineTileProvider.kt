package org.aprsdroid.app.map

import com.google.android.gms.maps.model.UrlTileProvider
import java.net.URL
import java.util.Locale

/**
 * Universal online tile provider for Google Maps SDK.
 * Supports standard web map tile templates with {x}, {y}, {z}, and optional {s} subdomains.
 */
class OnlineTileProvider(
    private val urlPattern: String,
    private val subdomains: String = "",
    width: Int = 256,
    height: Int = 256
) : UrlTileProvider(width, height) {

    override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
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
