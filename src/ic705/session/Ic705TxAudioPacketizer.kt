package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.audio.Pcm16LittleEndian
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec

/**
 * Slices 12 kHz PCM audio streams into sequential IC-705 audio UDP datagrams
 * with appropriate prime and trailing silence padding.
 */
class Ic705TxAudioPacketizer(
    val senderId: Int,
    val receiverId: Int,
    initialOuterSequence: Int = 1,
    initialAudioSequence: Int = 1,
    val samplesPerPacket: Int = Ic705AudioPacketCodec.SAMPLES_PER_PACKET,
) {
    init {
        require(samplesPerPacket > 0) { "samplesPerPacket must be positive" }
    }

    private var currentOuterSequence: Int = initialOuterSequence and 0xffff
    private var currentAudioSequence: Int = initialAudioSequence and 0xffff

    val outerSequence: Int
        get() = currentOuterSequence

    val audioSequence: Int
        get() = currentAudioSequence

    /**
     * Converts a stream of 16-bit PCM [samples] into a list of UDP datagrams ready to send,
     * adding prime and trailing silence.
     */
    @Synchronized
    fun packetize(
        samples: ShortArray,
        primeSilenceMs: Int = DEFAULT_PRIME_SILENCE_MS,
        trailingSilenceMs: Int = DEFAULT_TRAILING_SILENCE_MS,
        sampleRateHz: Int = Ic705AudioPacketCodec.SAMPLE_RATE_HZ,
    ): List<ByteArray> {
        val primeSamples = (primeSilenceMs * sampleRateHz) / 1000
        val trailingSamples = (trailingSilenceMs * sampleRateHz) / 1000
        val totalRawSamples = primeSamples + samples.size + trailingSamples

        // Pad to whole packet boundary
        val remainder = totalRawSamples % samplesPerPacket
        val paddingNeeded = if (remainder == 0) 0 else samplesPerPacket - remainder
        val totalSamples = totalRawSamples + paddingNeeded

        val combined = ShortArray(totalSamples)
        // Prime silence is all 0 (already default in ShortArray)
        // Copy audio payload
        System.arraycopy(samples, 0, combined, primeSamples, samples.size)
        // Trailing silence and padding remain 0

        val datagrams = ArrayList<ByteArray>(totalSamples / samplesPerPacket)
        val chunk = ShortArray(samplesPerPacket)

        for (offset in 0 until totalSamples step samplesPerPacket) {
            System.arraycopy(combined, offset, chunk, 0, samplesPerPacket)
            val pcmBytes = Pcm16LittleEndian.encode(chunk)
            val datagram = Ic705AudioPacketCodec.encode(
                sequence = currentOuterSequence,
                senderId = senderId,
                receiverId = receiverId,
                audioSequence = currentAudioSequence,
                pcmPayload = pcmBytes,
            )
            datagrams.add(datagram)
            currentOuterSequence = (currentOuterSequence + 1) and 0xffff
            currentAudioSequence = (currentAudioSequence + 1) and 0xffff
        }

        return datagrams
    }

    companion object {
        const val DEFAULT_PRIME_SILENCE_MS = 60
        const val DEFAULT_TRAILING_SILENCE_MS = 60
    }
}
