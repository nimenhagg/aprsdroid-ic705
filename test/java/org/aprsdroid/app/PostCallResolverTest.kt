package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostCallResolverTest {

    @Test
    fun returnsExplicitCallWhenProvided() {
        val result = PostCallResolver.resolve(
            cleanMessage = "BI1XYZ>APRS:>Hello world",
            postType = StorageDatabase.Companion.Post.TYPE_POST,
            explicitCall = "BG1ABC-9",
        )
        assertEquals("BG1ABC-9", result)
    }

    @Test
    fun extractsCallFromPostMessage() {
        val result = PostCallResolver.resolve(
            cleanMessage = "BG1ABC>APRS:=3954.12N/11624.34E-",
            postType = StorageDatabase.Companion.Post.TYPE_POST,
        )
        assertEquals("BG1ABC", result)
    }

    @Test
    fun extractsCallFromIncomingMessage() {
        val result = PostCallResolver.resolve(
            cleanMessage = "BH4TEST-7>APRS,TCPIP*:>Hi there",
            postType = StorageDatabase.Companion.Post.TYPE_INCMG,
        )
        assertEquals("BH4TEST-7", result)
    }

    @Test
    fun extractsCallFromTxMessage() {
        val result = PostCallResolver.resolve(
            cleanMessage = "MYCALL>APRS:!3954.12N/11624.34E#",
            postType = StorageDatabase.Companion.Post.TYPE_TX,
        )
        assertEquals("MYCALL", result)
    }

    @Test
    fun ignoresCallExtractionForInfoAndErrorPosts() {
        val infoResult = PostCallResolver.resolve(
            cleanMessage = "SERVICE>STARTED: Background radio service",
            postType = StorageDatabase.Companion.Post.TYPE_INFO,
        )
        assertNull(infoResult)

        val errResult = PostCallResolver.resolve(
            cleanMessage = "RADIO>ERROR: Connection refused",
            postType = StorageDatabase.Companion.Post.TYPE_ERROR,
        )
        assertNull(errResult)
    }

    @Test
    fun returnsNullWhenNoAngleBracketInMessage() {
        val result = PostCallResolver.resolve(
            cleanMessage = "Raw unparsed message without destination",
            postType = StorageDatabase.Companion.Post.TYPE_POST,
        )
        assertNull(result)
    }
}
