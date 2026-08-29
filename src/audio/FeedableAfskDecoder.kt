package org.aprsdroid.app.audio

import sivantoledo.ax25.Afsk1200Demodulator
import sivantoledo.ax25.PacketHandler

/**
 * A feedable AFSK1200 decoder with no dependency on Android audio capture.
 *
 * Android builds that package the Graywolf native library use its modern
 * multi-profile / multi-slicer demodulator. Host JVM tests and builds without
 * that optional library keep the established Sivan Toledo Java decoder as a
 * compatibility fallback.
 *
 * This class accepts mono PCM16 samples and forwards decoded raw AX.25 frames
 * to [onPacket]. It is synchronized so a transport can reset or close it safely
 * while its receive loop is active.
 */
class FeedableAfskDecoder(
    override val format: PcmFormat,
    private val onPacket: (ByteArray) -> Unit,
) : PcmSink {
    init {
        require(format.channelCount == 1) { "AFSK1200 decoding requires mono PCM" }
        require(format.encoding == PcmEncoding.PCM_16_LE) {
            "AFSK1200 decoding requires PCM16LE"
        }
    }

    private val packetHandler = object : PacketHandler {
        override fun handlePacket(packet: ByteArray) {
            // Give callback owners a stable frame even if the library reuses a buffer.
            onPacket(packet.copyOf())
        }
    }

    private val graywolf: GraywolfAfskDecoder? = if (GraywolfAfskDecoder.isNativeAvailable) {
        GraywolfAfskDecoder(format, onPacket)
    } else {
        null
    }
    private var demodulator: Afsk1200Demodulator? = if (graywolf == null) createDemodulator() else null
    private var floatBuffer = FloatArray(0)
    private var closed = false

    @Synchronized
    override fun write(buffer: ShortArray, offset: Int, length: Int) {
        ensureOpen()
        Pcm16LittleEndian.checkRange(buffer.size, offset, length)
        if (length == 0) return

        graywolf?.let { nativeDecoder ->
            nativeDecoder.write(buffer, offset, length)
            return
        }

        ensureFloatCapacity(length)
        val sampleCount = Pcm16LittleEndian.normalizeToFloats(
            samples = buffer,
            offset = offset,
            length = length,
            destination = floatBuffer,
        )
        demodulator!!.addSamples(floatBuffer, sampleCount)
    }

    /** Feeds exactly [length] bytes of signed PCM16LE data. */
    @Synchronized
    fun writePcm16Le(
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size - offset,
    ) {
        ensureOpen()
        Pcm16LittleEndian.checkRange(buffer.size, offset, length)
        require(length % format.bytesPerFrame == 0) {
            "PCM byte length must contain complete frames"
        }
        if (length == 0) return

        graywolf?.let { nativeDecoder ->
            nativeDecoder.writePcm16Le(buffer, offset, length)
            return
        }

        val sampleCount = length / PcmEncoding.PCM_16_LE.bytesPerSample
        ensureFloatCapacity(sampleCount)
        val decodedCount = Pcm16LittleEndian.decodeToFloats(
            bytes = buffer,
            offset = offset,
            length = length,
            destination = floatBuffer,
        )
        demodulator!!.addSamples(floatBuffer, decodedCount)
    }

    /** Clears all demodulator history while preserving the selected engine. */
    @Synchronized
    override fun reset() {
        ensureOpen()
        graywolf?.let { nativeDecoder ->
            nativeDecoder.reset()
            return
        }
        // Afsk1200Demodulator exposes no reset method, so rebuilding is intentional.
        demodulator = createDemodulator()
    }

    /** Permanently closes this wrapper and releases native state if present. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        graywolf?.close()
        demodulator = null
        floatBuffer = FloatArray(0)
    }

    private fun createDemodulator() = Afsk1200Demodulator(
        format.sampleRateHz,
        CORRELATION_LENGTH,
        EMPHASIS,
        packetHandler,
    )

    private fun ensureOpen() {
        check(!closed) { "decoder is closed" }
    }

    private fun ensureFloatCapacity(sampleCount: Int) {
        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }
    }

    private companion object {
        // Preserve the parameters used by the legacy AfskDemodulator fallback.
        const val CORRELATION_LENGTH = 1
        const val EMPHASIS = 6
    }
}
