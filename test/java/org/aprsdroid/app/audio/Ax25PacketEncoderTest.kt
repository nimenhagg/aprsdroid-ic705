package org.aprsdroid.app.audio

import java.util.ArrayList
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Digipeater
import net.ab0oo.aprs.parser.MessagePacket
import org.junit.Assert.fail
import org.junit.Test

class Ax25PacketEncoderTest {
    private fun messagePacket(body: String): APRSPacket {
        return APRSPacket(
            "N0CALL",
            "APRS",
            ArrayList<Digipeater>(),
            MessagePacket("TARGET", body, "1"),
        )
    }

    @Test
    fun latin1PayloadStillEncodesLosslessly() {
        Ax25PacketEncoder.encode(messagePacket("café"))
    }

    @Test
    fun unsupportedRfTextIsRejectedInsteadOfReplacedWithQuestionMarks() {
        try {
            Ax25PacketEncoder.encode(messagePacket("中文"))
            fail("Expected Ax25PayloadEncodingException")
        } catch (_: Ax25PayloadEncodingException) {
        }
    }

    @Test
    fun overLongCallsignIsRejectedInsteadOfSilentlyTruncated() {
        val packet = APRSPacket(
            "BG7XXXX",
            "APRS",
            ArrayList<Digipeater>(),
            MessagePacket("TARGET", "hello", "1"),
        )
        try {
            Ax25PacketEncoder.encode(packet)
            fail("Expected Ax25CallsignException")
        } catch (_: Ax25CallsignException) {
        }
    }

    @Test
    fun overLongCallsignIsRejectedByTheDirectFactoryToo() {
        try {
            Ax25PacketEncoder.create(
                source = "BG7XXXX",
                destination = "APRS",
                payload = byteArrayOf(0x3a),
            )
            fail("Expected Ax25CallsignException")
        } catch (_: Ax25CallsignException) {
        }
    }

    @Test
    fun overLongDigipeaterIsRejected() {
        val packet = APRSPacket(
            "N0CALL",
            "APRS",
            ArrayList(listOf(Digipeater("WIDE1234-1"))),
            MessagePacket("TARGET", "hello", "1"),
        )
        try {
            Ax25PacketEncoder.encode(packet)
            fail("Expected Ax25CallsignException")
        } catch (_: Ax25CallsignException) {
        }
    }

    @Test
    fun sixCharacterCallsignWithSsidIsStillAccepted() {
        // The SSID is encoded in the address extension byte, not in the six
        // call sign characters, so it must not count against the limit.
        Ax25PacketEncoder.encode(
            APRSPacket(
                "BG7XXX-5",
                "APRS",
                ArrayList<Digipeater>(),
                MessagePacket("TARGET", "hello", "1"),
            ),
        )
        Ax25PacketEncoder.create(
            source = "BG7XXX-15",
            destination = "APRS",
            payload = byteArrayOf(0x3a),
        )
    }
}
