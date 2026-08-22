package org.aprsdroid.app.ic705.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Ic705WireByteOrderTest {
    @Test
    fun helpersNameTheActualWireByteOrder() {
        val bytes = ByteArray(12)
        Ic705WireByteOrder.writeUInt16Le(bytes, 0, 0x1234)
        Ic705WireByteOrder.writeUInt16Be(bytes, 2, 0x1234)
        Ic705WireByteOrder.writeInt32Le(bytes, 4, 0x12345678)
        Ic705WireByteOrder.writeInt32Be(bytes, 8, 0x12345678)

        assertArrayEquals(
            hex("34 12 12 34 78 56 34 12 12 34 56 78"),
            bytes,
        )
        assertEquals(0x1234, Ic705WireByteOrder.readUInt16Le(bytes, 0))
        assertEquals(0x1234, Ic705WireByteOrder.readUInt16Be(bytes, 2))
        assertEquals(0x12345678, Ic705WireByteOrder.readInt32Le(bytes, 4))
        assertEquals(0x12345678, Ic705WireByteOrder.readInt32Be(bytes, 8))
    }
}

class Ic705ControlPacketCodecTest {
    private val golden = hex("10 00 00 00 03 00 34 12 44 33 22 11 88 77 66 55")

    @Test
    fun controlPacketMatchesFt8cnGoldenVector() {
        val packet = Ic705ControlPacket(
            type = Ic705ControlPacketCodec.TYPE_ARE_YOU_THERE,
            sequence = 0x1234,
            senderId = 0x11223344,
            receiverId = 0x55667788,
        )

        assertArrayEquals(golden, Ic705ControlPacketCodec.encode(packet))
        assertEquals(packet, Ic705ControlPacketCodec.decode(golden, expectedReceiverId = 0x55667788))
    }

    @Test
    fun controlPacketRejectsMalformedLength() {
        val malformed = golden.copyOf()
        malformed[0] = 0x0f
        expectProtocolFailure { Ic705ControlPacketCodec.decode(malformed) }
    }

    @Test
    fun controlPacketRejectsWrongReceiverId() {
        expectProtocolFailure {
            Ic705ControlPacketCodec.decode(golden, expectedReceiverId = 0x01020304)
        }
    }

    @Test
    fun retransmitRequestSupportsSingleAndMultipleSequences() {
        val single = Ic705ControlPacketCodec.encode(
            Ic705ControlPacket(
                type = Ic705ControlPacketCodec.TYPE_RETRANSMIT,
                sequence = 0x1234,
                senderId = 0x11223344,
                receiverId = 0x55667788,
            ),
        )
        assertEquals(
            listOf(0x1234),
            Ic705ControlPacketCodec.decodeRetransmitRequest(single, 0x55667788),
        )

        val multiple = hex("14 00 00 00 01 00 00 00 44 33 22 11 88 77 66 55 34 12 cd ab")
        assertEquals(
            listOf(0x1234, 0xabcd),
            Ic705ControlPacketCodec.decodeRetransmitRequest(multiple, 0x55667788),
        )
    }

    @Test
    fun retransmitRequestRejectsAnOddSequencePayload() {
        val malformed = hex("11 00 00 00 01 00 00 00 44 33 22 11 88 77 66 55 34")
        expectProtocolFailure { Ic705ControlPacketCodec.decodeRetransmitRequest(malformed) }
    }
}

class Ic705AudioPacketCodecTest {
    private val payload = hex("34 12 cc ed")
    private val golden = hex(
        "1c 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
            "80 00 9a bc 00 00 00 04 34 12 cc ed",
    )

