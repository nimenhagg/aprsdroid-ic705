package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyAckMessageIdTest {
    @Test
    fun startsAtOne() {
        assertEquals(1, nextReplyAckMessageId(lastUsed = null, pendingIds = emptySet()))
    }

    @Test
    fun wrapsFromNinetyNineToOne() {
        assertEquals(1, nextReplyAckMessageId(lastUsed = 99, pendingIds = emptySet()))
    }

    @Test
    fun skipsPendingIdsAfterWrap() {
        assertEquals(
            3,
            nextReplyAckMessageId(
                lastUsed = 99,
                pendingIds = setOf(1, 2),
            ),
        )
    }

    @Test
    fun oldLargeIdsStillFoldIntoReplyAckRange() {
        assertEquals(2, nextReplyAckMessageId(lastUsed = 100, pendingIds = emptySet()))
    }
}
