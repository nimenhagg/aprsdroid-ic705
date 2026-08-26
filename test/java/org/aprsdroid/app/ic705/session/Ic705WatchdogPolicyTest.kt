package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.transport.Ic705ChannelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705WatchdogPolicyTest {
    @Test
    fun defaultTimeoutsAreRoleSpecific() {
        val timing = Ic705RxSessionTiming()

        assertEquals(5_000L, ic705ChannelWatchdogTimeoutMillis(timing, Ic705ChannelRole.CONTROL))
        assertEquals(3_000L, ic705ChannelWatchdogTimeoutMillis(timing, Ic705ChannelRole.CIV))
        assertEquals(30_000L, ic705ChannelWatchdogTimeoutMillis(timing, Ic705ChannelRole.AUDIO))
        assertEquals(5_000L, timing.audioPostTxGraceMillis)
    }

    @Test
    fun audioWatchdogIsSuppressedWhilePttMayBeAssertedOrDuringRxResumeGrace() {
        assertTrue(
            shouldSuppressIc705AudioWatchdog(
                pttPossiblyAsserted = true,
                nowMillis = 10_000L,
                graceUntilMillis = 0L,
            ),
        )
        assertTrue(
            shouldSuppressIc705AudioWatchdog(
                pttPossiblyAsserted = false,
                nowMillis = 10_000L,
                graceUntilMillis = 10_001L,
            ),
        )
        assertFalse(
            shouldSuppressIc705AudioWatchdog(
                pttPossiblyAsserted = false,
                nowMillis = 10_000L,
                graceUntilMillis = 10_000L,
            ),
        )
    }
}
