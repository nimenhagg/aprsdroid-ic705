package org.aprsdroid.app.audio

/**
 * Feedable AFSK1200 decoder for the IC-705 receive path.
 *
 * Graywolf is mandatory for this production path. If the native library cannot
 * be loaded or initialized, construction fails immediately instead of silently
 * falling back to the legacy Java demodulator.
 *
 * This class accepts mono PCM16 samples and forwards decoded raw AX.25 frames
 * to [onPacket]. It is synchronized so a transport can reset or close it safely
 * while its receive loop is active.
 */
class FeedableAfskDecoder(
    override val format: PcmFormat,
    onPacket: (ByteArray) -> Unit,
) : PcmSink {
    init {
        require(format.channelCount == 1) { "AFSK1200 decoding requires mono PCM" }
        require(format.encoding == PcmEncoding.PCM_16_LE) {
            "AFSK1200 decoding requires PCM16LE"
        }
    }

    private val graywolf = GraywolfAfskDecoder(format, onPacket)
    private var closed = false

    @Synchronized
    override fun write(buffer: ShortArray, offset: Int, length: Int) {
        ensureOpen()
        graywolf.write(buffer, offset, length)
    }

    /** Feeds exactly [length] bytes of signed PCM16LE data. */
    @Synchronized
    fun writePcm16Le(
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size - offset,
    ) {
        ensureOpen()
        graywolf.writePcm16Le(buffer, offset, length)
    }

    /** Clears all Graywolf demodulator history. */
    @Synchronized
    override fun reset() {
        ensureOpen()
        graywolf.reset()
    }

    /** Permanently closes this wrapper and releases Graywolf native state. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        graywolf.close()
    }

    private fun ensureOpen() {
        check(!closed) { "decoder is closed" }
    }
}
