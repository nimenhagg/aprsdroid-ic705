package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRetryDecisionTest {
    @Test
    fun finalRetryWaitsUntilItsTimeoutExpires() {
        assertEquals(
            PendingMessageAction.WAIT,
            pendingMessageAction(
                retryCount = MessageService.NUM_OF_RETRIES,
                maxRetries = MessageService.NUM_OF_RETRIES,
                delayMillis = 1L,
            ),
        )
    }

    @Test
    fun finalRetryAbortsOnlyAfterTimeout() {
        assertEquals(
            PendingMessageAction.ABORT,
            pendingMessageAction(
                retryCount = MessageService.NUM_OF_RETRIES,
                maxRetries = MessageService.NUM_OF_RETRIES,
                delayMillis = 0L,
            ),
        )
    }

    @Test
    fun earlierRetrySendsWhenDue() {
        assertEquals(
            PendingMessageAction.SEND,
            pendingMessageAction(
                retryCount = MessageService.NUM_OF_RETRIES - 1,
                maxRetries = MessageService.NUM_OF_RETRIES,
                delayMillis = 0L,
            ),
        )
    }
}
