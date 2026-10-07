package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsPacketKind
import org.aprsdroid.app.aprs.AprsPacketSummaryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class AprsPacketSummaryParserTest {

    @Test
    fun testPositionWithDao() {
        // Standard APRS position with Base91 DAO !w^r!
        val raw = "BG7XXX>APRS,WIDE1-1:!2216.45N/11410.34E#73 via APRS !w^r!"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.POSITION, parsed.kind)
        assertEquals("BG7XXX", parsed.source)
        assertNotNull(parsed.dao)
        assertNotNull(parsed.latitude)
        assertNotNull(parsed.longitude)

        // Standard lat is 22 + 16.45 / 60 = 22.2741666...
        // DAO adds offset
        assertTrue(parsed.latitude!! > 22.2741)
        assertTrue(parsed.longitude!! > 114.1723)

        // DAO should be stripped from the visible comment
        assertEquals("73 via APRS", parsed.comment)
    }

    @Test
    fun testWeatherPacket() {
        // APRS positionless weather report
        val raw = "FW0123>APRS:_10051130c220s004g008t068r000p005P002h75b10132"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.WEATHER, parsed.kind)
        assertNotNull(parsed.weather)
        val wx = parsed.weather!!
        assertEquals(220, wx.windDirDeg)
        assertEquals(4, wx.windSpeedMph)
        assertEquals(8, wx.windGustMph)
        assertEquals(68, wx.tempF)
        assertEquals(75, wx.humidityPercent)
        assertEquals(10132, wx.pressureTenthsHpa)
        assertEquals(1013.2, wx.pressureHpa!!, 0.01)
    }

    @Test
    fun testPhgAndRngCoverage() {
        val raw = "BI4XXX>APRS,TCPIP*:=3114.50N/12128.50E#PHG2430 RNG0035 438.500MHz"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.POSITION, parsed.kind)
        assertNotNull(parsed.phg)
        assertEquals(4, parsed.phg?.powerWatts)
        assertEquals(160, parsed.phg?.heightFeet)
        assertEquals(3, parsed.phg?.gainDb)
        assertEquals(35.0, parsed.rngMiles!!, 0.1)
        org.junit.Assert.assertNull("Protocol extensions should not remain in comment", parsed.comment)
    }

    @Test
    fun testCharsetRepairInRawComment() {
        val originalText = "BG1ABC>APRS,WIDE2-1:!3954.20N/11623.50E#北京业余无线电俱乐部"
        val gbk = Charset.forName("GBK")
        val gbkBytes = originalText.toByteArray(gbk)

        // Read as ISO-8859-1 (how javAPRSlib reads 8-bit streams)
        val corrupted = String(gbkBytes, Charsets.ISO_8859_1)

        val parsed = AprsPacketSummaryParser.parse(corrupted)
        assertEquals("北京业余无线电俱乐部", parsed.comment)
    }

    @Test
    fun testPhgAndRngWithHumanRemark() {
        val raw = "BI4XXX>APRS,TCPIP*:=3114.50N/12128.50E#PHG2430 RNG0035 438.500MHz Shanghai Repeater"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.POSITION, parsed.kind)
        assertNotNull(parsed.phg)
        assertEquals(4, parsed.phg?.powerWatts)
        assertEquals("Shanghai Repeater", parsed.comment)
    }

    @Test
    fun testTelemetryPacketDeepDecoding() {
        val raw = "HAB001>APRS,WIDE2-1:T#042,128,095,012,230,005,10100001 Balloon telemetry"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.TELEMETRY, parsed.kind)
        assertEquals("HAB001", parsed.source)
        assertNotNull(parsed.telemetry)
        val t = parsed.telemetry!!
        assertEquals(42, t.sequenceNumber)
        assertEquals(128, t.analogChannels[0])
        assertEquals("10100001", t.digitalBits)
        assertEquals("Balloon telemetry", parsed.comment)
    }

    @Test
    fun testThirdPartyRecursiveUnwrapping() {
        val raw = "IGATE>APRS,TCPIP*:}BG1ABC>APRS,WIDE2-1:!3954.20N/11623.50E#Beijing Relay"
        val parsed = AprsPacketSummaryParser.parse(raw)

        assertEquals(AprsPacketKind.POSITION, parsed.kind)
        assertEquals("BG1ABC", parsed.source)
        assertEquals("APRS", parsed.destination)
        assertEquals(listOf("WIDE2-1"), parsed.path)
        assertEquals("Beijing Relay", parsed.comment)
        assertEquals(1, parsed.thirdPartyGateways.size)
        assertEquals("IGATE", parsed.thirdPartyGateways[0].gatewaySource)
    }
}
