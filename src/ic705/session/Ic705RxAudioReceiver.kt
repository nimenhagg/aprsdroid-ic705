package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.audio.Pcm16LittleEndian
import org.aprsdroid.app.audio.PcmEncoding
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705ProtocolException

enum class Ic705AudioDiscontinuityKind {
    GAP,
    OUT_OF_ORDER,
}

data class Ic705AudioDiscontinuity(
    val kind: Ic705AudioDiscontinuityKind,
    val expectedSequence: Int,
    val actualSequence: Int,
    val missingPacketCount: Int,
)

enum class Ic705AudioReceiveResult {
    ACCEPTED,
    BUFFERED,
    DUPLICATE_DROPPED,
    OUT_OF_ORDER_DROPPED,
}

/**
 * Validates IC-705 LAN audio datagrams and pushes their PCM into an external sink.
 *
 * This class owns neither the socket nor [sink]. It performs no control, CI-V, PTT,
 * or transmit operation. Call [reset] after a network discontinuity so demodulator
 * history is not treated as continuous across separate radio sessions.
 */
class Ic705RxAudioReceiver(
    private val localId: Int,
    private val radioId: Int? = null,
    private val sink: PcmSink,
    private val onDiscontinuity: (Ic705AudioDiscontinuity) -> Unit = {},
) {
    init {
        require(sink.format.sampleRateHz == Ic705AudioPacketCodec.SAMPLE_RATE_HZ) {
            "IC-705 receive audio requires ${Ic705AudioPacketCodec.SAMPLE_RATE_HZ} Hz PCM"
        }
        require(sink.format.channelCount == 1) {
            "IC-705 receive audio requires mono PCM"
        }
        require(sink.format.encoding == PcmEncoding.PCM_16_LE) {
            "IC-705 receive audio requires PCM16LE"
        }
    }

    private var nextAudioSequence: Int? = null
    private var lastDeliveredSequence: Int? = null
    private val pendingAudio = mutableMapOf<Int, ShortArray>()

    @Synchronized
    fun accept(datagram: ByteArray): Ic705AudioReceiveResult {
        val packet = Ic705AudioPacketCodec.decode(datagram, expectedReceiverId = localId)
        if (radioId != null && packet.header.senderId != radioId) {
            throw Ic705ProtocolException(
                "Audio sender ID ${packet.header.senderId} does not match radio ID $radioId",
            )
        }
        if (packet.pcmPayload.size % sink.format.bytesPerFrame != 0) {
            throw Ic705ProtocolException(
                "Audio payload has ${packet.pcmPayload.size} bytes, not complete PCM frames",
            )
        }

        val actualSequence = packet.header.audioSequence
        if (lastDeliveredSequence == actualSequence || pendingAudio.containsKey(actualSequence)) {
            return Ic705AudioReceiveResult.DUPLICATE_DROPPED
        }

        val samples = Pcm16LittleEndian.decode(packet.pcmPayload)
        val expectedSequence = nextAudioSequence
        if (expectedSequence == null) {
            deliver(actualSequence, samples)
            return Ic705AudioReceiveResult.ACCEPTED
        }

        val forwardDistance = ic705AudioSequenceDistance(expectedSequence, actualSequence)
        return when {
            forwardDistance == 0 -> {
                deliver(actualSequence, samples)
                drainContiguousPending()
                Ic705AudioReceiveResult.ACCEPTED
            }
            forwardDistance >= IC705_AUDIO_HALF_SEQUENCE_SPACE -> {
                // This packet arrived after its place in the output stream. The gap was
                // already handled when newer audio was released, so resetting again here
                // would turn one network discontinuity into two demodulator resets.
                Ic705AudioReceiveResult.OUT_OF_ORDER_DROPPED
            }
            forwardDistance > IC705_AUDIO_MAX_REORDER_PACKETS -> {
                reportGapAndDeliver(expectedSequence, actualSequence, samples)
                drainContiguousPending()
                Ic705AudioReceiveResult.ACCEPTED
            }
            else -> {
                pendingAudio[actualSequence] = samples
                if (pendingAudio.size >= IC705_AUDIO_MAX_REORDER_PACKETS) {
                    releaseNearestPending(expectedSequence)
                    Ic705AudioReceiveResult.ACCEPTED
                } else {
                    Ic705AudioReceiveResult.BUFFERED
                }
            }
        }
    }

    @Synchronized
    fun reset() {
        nextAudioSequence = null
        lastDeliveredSequence = null
        pendingAudio.clear()
    }

    private fun deliver(sequence: Int, samples: ShortArray) {
        sink.write(samples)
        lastDeliveredSequence = sequence
        nextAudioSequence = incrementIc705AudioSequence(sequence)
    }

    private fun drainContiguousPending() {
        while (true) {
            val expected = nextAudioSequence ?: return
            val samples = pendingAudio.remove(expected) ?: return
            deliver(expected, samples)
        }
    }

    private fun releaseNearestPending(expectedSequence: Int) {
        val sequence = pendingAudio.keys.minByOrNull { ic705AudioSequenceDistance(expectedSequence, it) } ?: return
        val samples = pendingAudio.remove(sequence) ?: return
        reportGapAndDeliver(expectedSequence, sequence, samples)
        drainContiguousPending()
    }

    private fun reportGapAndDeliver(expectedSequence: Int, actualSequence: Int, samples: ShortArray) {
        val missingPacketCount = ic705AudioSequenceDistance(expectedSequence, actualSequence)
        if (missingPacketCount <= IC705_AUDIO_MAX_CONCEALED_PACKETS) {
            val concealedSampleCount = (0 until missingPacketCount).sumOf { offset ->
                ic705SamplesPerReceivePacket((expectedSequence + offset) and 0xffff)
            }
            val concealed = ShortArray(concealedSampleCount + samples.size)
            samples.copyInto(concealed, destinationOffset = concealedSampleCount)
            deliver(actualSequence, concealed)
            return
        }
        onDiscontinuity(
            Ic705AudioDiscontinuity(
                kind = Ic705AudioDiscontinuityKind.GAP,
                expectedSequence = expectedSequence,
                actualSequence = actualSequence,
                missingPacketCount = missingPacketCount,
            ),
        )
        deliver(actualSequence, samples)
    }
}
