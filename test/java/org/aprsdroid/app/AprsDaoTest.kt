package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AprsDaoTest {

    @Test
    fun testEncodeDao() {
        val lat = 22.274278 // 22° 16.45668' N
        val lon = 114.172481 // 114° 10.34886' E
        val dao = AprsDao.encodeDao(lat, lon)
        assertTrue("DAO should start with !w and end with !", dao.startsWith("!w") && dao.endsWith("!"))
        assertEquals("DAO length should be 5", 5, dao.length)
    }

    @Test
    fun testParseBase91Dao() {
        val comment = "73 de BG7XXX !w^r! /A=000100"
        val offset = AprsDao.parseDao(comment, isNorth = true, isEast = true)
        assertNotNull(offset)
        assertEquals("!w^r!", offset!!.rawDao)
        assertTrue(offset.deltaLatDegrees > 0.0)
        assertTrue(offset.deltaLonDegrees > 0.0)
    }

    @Test
    fun testParseDecimalDao() {
        val comment = "Testing !W48! in comment"
        val offset = AprsDao.parseDao(comment, isNorth = true, isEast = true)
        assertNotNull(offset)
        assertEquals("!W48!", offset!!.rawDao)
        val expectedDeltaLat = (4 * 0.001) / 60.0
        val expectedDeltaLon = (8 * 0.001) / 60.0
        assertEquals(expectedDeltaLat, offset.deltaLatDegrees, 1e-7)
        assertEquals(expectedDeltaLon, offset.deltaLonDegrees, 1e-7)
    }

    @Test
    fun testStripDao() {
        val comment = "Hello world !w48! 438.500MHz"
        val stripped = AprsDao.stripDao(comment)
        assertEquals("Hello world  438.500MHz", stripped)
    }

    @Test
    fun testRoundTripPrecision() {
        val originalLat = 31.230416
        val originalLon = 121.473701
        val dao = AprsDao.encodeDao(originalLat, originalLon)
        val offset = AprsDao.parseDao(dao, isNorth = true, isEast = true)
        assertNotNull(offset)

        // Standard APRS truncate to 0.01 minute
        val latMin = originalLat * 60.0
        val baseLatMin = kotlin.math.floor(latMin * 100.0) / 100.0
        val baseLatDeg = baseLatMin / 60.0

        val restoredLat = baseLatDeg + offset!!.deltaLatDegrees
        val errorMeters = abs(originalLat - restoredLat) * 111139.0
        assertTrue("Error after DAO should be sub-meter (< 1.0m), got $errorMeters m", errorMeters < 1.0)
    }
}
