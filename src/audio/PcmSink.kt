package org.aprsdroid.app.audio

import java.io.Closeable

/** Push-based destination for signed PCM samples. */
interface PcmSink : Closeable {
    val format: PcmFormat

    /** Writes exactly [length] valid samples from [buffer] starting at [offset]. */
    fun write(buffer: ShortArray, offset: Int = 0, length: Int = buffer.size - offset)

    /** Completes any buffered writes. Sinks without buffering may leave this as a no-op. */
    fun flush() = Unit

    /**
     * Clears any decoder/sink history. Sinks without buffered history may leave this as a no-op.
     */
    fun reset() = Unit
}
