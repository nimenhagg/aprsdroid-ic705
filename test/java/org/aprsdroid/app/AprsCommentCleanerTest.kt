package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsCommentCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AprsCommentCleanerTest {

    @Test
    fun testStripPhgAndRngLeavesOnlyUserComment() {
        val comment = "PHG2430 RNG0035 438.500MHz Repeater On Air"
        val clean = AprsCommentCleaner.clean(comment)
        assertEquals("Repeater On Air", clean)
    }

    @Test
    fun testPureProtocolExtensionsResultInNull() {
        val comment = "PHG5330 RNG0040 /A=001200 144.800MHz"
        val clean = AprsCommentCleaner.clean(comment)
        assertNull("When comment only contains protocol tokens, clean should return null", clean)
    }

    @Test
    fun testStripDaoAndAltitude() {
        val comment = "73 de BG7XXX /A=000120 !w^r!"
        val clean = AprsCommentCleaner.clean(comment)
        assertEquals("73 de BG7XXX", clean)
    }

    @Test
    fun testChineseRemarksPreserved() {
        val comment = "PHG7462 438.500MHz 北京业余无线电俱乐部中继台"
        val clean = AprsCommentCleaner.clean(comment)
        assertEquals("北京业余无线电俱乐部中继台", clean)
    }

    @Test
    fun testPunctuationAndDelimiterDebrisCleaned() {
        val comment = " / /A=000500 / Hello world / - "
        val clean = AprsCommentCleaner.clean(comment)
        assertEquals("Hello world", clean)
    }

    @Test
    fun testNullAndBlankInput() {
        assertNull(AprsCommentCleaner.clean(null))
        assertNull(AprsCommentCleaner.clean(""))
        assertNull(AprsCommentCleaner.clean("    "))
    }
}
