package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705ProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Ic705RxAudioReceiverTest {
    private val localId = 0x11223344
    private val radioId = 0x55667788

    @Test
    fun decodesPcm16LeAndWritesItToSink() {
        val sink = RecordingSink()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink)

        val result = receiver.accept(audioDatagram(sequence = 7, pcm = byteArrayOf(
            0x34, 0x12,
            0x00, 0x80.toByte(),
        )))

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, result)
        assertArrayEquals(shortArrayOf(0x1234, Short.MIN_VALUE), sink.samples.toShortArray())
    }

    @Test
    fun dropsAnExactDuplicateWithoutWritingItTwice() {
        val sink = RecordingSink()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink)
        val datagram = audioDatagram(sequence = 7)

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, receiver.accept(datagram))
        assertEquals(Ic705AudioReceiveResult.DUPLICATE_DROPPED, receiver.accept(datagram))
        assertEquals(1, sink.samples.size)
    }

    @Test
    fun reordersAShortForwardGapWithoutResettingTheDecoder() {
        val sink = RecordingSink()
        val discontinuities = mutableListOf<Ic705AudioDiscontinuity>()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink, discontinuities::add)

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, receiver.accept(audioDatagram(sequence = 7)))
        assertEquals(Ic705AudioReceiveResult.BUFFERED, receiver.accept(audioDatagram(sequence = 9)))
        assertEquals(Ic705AudioReceiveResult.ACCEPTED, receiver.accept(audioDatagram(sequence = 8)))

        assertEquals(3, sink.samples.size)
        assertEquals(emptyList<Ic705AudioDiscontinuity>(), discontinuities)
    }

    @Test
    fun concealsAnIsolatedLossAndDropsTheLatePacketWithoutResetting() {
        val sink = RecordingSink()
        val discontinuities = mutableListOf<Ic705AudioDiscontinuity>()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink, discontinuities::add)

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, receiver.accept(audioDatagram(sequence = 7)))
        assertEquals(Ic705AudioReceiveResult.BUFFERED, receiver.accept(audioDatagram(sequence = 9)))
        assertEquals(Ic705AudioReceiveResult.BUFFERED, receiver.accept(audioDatagram(sequence = 10)))
        assertEquals(Ic705AudioReceiveResult.BUFFERED, receiver.accept(audioDatagram(sequence = 11)))
        assertEquals(
            Ic705AudioReceiveResult.ACCEPTED,
            receiver.accept(audioDatagram(sequence = 12)),
        )
        assertEquals(emptyList<Ic705AudioDiscontinuity>(), discontinuities)

        assertEquals(
            Ic705AudioReceiveResult.OUT_OF_ORDER_DROPPED,
            receiver.accept(audioDatagram(sequence = 8, pcm = byteArrayOf(0x02, 0x00))),
        )
        // Sequence 8 is the 682-sample half of the alternating IC-705 RX pair.
        assertEquals(1 + 682 + 4, sink.samples.size)
        assertEquals(0, discontinuities.size)
    }

    @Test
    fun reportsALargerGapInsteadOfConcealingASevereDiscontinuity() {
        val sink = RecordingSink()
        val discontinuities = mutableListOf<Ic705AudioDiscontinuity>()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink, discontinuities::add)

        receiver.accept(audioDatagram(sequence = 7))
        assertEquals(Ic705AudioReceiveResult.ACCEPTED, receiver.accept(audioDatagram(sequence = 20)))

        assertEquals(
            Ic705AudioDiscontinuity(
                kind = Ic705AudioDiscontinuityKind.GAP,
                expectedSequence = 8,
                actualSequence = 20,
                missingPacketCount = 12,
            ),
            discontinuities.single(),
        )
        assertEquals(2, sink.samples.size)
    }

    @Test
    fun acceptsSequenceWrapWithoutAFalseGap() {
        val sink = RecordingSink()
        val discontinuities = mutableListOf<Ic705AudioDiscontinuity>()
        val receiver = Ic705RxAudioReceiver(localId, radioId, sink, discontinuities::add)

        receiver.accept(audioDatagram(sequence = 0xffff))
        receiver.accept(audioDatagram(sequence = 0))

        assertEquals(2, sink.samples.size)
        assertEquals(emptyList<Ic705AudioDiscontinuity>(), discontinuities)
    }

    @Test(expected = Ic705ProtocolException::class)
    fun rejectsAudioFromAnotherRadioSession() {
        val receiver = Ic705RxAudioReceiver(localId, radioId, RecordingSink())
        receiver.accept(audioDatagram(sequence = 1, senderId = 0x01020304))
    }

    @Test(expected = Ic705ProtocolException::class)
    fun rejectsAnIncompletePcmFrame() {
        val receiver = Ic705RxAudioReceiver(localId, radioId, RecordingSink())
        receiver.accept(audioDatagram(sequence = 1, pcm = byteArrayOf(0x01)))
    }

    private fun audioDatagram(
        sequence: Int,
        senderId: Int = radioId,
        pcm: ByteArray = byteArrayOf(0x01, 0x00),
    ): ByteArray = Ic705AudioPacketCodec.encode(
        sequence = sequence,
        senderId = senderId,
        receiverId = localId,
        audioSequence = sequence,
        pcmPayload = pcm,
    )

    private class RecordingSink : PcmSink {
        override val format = PcmFormat(sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ)
        val samples = mutableListOf<Short>()

        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            for (index in offset until offset + length) samples += buffer[index]
        }

        override fun close() = Unit
    }
}
