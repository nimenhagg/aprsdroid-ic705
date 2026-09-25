package org.aprsdroid.app.map

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object MapLibreOfflineStyle {
    fun build(tileUrls: Array<String>, maxZoom: Float, attribution: String?): String {
        require(tileUrls.isNotEmpty()) { "At least one tile URL is required" }
        val source = JsonObject().apply {
            addProperty("type", "raster")
            add("tiles", JsonArray().also { array -> tileUrls.forEach(array::add) })
            addProperty("tileSize", 256)
            addProperty("minzoom", 0)
            addProperty("maxzoom", maxZoom)
            attribution?.takeIf { it.isNotBlank() }?.let { addProperty("attribution", it) }
        }
        return JsonObject().apply {
            addProperty("version", 8)
            add("sources", JsonObject().apply { add("offline-raster", source) })
            add("layers", JsonArray().also { layers ->
                layers.add(JsonObject().apply {
                    addProperty("id", "offline-raster")
                    addProperty("type", "raster")
                    addProperty("source", "offline-raster")
                })
            })
        }.toString()
    }
}
