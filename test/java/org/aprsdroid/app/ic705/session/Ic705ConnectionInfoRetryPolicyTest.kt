package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Test

class Ic705ConnectionInfoRetryPolicyTest {
    @Test
    fun statusDecisionIgnoresEventsBeforeRequestOrAfterEndpointsArrive() {
        assertEquals(
            Ic705ConnectionInfoStatusDecision.IGNORE,
            ic705ConnectionInfoStatusDecision(
                connectionInfoSent = false,
                hasStreamEndpoints = false,
                errorCode = 0,
                disconnectFlag = 0,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoStatusDecision.IGNORE,
            ic705ConnectionInfoStatusDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = true,
                errorCode = -1,
                disconnectFlag = 1,
            ),
        )
    }

    @Test
    fun zeroStatusRetriesButAnyExplicitFailureRejectsTheSession() {
        assertEquals(
            Ic705ConnectionInfoStatusDecision.RETRY_SAME_SESSION,
            ic705ConnectionInfoStatusDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                errorCode = 0,
                disconnectFlag = 0,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoStatusDecision.REJECT_SESSION,
            ic705ConnectionInfoStatusDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                errorCode = -1,
                disconnectFlag = 0,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoStatusDecision.REJECT_SESSION,
            ic705ConnectionInfoStatusDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                errorCode = 0,
                disconnectFlag = 1,
            ),
        )
    }

    @Test
    fun retryDecisionAllowsFourTotalAttemptsThenExhausts() {
        assertEquals(4, IC705_MAX_CONNECTION_INFO_ATTEMPTS)
        assertEquals(
            Ic705ConnectionInfoRetryDecision.RETRY,
            ic705ConnectionInfoRetryDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                attempts = 1,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoRetryDecision.RETRY,
            ic705ConnectionInfoRetryDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                attempts = 3,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoRetryDecision.EXHAUSTED,
            ic705ConnectionInfoRetryDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = false,
                attempts = 4,
            ),
        )
    }

    @Test
    fun retryTimerIsIgnoredWhenItCannotAffectTheActiveNegotiation() {
        assertEquals(
            Ic705ConnectionInfoRetryDecision.IGNORE,
            ic705ConnectionInfoRetryDecision(
                connectionInfoSent = false,
                hasStreamEndpoints = false,
                attempts = 0,
            ),
        )
        assertEquals(
            Ic705ConnectionInfoRetryDecision.IGNORE,
            ic705ConnectionInfoRetryDecision(
                connectionInfoSent = true,
                hasStreamEndpoints = true,
                attempts = 4,
            ),
        )
    }
}
