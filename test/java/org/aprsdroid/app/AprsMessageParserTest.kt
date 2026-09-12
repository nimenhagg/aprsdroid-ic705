package org.aprsdroid.app

import net.ab0oo.aprs.parser.MessagePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsMessageParserTest {
    @Test
    fun acknowledgedTextWithMessageNumberIsNotAck() {
        val parsed = MessagePacket(":N0CALL   :acknowledged{12".toByteArray(), "APRS")
        assertTrue("bundled parser regression precondition", parsed.isAck)

        val fixed = AprsMessageParser.reparseIncoming(parsed)

        assertFalse(fixed.isAck)
        assertFalse(fixed.isRej)
        assertEquals("acknowledged", fixed.messageBody)
        assertEquals("12", fixed.messageNumber)
        assertEquals("N0CALL", fixed.targetCallsign)
    }

    @Test
    fun rejectedTextWithMessageNumberIsNotRej() {
        val parsed = MessagePacket(":N0CALL   :rejected the plan{12".toByteArray(), "APRS")
        assertTrue("bundled parser regression precondition", parsed.isRej)

        val fixed = AprsMessageParser.reparseIncoming(parsed)

        assertFalse(fixed.isAck)
        assertFalse(fixed.isRej)
        assertEquals("rejected the plan", fixed.messageBody)
        assertEquals("12", fixed.messageNumber)
    }

    @Test
    fun standardAckStillParsesAsAck() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :ack003".toByteArray(), "APRS"),
        )

        assertTrue(fixed.isAck)
        assertFalse(fixed.isRej)
        assertEquals("003", fixed.messageNumber)
    }

    @Test
    fun ackLikeTextLongerThanFiveByteMessageNumberIsOrdinaryText() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :acknowledged".toByteArray(), "APRS"),
        )

        assertFalse(fixed.isAck)
        assertEquals("acknowledged", fixed.messageBody)
        assertEquals("", fixed.messageNumber)
    }
}
