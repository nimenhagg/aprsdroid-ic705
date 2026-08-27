package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ic705SessionTimingPolicyTest {
    @Test
    fun reconnectDelayUsesExponentialBackoffWithConfiguredCap() {
        val timing = Ic705RxSessionTiming(
            initialReconnectMillis = 1_000L,
            maximumReconnectMillis = 30_000L,
        )

        assertEquals(1_000L, ic705ReconnectDelayMillis(timing, 1, Ic705RxSessionEngine.RetryCooldown.NORMAL))
        assertEquals(2_000L, ic705ReconnectDelayMillis(timing, 2, Ic705RxSessionEngine.RetryCooldown.NORMAL))
        assertEquals(4_000L, ic705ReconnectDelayMillis(timing, 3, Ic705RxSessionEngine.RetryCooldown.NORMAL))
        assertEquals(30_000L, ic705ReconnectDelayMillis(timing, 6, Ic705RxSessionEngine.RetryCooldown.NORMAL))
        assertEquals(30_000L, ic705ReconnectDelayMillis(timing, 20, Ic705RxSessionEngine.RetryCooldown.NORMAL))
    }

    @Test
    fun reconnectCooldownsApplyTheSameMinimumFloorsAsTheSession() {
        val timing = Ic705RxSessionTiming(
            initialReconnectMillis = 1_000L,
            maximumReconnectMillis = 30_000L,
            connectionInfoRetryMillis = 10_000L,
        )

        assertEquals(
            10_000L,
            ic705ReconnectDelayMillis(timing, 1, Ic705RxSessionEngine.RetryCooldown.SESSION_NOT_READY),
        )
        assertEquals(
            30_000L,
            ic705ReconnectDelayMillis(timing, 1, Ic705RxSessionEngine.RetryCooldown.SESSION_REJECTED),
        )
    }

    @Test
    fun handshakeTimeoutsRemainPhaseSpecific() {
        val timing = Ic705RxSessionTiming(
            handshakeStageTimeoutMillis = 10_000L,
            negotiationTimeoutMillis = 45_000L,
        )

        assertEquals(10_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.OPENING_SOCKETS))
        assertEquals(10_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.CONTROL_DISCOVERY))
        assertEquals(10_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.AUTHENTICATING))
        assertEquals(45_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.NEGOTIATING))
        assertEquals(10_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.OPENING_STREAMS))
        assertEquals(10_000L, ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.STREAMS_READY))
        assertNull(ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.RECEIVING))
        assertNull(ic705HandshakeTimeoutMillis(timing, Ic705RxSessionEngine.Phase.RECONNECT_WAIT))
    }
}
