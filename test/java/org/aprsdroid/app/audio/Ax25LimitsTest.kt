package org.aprsdroid.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ax25LimitsTest {
    @Test
    fun callsignLimitMatchesTheAx25AddressField() {
        assertTrue(Ax25Limits.fitsCallsign("BG7XXX"))
        assertFalse(Ax25Limits.fitsCallsign("BG7XXXX"))
        assertTrue(Ax25Limits.fitsCallsign(""))
    }

    @Test
    fun frameLimitAcceptsTheLargestValidFrame() {
        assertTrue(Ax25Limits.fitsFrame(1))
        assertTrue(Ax25Limits.fitsFrame(Ax25Limits.MAX_FRAME_BYTES))
        assertFalse(Ax25Limits.fitsFrame(Ax25Limits.MAX_FRAME_BYTES + 1))
        assertFalse(Ax25Limits.fitsFrame(0))
        assertFalse(Ax25Limits.fitsFrame(-1))
    }

    @Test
    fun callsignExceptionReportsTheOffendingAddress() {
        val error = Ax25CallsignException("BG7XXXX-5")
        assertTrue(error.message.orEmpty().contains("BG7XXXX-5"))
        assertTrue(error is IllegalArgumentException)
    }
}
