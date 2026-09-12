package org.aprsdroid.app.audio

import java.nio.charset.StandardCharsets
import java.util.ArrayList
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test

class Ax25PacketEncoderTest {
    private fun messagePacket(body: String): APRSPacket {
        val packet = APRSPacket(
            "N0CALL",
            "APRS",
            ArrayList(),
            byteArrayOf(':'.code.toByte()),
        )
        packet.setInfoField(MessagePacket("TARGET", body, "1"))
        return packet
    }

    @Test
    fun latin1PayloadStillEncodesLosslessly() {
        val encoded = Ax25PacketEncoder.encode(messagePacket("café"))
        val text = String(encoded.getPayload(), StandardCharsets.ISO_8859_1)
        org.junit.Assert.assertTrue(text.contains("café"))
    }

    @Test
    fun unsupportedRfTextIsRejectedInsteadOfReplacedWithQuestionMarks() {
        try {
            Ax25PacketEncoder.encode(messagePacket("中文"))
            fail("Expected Ax25PayloadEncodingException")
        } catch (_: Ax25PayloadEncodingException) {
        }
    }
}
