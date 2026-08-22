package org.aprsdroid.app.ic705.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705ConnectionInfoCodecTest {
    private val golden = connectionInfoHex(
        "90 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
            "00 00 00 80 01 03 9a bc 00 00 13 57 24 68 ac e0 " +
            "00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f " +
            "10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e 1f " +
            "49 43 2d 37 30 35 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "56 6b 54 56 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "01 00 04 04 00 00 2e e0 00 00 2e e0 00 00 12 34 " +
            "00 00 56 78 00 00 00 96 01 00 00 00 00 00 00 00",
    )

    @Test
    fun rxOnlyParametersMatchFt8cnMixedEndianLayout() {
        assertArrayEquals(
            golden,
            Ic705ConnectionInfoCodec.encodeParameters(
                parameters().copy(
                    receiveSampleRateHz = 12_000,
                    transmitSampleRateHz = 12_000,
                    transmitBufferSamples = 0x96,
                ),
            ),
        )
        assertEquals(0x01, golden[0x70].toInt() and 0xff)
        assertEquals(0x00, golden[0x71].toInt() and 0xff)
    }

    @Test
    fun parametersMatchSuccessfulRsBa1Ic705Capture() {
        val captured = connectionInfoHex(
            "90 00 00 00 00 00 26 00 ec 45 97 ae 42 4b 8b 18 " +
                "00 00 00 80 01 03 00 1e 00 00 50 ed 60 6c d0 a6 " +
                "00 00 00 00 00 00 10 80 00 00 90 c7 12 78 26 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "49 43 2d 37 30 35 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "37 50 49 2d 49 00 00 00 00 00 00 00 00 00 00 00 " +
                "01 01 04 04 00 00 bb 80 00 00 bb 80 00 00 c3 52 " +
                "00 00 c3 53 00 00 00 a0 01 00 00 00 00 00 00 00",
        )
        val identity = captured.copyOfRange(0x20, 0x40)
        val encoded = Ic705ConnectionInfoCodec.encodeParameters(
            Ic705ConnectionParameters(
                sequence = 0x26,
                senderId = 0xae9745ec.toInt(),
                receiverId = 0x188b4b42,
                innerSequence = 0x001e,
                tokenRequest = 0x50ed,
                token = 0x606cd0a6,
                radioIdentityBlock = identity,
                radioName = "IC-705",
                username = "ic705",
                localCivPort = 50_002,
                localAudioPort = 50_003,
                receiveEnabled = true,
                transmitEnabled = true,
                receiveSampleRateHz = 48_000,
                transmitSampleRateHz = 48_000,
                transmitBufferSamples = 0xa0,
            ),
        )

        assertArrayEquals(captured, encoded)
    }

    @Test
    fun initialIdentityBlockMatchesFt8cnConnectionRequestMarker() {
        val identity = Ic705ConnectionInfoCodec.initialClientIdentityBlock()
        assertEquals(0x10, identity[0x06].toInt() and 0xff)
        assertEquals(0x80, identity[0x07].toInt() and 0xff)
        assertEquals(2, identity.count { it != 0.toByte() })
    }

    @Test
    fun parsesRadioIdentityNameAndBusyUnion() {
        val announcementBytes = golden.copyOf().apply {
            // Turn the client union at 0x60 into the start of a radio busy union.
            fill(0, fromIndex = 0x60, toIndex = size)
            this[0x60] = 1
            this[0x08] = 0x88.toByte()
            this[0x09] = 0x77
            this[0x0a] = 0x66
            this[0x0b] = 0x55
            this[0x0c] = 0x44
            this[0x0d] = 0x33
            this[0x0e] = 0x22
            this[0x0f] = 0x11
            this[0x14] = Ic705HandshakeCodec.REQUEST_REPLY_RESPONSE.toByte()
            "APRSdroid".toByteArray().copyInto(this, destinationOffset = 0x64)
        }

        val packet = Ic705ConnectionInfoCodec.decodeAnnouncement(
            announcementBytes,
            expectedReceiverId = 0x11223344,
        )
        assertEquals("IC-705", packet.radioName)
        assertArrayEquals(ByteArray(0x20) { it.toByte() }, packet.radioIdentityBlock)
        assertTrue(packet.isBusy)
        assertEquals("APRSdroid", packet.busyClientName)

        announcementBytes[0x60] = 0
        val available = Ic705ConnectionInfoCodec.decodeAnnouncement(announcementBytes)
        assertFalse(available.isBusy)
        assertEquals(null, available.busyClientName)
    }

    @Test(expected = Ic705ProtocolException::class)
    fun rejectsWrongReceiverId() {
        Ic705ConnectionInfoCodec.decodeAnnouncement(golden, expectedReceiverId = 0x01020304)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvalidLocalPort() {
        Ic705ConnectionInfoCodec.encodeParameters(parameters().copy(localAudioPort = 0))
    }

    private fun parameters() = Ic705ConnectionParameters(
        sequence = 0x1234,
        senderId = 0x11223344,
        receiverId = 0x55667788,
        innerSequence = 0x9abc,
        tokenRequest = 0x1357,
        token = 0x2468ace0,
        radioIdentityBlock = ByteArray(0x20) { it.toByte() },
        radioName = "IC-705",
        username = "USER",
        localCivPort = 0x1234,
        localAudioPort = 0x5678,
    )
}

private fun connectionInfoHex(value: String): ByteArray {
    val compact = value.filterNot(Char::isWhitespace)
    require(compact.length % 2 == 0)
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
