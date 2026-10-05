package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsCharsetDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class AprsCharsetDecoderTest {

    @Test
    fun testPureAscii() {
        val text = "73 de BG7XXX CQ CQ 144.640MHz"
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val decoded = AprsCharsetDecoder.decodeSmart(bytes)
        assertEquals(text, decoded)
    }

    @Test
    fun testUtf8Chinese() {
        val text = "你好，业余无线电 73 de BG7XXX"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val decoded = AprsCharsetDecoder.decodeSmart(bytes)
        assertEquals(text, decoded)
    }

    @Test
    fun testGbkChinese() {
        val text = "美好的一天，祝通联愉快！"
        val gbk = Charset.forName("GBK")
        val bytes = text.toByteArray(gbk)
        val decoded = AprsCharsetDecoder.decodeSmart(bytes)
        assertEquals(text, decoded)
    }

    @Test
    fun testRepairIso8859MisinterpretedGbk() {
        val originalText = "北京海淀中继台 438.500MHz"
        val gbk = Charset.forName("GBK")
        val rawGbkBytes = originalText.toByteArray(gbk)

        // Simulate javAPRSlib reading bytes as ISO-8859-1
        val corruptedString = String(rawGbkBytes, Charsets.ISO_8859_1)

        // Repair should recover the original Chinese text
        val repaired = AprsCharsetDecoder.repairString(corruptedString)
        assertEquals(originalText, repaired)
    }

    @Test
    fun testRepairIso8859MisinterpretedUtf8() {
        val originalText = "广州塔 APRS 节点 144.640MHz"
        val rawUtf8Bytes = originalText.toByteArray(Charsets.UTF_8)

        // Simulate reading bytes as ISO-8859-1
        val corruptedString = String(rawUtf8Bytes, Charsets.ISO_8859_1)

        val repaired = AprsCharsetDecoder.repairString(corruptedString)
        assertEquals(originalText, repaired)
    }
}
