package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ax25PacketConsumerTest {
    @Test
    fun boundedHexShowsShortFramesCompletely() {
        assertEquals("00 01 fe ff (4 bytes)", boundedAx25Hex(byteArrayOf(0, 1, -2, -1)))
    }

    @Test
    fun boundedHexTruncatesLongFramesAndReportsOriginalLength() {
        val data = ByteArray(80) { it.toByte() }
        val summary = boundedAx25Hex(data, maxBytes = 8)

        assertTrue(summary.startsWith("00 01 02 03 04 05 06 07"))
        assertTrue(summary.contains("+72 bytes; 80 total"))
        assertFalse(summary.contains("08 09"))
    }
}
