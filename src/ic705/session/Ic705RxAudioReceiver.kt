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
    onDiscontinuity: (Ic705AudioDiscontinuity) -> Unit = {},
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

    private val reorderBuffer = Ic705AudioReorderBuffer(
        writeSamples = sink::write,
        onDiscontinuity = onDiscontinuity,
    )

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

        return reorderBuffer.accept(
            sequence = packet.header.audioSequence,
            samples = Pcm16LittleEndian.decode(packet.pcmPayload),
        )
    }

    @Synchronized
    fun reset() {
        reorderBuffer.reset()
    }
}
