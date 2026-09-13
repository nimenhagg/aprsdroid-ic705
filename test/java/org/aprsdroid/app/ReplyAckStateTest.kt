package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyAckStateTest {
    @Test
    fun outgoingAlwaysAdvertisesReplyAckCapability() {
        val state = ReplyAckState()

        assertEquals("12}", state.decorateOutgoing("N0CALL", "12"))
    }

    @Test
    fun latestIncomingMessageNumberRidesOnNextTransmission() {
        val state = ReplyAckState()
        state.rememberIncoming("N0CALL", "34")

        assertEquals("12}34", state.decorateOutgoing("N0CALL", "12"))
    }

    @Test
    fun ssidZeroUsesSameCorrespondentState() {
        val state = ReplyAckState()
        state.rememberIncoming("N0CALL-0", "34")

        assertEquals("12}34", state.decorateOutgoing("n0call", "12"))
    }

    @Test
    fun newestIncomingNumberReplacesOlderOneForRetries() {
        val state = ReplyAckState()
        state.rememberIncoming("N0CALL", "34")
        assertEquals("12}34", state.decorateOutgoing("N0CALL", "12"))

        state.rememberIncoming("N0CALL", "35")
        assertEquals("12}35", state.decorateOutgoing("N0CALL", "12"))
    }
}
