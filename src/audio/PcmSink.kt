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

/**
 * A [PcmSink] capable of blocking until queued audio samples have finished physical playback/drain.
 */
interface DrainablePcmSink : PcmSink {
    /**
     * Blocks until all queued audio samples have completed playback or [timeoutMs] has elapsed.
     */
    fun drain(timeoutMs: Long = DEFAULT_DRAIN_TIMEOUT_MS)

    companion object {
        const val DEFAULT_DRAIN_TIMEOUT_MS = 5000L
    }
}

