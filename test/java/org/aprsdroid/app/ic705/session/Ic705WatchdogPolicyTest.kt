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
        assertEquals(3_000L, timing.streamRecoveryResponseMillis)
        assertEquals(2, timing.streamRecoveryAttempts)
    }

    @Test
    fun controlEscalatesButIdleStreamsGetBoundedSoftRecovery() {
        assertEquals(
            Ic705WatchdogDecision.ESCALATE,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.CONTROL,
                ageMillis = 5_001L,
                timeoutMillis = 5_000L,
                pttPossiblyAsserted = false,
                activeRecoveryAttempt = null,
                recoveryDeadlineReached = false,
                maxSoftRecoveryAttempts = 2,
            ),
        )
        assertEquals(
            Ic705WatchdogDecision.START_SOFT_RECOVERY,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.CIV,
                ageMillis = 3_001L,
                timeoutMillis = 3_000L,
                pttPossiblyAsserted = false,
                activeRecoveryAttempt = null,
                recoveryDeadlineReached = false,
                maxSoftRecoveryAttempts = 2,
            ),
        )
        assertEquals(
            Ic705WatchdogDecision.ESCALATE,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.CIV,
                ageMillis = 3_001L,
                timeoutMillis = 3_000L,
                pttPossiblyAsserted = true,
                activeRecoveryAttempt = null,
                recoveryDeadlineReached = false,
                maxSoftRecoveryAttempts = 2,
            ),
        )
        assertEquals(
            Ic705WatchdogDecision.WAIT_FOR_SOFT_RECOVERY,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.AUDIO,
                ageMillis = 31_000L,
                timeoutMillis = 30_000L,
                pttPossiblyAsserted = false,
                activeRecoveryAttempt = 1,
                recoveryDeadlineReached = false,
                maxSoftRecoveryAttempts = 2,
            ),
        )
        assertEquals(
            Ic705WatchdogDecision.RETRY_SOFT_RECOVERY,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.AUDIO,
                ageMillis = 34_000L,
                timeoutMillis = 30_000L,
                pttPossiblyAsserted = false,
                activeRecoveryAttempt = 1,
                recoveryDeadlineReached = true,
                maxSoftRecoveryAttempts = 2,
            ),
        )
        assertEquals(
            Ic705WatchdogDecision.ESCALATE,
            ic705WatchdogDecision(
                role = Ic705ChannelRole.AUDIO,
                ageMillis = 37_000L,
                timeoutMillis = 30_000L,
                pttPossiblyAsserted = false,
                activeRecoveryAttempt = 2,
                recoveryDeadlineReached = true,
                maxSoftRecoveryAttempts = 2,
            ),
        )
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
