package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRetryDelayTest {

    @Test
    fun retryDelayStrictlyAscendingAndCapped() {
        // Expected delay progression for retries 0 through 10:
        // retrycnt = 0: 30s (shl 0)
        // retrycnt = 1: 30s (shl 0)
        // retrycnt = 2: 60s (shl 1)
        // retrycnt = 3: 120s (shl 2)
        // retrycnt = 4: 240s (shl 3)
        // retrycnt = 5: 480s (shl 4)
        // retrycnt = 6: 960s (shl 5)
        // retrycnt = 7: 1920s (shl 6, cap)
        // retrycnt = 8..10: 1920s (cap maintained)
        val expectedDelays = longArrayOf(
            30_000L,
            30_000L,
            60_000L,
            120_000L,
            240_000L,
            480_000L,
            960_000L,
            1_920_000L,
            1_920_000L,
            1_920_000L,
            1_920_000L,
        )

        for (retrycnt in 0..10) {
            val actual = MessageService.calculateRetryDelayMs(retrycnt)
            assertEquals("Delay mismatch for retry count $retrycnt", expectedDelays[retrycnt], actual)
            assertTrue("Delay must be strictly positive", actual > 0)
        }

        // Monotonicity assertion across 0..10
        for (i in 0 until 10) {
            val d1 = MessageService.calculateRetryDelayMs(i)
            val d2 = MessageService.calculateRetryDelayMs(i + 1)
            assertTrue("Delay must not decrease: retry $i ($d1) -> retry ${i + 1} ($d2)", d2 >= d1)
        }
    }
}