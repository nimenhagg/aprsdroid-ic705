package org.aprsdroid.app.audio

import java.io.Closeable

/** Pull-based source of signed PCM samples. */
interface PcmSource : Closeable {
    val format: PcmFormat

    /**
     * Reads at most [length] valid samples into [buffer] starting at [offset].
     * Returns the number of samples read, or -1 after end-of-stream.
     */
    fun read(buffer: ShortArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
