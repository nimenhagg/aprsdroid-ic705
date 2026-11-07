package org.aprsdroid.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMapIconRendererTest {

    @Test
    fun encodeImageIdReturnsSafeBase64String() {
        val encoded = StationMapIconRenderer.encodeImageId("BG1ABC-9")
        assertFalse(encoded.contains("="))
        assertFalse(encoded.contains("\n"))
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun encodeImageIdIsDeterministic() {
        val encoded1 = StationMapIconRenderer.encodeImageId("BI1AXX")
        val encoded2 = StationMapIconRenderer.encodeImageId("BI1AXX")
        assertEquals(encoded1, encoded2)
    }
}
