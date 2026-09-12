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
}