    @Test
    fun audioPacketMatchesFt8cnMixedEndianGoldenVector() {
        val encoded = Ic705AudioPacketCodec.encode(
            sequence = 0x1234,
            senderId = 0x11223344,
            receiverId = 0x55667788,
            audioSequence = 0x9abc,
            pcmPayload = payload,
        )

        assertArrayEquals(golden, encoded)
        val decoded = Ic705AudioPacketCodec.decode(encoded, expectedReceiverId = 0x55667788)
        assertEquals(0x1234, decoded.header.sequence)
        assertEquals(0x8000, decoded.header.identity)
        assertEquals(0x9abc, decoded.header.audioSequence)
        assertEquals(4, decoded.header.payloadLength)
        assertArrayEquals(payload, decoded.pcmPayload)
    }

    @Test
    fun audioConstantsPreserveFt8cnNegotiatedFormat() {
        assertEquals(12_000, Ic705AudioPacketCodec.SAMPLE_RATE_HZ)
        assertEquals(240, Ic705AudioPacketCodec.SAMPLES_PER_PACKET)
        assertEquals(480, Ic705AudioPacketCodec.PCM_BYTES_PER_PACKET)
    }

    @Test
    fun audioPacketRejectsMalformedTotalLength() {
        val malformed = golden.copyOf()
        malformed[0] = 0x1b
        expectProtocolFailure { Ic705AudioPacketCodec.decode(malformed) }
    }

    @Test
    fun audioPacketRejectsMalformedPayloadLength() {
        val malformed = golden.copyOf()
        malformed[0x17] = 0x05
        expectProtocolFailure { Ic705AudioPacketCodec.decode(malformed) }
    }

    @Test
    fun audioPacketRejectsWrongReceiverId() {
        expectProtocolFailure {
            Ic705AudioPacketCodec.decode(golden, expectedReceiverId = 0x01020304)
        }
    }
}

class Ic705CivCodecTest {
    private val pttOn = hex("fe fe a4 e0 1c 00 01 fd")
    private val golden = hex(
        "1d 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
            "c1 08 00 9a bc fe fe a4 e0 1c 00 01 fd",
    )

    @Test
    fun pttBuilderMatchesFt8cnCivFrame() {
        assertArrayEquals(pttOn, Ic705CivCommands.buildPttFrame(pttOn = true))
        assertArrayEquals(
            hex("fe fe a4 e0 1c 00 00 fd"),
            Ic705CivCommands.buildPttFrame(pttOn = false),
        )
    }

    @Test
    fun civEnvelopeMatchesFt8cnMixedEndianGoldenVector() {
        val packet = Ic705CivDatagram(
            sequence = 0x1234,
            senderId = 0x11223344,
            receiverId = 0x55667788,
            civSequence = 0x9abc,
            civFrame = pttOn,
        )

        assertArrayEquals(golden, Ic705CivDatagramCodec.encode(packet))
        val decoded = Ic705CivDatagramCodec.decode(golden, expectedReceiverId = 0x55667788)
        assertEquals(packet.type, decoded.type)
        assertEquals(packet.sequence, decoded.sequence)
        assertEquals(packet.senderId, decoded.senderId)
        assertEquals(packet.receiverId, decoded.receiverId)
        assertEquals(packet.civSequence, decoded.civSequence)
        assertArrayEquals(packet.civFrame, decoded.civFrame)
    }

    @Test
    fun civEnvelopeRejectsMalformedFrameLength() {
        val malformed = golden.copyOf()
        malformed[0x11] = 0x07
        expectProtocolFailure { Ic705CivDatagramCodec.decode(malformed) }
    }

    @Test
    fun civEnvelopeRejectsWrongReceiverId() {
        expectProtocolFailure {
            Ic705CivDatagramCodec.decode(golden, expectedReceiverId = 0x01020304)
        }
    }
}

private fun hex(value: String): ByteArray {
    val compact = value.filterNot(Char::isWhitespace)
    require(compact.length % 2 == 0) { "Hex text must contain complete bytes" }
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private inline fun expectProtocolFailure(block: () -> Unit) {
    try {
        block()
        fail("Expected Ic705ProtocolException")
    } catch (_: Ic705ProtocolException) {
        // Expected.
    }
}
