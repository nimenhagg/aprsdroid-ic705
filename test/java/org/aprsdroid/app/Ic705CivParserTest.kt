package org.aprsdroid.app

import org.aprsdroid.app.ic705.civ.Ic705CivParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Ic705CivParserTest {

    @Test
    fun testParseOperatingFrequency144640() {
        // Frame: FE FE E0 A4 03 00 00 64 44 01 FD -> 144.640.000 Hz
        val frame = byteArrayOf(
            0xFE.toByte(), 0xFE.toByte(),
            0xE0.toByte(), 0xA4.toByte(),
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x64.toByte(), 0x44.toByte(), 0x01.toByte(),
            0xFD.toByte()
        )
        val freq = Ic705CivParser.parseOperatingFrequency(frame)
        assertNotNull(freq)
        assertEquals(144.640, freq!!, 0.0001)
    }

    @Test
    fun testParseOperatingFrequency438500() {
        // 438,500,000 Hz -> b0=00, b1=00, b2=50, b3=38, b4=04
        val frame = byteArrayOf(
            0xFE.toByte(), 0xFE.toByte(),
            0xE0.toByte(), 0xA4.toByte(),
            0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x50.toByte(), 0x38.toByte(), 0x04.toByte(),
            0xFD.toByte()
        )
        val freq = Ic705CivParser.parseOperatingFrequency(frame)
        assertNotNull(freq)
        assertEquals(438.500, freq!!, 0.0001)
    }

    @Test
    fun testParseGpsNmeaRmc() {
        val nmea = "\$GPRMC,081836.00,A,3113.8249,N,12128.4220,E,0.0,360.0,130926,,,A*62"
        val pos = Ic705CivParser.parseNmea(nmea)
        assertNotNull(pos)
        assertEquals(31.230415, pos!!.latitude, 0.0001)
        assertEquals(121.4737, pos.longitude, 0.0001)
    }
}
