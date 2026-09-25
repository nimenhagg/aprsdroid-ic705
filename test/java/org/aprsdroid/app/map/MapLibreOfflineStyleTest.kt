package org.aprsdroid.app.map

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLibreOfflineStyleTest {
    @Test
    fun buildsRasterStyleForOfflinePack() {
        val json = JsonParser.parseString(
            MapLibreOfflineStyle.build(
                arrayOf(
                    "https://tiles.example/a/{z}/{x}/{y}.png",
                    "https://tiles.example/b/{z}/{x}/{y}.png"
                ),
                19f,
                "Example"
            )
        ).asJsonObject

        assertEquals(8, json.get("version").asInt)
        val source = json.getAsJsonObject("sources").getAsJsonObject("offline-raster")
        assertEquals("raster", source.get("type").asString)
        assertEquals(2, source.getAsJsonArray("tiles").size())
        assertEquals(256, source.get("tileSize").asInt)
        assertEquals("Example", source.get("attribution").asString)
        assertEquals("offline-raster", json.getAsJsonArray("layers")[0].asJsonObject.get("source").asString)
        assertTrue(json.getAsJsonArray("layers").size() == 1)
    }
}
