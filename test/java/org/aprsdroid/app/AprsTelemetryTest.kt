package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsTelemetryTest {

    @Test
    fun testParseStandardTelemetry() {
        val payload = "T#042,128,095,012,230,005,10100001 High altitude balloon"
        val data = AprsTelemetry.parse(payload)
        assertNotNull(data)
        assertEquals(42, data!!.sequenceNumber)
        assertEquals(listOf(128, 95, 12, 230, 5), data.analogChannels)
        assertEquals("10100001", data.digitalBits)
        assertEquals(listOf(true, false, true, false, false, false, false, true), data.digitalFlags)
        assertEquals("High altitude balloon", data.comment)

        val summary = data.formatSummary()
        assertTrue(summary.contains("#42"))
        assertTrue(summary.contains("A1:128"))
        assertTrue(summary.contains("A5:5"))
        assertTrue(summary.contains("D:10100001"))

        val summaryZh = data.formatSummaryZh()
        assertTrue(summaryZh.contains("序号 #42"))
        assertTrue(summaryZh.contains("数字通道:10100001"))
    }

    @Test
    fun testParseTelemetryWithoutComment() {
        val payload = "T#999,255,200,150,100,050,00000000"
        val data = AprsTelemetry.parse(payload)
        assertNotNull(data)
        assertEquals(999, data!!.sequenceNumber)
        assertEquals(listOf(255, 200, 150, 100, 50), data.analogChannels)
        assertEquals("00000000", data.digitalBits)
        assertFalse(data.digitalFlags.any { it })
        assertNull(data.comment)
    }

    @Test
    fun testParseInvalidTelemetry() {
        assertNull(AprsTelemetry.parse("Not a telemetry packet"))
        assertNull(AprsTelemetry.parse("T#invalid,data"))
    }

    @Test
    fun testParseTelemetryDefinitions() {
        val parm = "PARM.BatV,Temp,Press,SolV,Alt,LED,TX,RX,GPS,S1,S2,S3,S4"
        val params = AprsTelemetry.parseParameterNames(parm)
        assertNotNull(params)
        assertEquals(13, params!!.size)
        assertEquals("BatV", params[0])
        assertEquals("S4", params[12])

        val unit = "UNIT.V,degC,hPa,V,m,on,on,on,on,on,on,on,on"
        val units = AprsTelemetry.parseUnitNames(unit)
        assertNotNull(units)
        assertEquals(13, units!!.size)
        assertEquals("V", units[0])
        assertEquals("degC", units[1])

        val eqns = "EQNS.0,0.1,0,0,1,-50,0,0.01,0,0,0.2,0,0,1,0"
        val equations = AprsTelemetry.parseEquations(eqns)
        assertNotNull(equations)
        assertEquals(5, equations!!.size)
        // Channel 1: 0*v^2 + 0.1*v + 0 -> 128 raw = 12.8
        assertEquals(12.8, equations[0].apply(128), 0.001)
        // Channel 2: 0*v^2 + 1*v - 50 -> 75 raw = 25.0
        assertEquals(25.0, equations[1].apply(75), 0.001)

        val bits = "BITS.11111111,HAB Project Alpha"
        val bitsAndTitle = AprsTelemetry.parseBitsAndTitle(bits)
        assertNotNull(bitsAndTitle)
        assertEquals("11111111", bitsAndTitle!!.first)
        assertEquals("HAB Project Alpha", bitsAndTitle.second)
    }
}
