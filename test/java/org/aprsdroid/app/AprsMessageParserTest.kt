package org.aprsdroid.app

import net.ab0oo.aprs.parser.MessagePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsMessageParserTest {
    @Test
    fun acknowledgedTextWithMessageNumberIsNotAck() {
        val parsed = MessagePacket(":N0CALL   :acknowledged{12".toByteArray(), "APRS")
        assertTrue("bundled parser regression precondition", parsed.isAck)

        val fixed = AprsMessageParser.reparseIncoming(parsed)

        assertFalse(fixed.packet.isAck)
        assertFalse(fixed.packet.isRej)
        assertEquals("acknowledged", fixed.packet.messageBody)
        assertEquals("12", fixed.packet.messageNumber)
        assertEquals("12", fixed.wireMessageNumber)
        assertEquals("N0CALL", fixed.packet.targetCallsign)
    }

    @Test
    fun rejectedTextWithMessageNumberIsNotRej() {
        val parsed = MessagePacket(":N0CALL   :rejected the plan{12".toByteArray(), "APRS")
        assertTrue("bundled parser regression precondition", parsed.isRej)

        val fixed = AprsMessageParser.reparseIncoming(parsed)

        assertFalse(fixed.packet.isAck)
        assertFalse(fixed.packet.isRej)
        assertEquals("rejected the plan", fixed.packet.messageBody)
        assertEquals("12", fixed.packet.messageNumber)
    }

    @Test
    fun standardAckStillParsesAsAck() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :ack003".toByteArray(), "APRS"),
        )

        assertTrue(fixed.packet.isAck)
        assertFalse(fixed.packet.isRej)
        assertEquals("003", fixed.packet.messageNumber)
        assertNull(fixed.replyAck)
    }

    @Test
    fun replyAckMessageSplitsMmAndAaButPreservesWireId() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :hello{12}34".toByteArray(), "APRS"),
        )

        assertFalse(fixed.packet.isAck)
        assertEquals("hello", fixed.packet.messageBody)
        assertEquals("12", fixed.packet.messageNumber)
        assertEquals("12}34", fixed.wireMessageNumber)
        assertEquals("34", fixed.replyAck)
        assertTrue(fixed.replyAckCapable)
    }

    @Test
    fun replyAckCapabilityWithoutPendingAckIsPreserved() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :hello{12}".toByteArray(), "APRS"),
        )

        assertEquals("12", fixed.packet.messageNumber)
        assertEquals("12}", fixed.wireMessageNumber)
        assertNull(fixed.replyAck)
        assertTrue(fixed.replyAckCapable)
    }

    @Test
    fun ackEchoWithReplyAckTrailerMatchesOnlyMm() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :ack12}34".toByteArray(), "APRS"),
        )

        assertTrue(fixed.packet.isAck)
        assertEquals("12", fixed.packet.messageNumber)
        assertEquals("12}34", fixed.wireMessageNumber)
        assertNull(fixed.replyAck)
    }

    @Test
    fun ackLikeTextLongerThanFiveByteMessageNumberIsOrdinaryText() {
        val fixed = AprsMessageParser.reparseIncoming(
            MessagePacket(":N0CALL   :acknowledged".toByteArray(), "APRS"),
        )

        assertFalse(fixed.packet.isAck)
        assertEquals("acknowledged", fixed.packet.messageBody)
        assertEquals("", fixed.packet.messageNumber)
    }
}
