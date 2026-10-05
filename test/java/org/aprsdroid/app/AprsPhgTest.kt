package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsPhg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AprsPhgTest {

    @Test
    fun testPowerQuantizationFlooring() {
        assertEquals(0, AprsPhg.powerToCode(0.0))
        assertEquals(0, AprsPhg.powerToCode(0.5))
        assertEquals(1, AprsPhg.powerToCode(1.0))
        assertEquals(1, AprsPhg.powerToCode(3.9))
        assertEquals(2, AprsPhg.powerToCode(4.0))
        assertEquals(2, AprsPhg.powerToCode(5.0)) // 5W HT reports code 2 (4W)
        assertEquals(3, AprsPhg.powerToCode(9.0))
        assertEquals(3, AprsPhg.powerToCode(10.0)) // 10W reports code 3 (9W)
        assertEquals(4, AprsPhg.powerToCode(16.0))
        assertEquals(4, AprsPhg.powerToCode(20.0))
        assertEquals(5, AprsPhg.powerToCode(25.0))
        assertEquals(5, AprsPhg.powerToCode(30.0)) // 30W reports code 5 (25W)
        assertEquals(6, AprsPhg.powerToCode(36.0))
        assertEquals(7, AprsPhg.powerToCode(49.0))
        assertEquals(7, AprsPhg.powerToCode(50.0)) // 50W reports code 7 (49W)
        assertEquals(8, AprsPhg.powerToCode(64.0))
        assertEquals(9, AprsPhg.powerToCode(81.0))
        assertEquals(9, AprsPhg.powerToCode(100.0)) // Capped at code 9
    }

    @Test
    fun testHeightQuantizationFlooring() {
        assertEquals(0, AprsPhg.heightToCode(5.0))
        assertEquals(0, AprsPhg.heightToCode(10.0))
        assertEquals(0, AprsPhg.heightToCode(19.9))
        assertEquals(1, AprsPhg.heightToCode(20.0))
        assertEquals(2, AprsPhg.heightToCode(40.0))
        assertEquals(3, AprsPhg.heightToCode(80.0))
        assertEquals(4, AprsPhg.heightToCode(160.0))
        assertEquals(5, AprsPhg.heightToCode(320.0))
        assertEquals(6, AprsPhg.heightToCode(640.0))
        assertEquals(7, AprsPhg.heightToCode(1280.0))
        assertEquals(8, AprsPhg.heightToCode(2560.0))
        assertEquals(9, AprsPhg.heightToCode(5120.0))
        assertEquals(9, AprsPhg.heightToCode(10000.0)) // Capped at 9
    }

    @Test
    fun testGainQuantization() {
        assertEquals(0, AprsPhg.gainToCode(0.0))
        assertEquals(3, AprsPhg.gainToCode(3.2))
        assertEquals(6, AprsPhg.gainToCode(6.0))
        assertEquals(9, AprsPhg.gainToCode(9.0))
        assertEquals(9, AprsPhg.gainToCode(15.0)) // Capped at 9
    }

    @Test
    fun testDirectivityQuantization() {
        assertEquals(0, AprsPhg.directivityToCode(0, isOmni = true))
        assertEquals(0, AprsPhg.directivityToCode(0, isOmni = false))
        assertEquals(1, AprsPhg.directivityToCode(45, isOmni = false)) // NE
        assertEquals(2, AprsPhg.directivityToCode(90, isOmni = false)) // E
        assertEquals(3, AprsPhg.directivityToCode(135, isOmni = false)) // SE
        assertEquals(4, AprsPhg.directivityToCode(180, isOmni = false)) // S
        assertEquals(5, AprsPhg.directivityToCode(225, isOmni = false)) // SW
        assertEquals(6, AprsPhg.directivityToCode(270, isOmni = false)) // W
        assertEquals(7, AprsPhg.directivityToCode(315, isOmni = false)) // NW
        assertEquals(8, AprsPhg.directivityToCode(360, isOmni = false)) // N
    }

    @Test
    fun testEncoding() {
        // null if unconfigured
        assertNull(AprsPhg.encode(null, null, null))

        // 25W, 80ft HAAT, 3dBi, Omni -> PHG5330
        assertEquals("PHG5330", AprsPhg.encode(25.0, 80.0, 3.0, 0))

        // 5W, 10ft, 0dBi, Omni -> PHG2000
        assertEquals("PHG2000", AprsPhg.encode(5.0, 10.0, 0.0, 0))

        // 50W, 160ft, 6dBi, East (90 deg) -> PHG7462
        assertEquals("PHG7462", AprsPhg.encode(50.0, 160.0, 6.0, 90, isOmni = false))
    }

    @Test
    fun testDecoding() {
        val decoded = AprsPhg.decode("PHG5330")
        assertNotNull(decoded)
        assertEquals(25, decoded!!.powerWatts)
        assertEquals(80, decoded.heightFeet)
        assertEquals(3, decoded.gainDb)
        assertEquals(0, decoded.directivityDeg)
        assertEquals("PHG5330", decoded.rawCode)

        val desc = AprsPhg.formatDescription(decoded)
        assertEquals("25W · 80ft (24m) · 3dBi · Omni", desc)

        val descZh = AprsPhg.formatDescriptionZh(decoded)
        assertEquals("25W · 80ft (24m) · 3dBi · 全向", descZh)
    }

    @Test
    fun testRegexExtraction() {
        val payload = "!2216.45N/11113.90ErPHG5330/A=000120 APRSDroid Mod"
        val match = AprsPhg.PHG_REGEX.find(payload)
        assertNotNull(match)
        assertEquals("PHG5330", match!!.value)
        val phg = AprsPhg.decode(match.value)
        assertNotNull(phg)
        assertEquals(25, phg!!.powerWatts)
    }
}
