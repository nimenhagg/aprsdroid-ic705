package org.aprsdroid.app.map

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TileUrlTemplateTest {
    @Test
    fun expandsDistinctSubdomains() {
        assertArrayEquals(
            arrayOf("https://a.example/1/2/3", "https://b.example/1/2/3"),
            TileUrlTemplate.expand("https://{s}.example/1/2/3", "aab")
        )
    }

    @Test
    fun keepsTemplateWithoutSubdomainPlaceholder() {
        assertArrayEquals(
            arrayOf("http://example.test/{z}/{x}/{y}.png"),
            TileUrlTemplate.expand(" http://example.test/{z}/{x}/{y}.png ", "abc")
        )
    }

    @Test
    fun removesUnusedSubdomainPlaceholder() {
        assertArrayEquals(
            arrayOf("http://tiles.example/{z}/{x}/{y}.png"),
            TileUrlTemplate.expand("http://tiles{s}.example/{z}/{x}/{y}.png")
        )
    }

    @Test
    fun rejectsBlankTemplate() {
        assertArrayEquals(emptyArray<String>(), TileUrlTemplate.expand("   ", "abc"))
    }
}
