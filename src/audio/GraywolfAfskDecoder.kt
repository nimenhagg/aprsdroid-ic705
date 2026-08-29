package org.aprsdroid.app.audio

/**
 * Feedable Bell 202 / AX.25 decoder backed by Graywolf's modern Rust demodulator.
 *
 * The native library is optional at runtime so host JVM tests and developer builds
 * that have not built the Rust artifact can continue to use the legacy Java path.
 */
internal class GraywolfAfskDecoder(
    override val format: PcmFormat,
    private val onPacket: (ByteArray) -> Unit,
) : PcmSink {
    init {
        require(format.channelCount == 1) { "AFSK1200 decoding requires mono PCM" }
        require(format.encoding == PcmEncoding.PCM_16_LE) {
            "AFSK1200 decoding requires PCM16LE"
        }
    }

    private var nativeHandle: Long = GraywolfNative.create(format.sampleRateHz).also {
        check(it != 0L) { "Graywolf returned an invalid decoder handle" }
    }
    private var shortBuffer = ShortArray(0)

    @Synchronized
    override fun write(buffer: ShortArray, offset: Int, length: Int) {
        ensureOpen()
        Pcm16LittleEndian.checkRange(buffer.size, offset, length)
        if (length == 0) return
        process(buffer, offset, length)
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

        val sampleCount = length / PcmEncoding.PCM_16_LE.bytesPerSample
        if (shortBuffer.size < sampleCount) shortBuffer = ShortArray(sampleCount)
        var source = offset
        for (index in 0 until sampleCount) {
            shortBuffer[index] = (
                (buffer[source].toInt() and 0xff) or
                    (buffer[source + 1].toInt() shl 8)
                ).toShort()
            source += 2
        }
        process(shortBuffer, 0, sampleCount)
    }

    @Synchronized
    override fun reset() {
        ensureOpen()
        // Construct first so a failed replacement leaves the current decoder usable.
        val replacement = GraywolfNative.create(format.sampleRateHz)
        check(replacement != 0L) { "Graywolf returned an invalid replacement decoder handle" }
        val old = nativeHandle
        nativeHandle = replacement
        GraywolfNative.destroy(old)
    }

    @Synchronized
    override fun close() {
        val handle = nativeHandle
        if (handle == 0L) return
        nativeHandle = 0L
        shortBuffer = ShortArray(0)
        GraywolfNative.destroy(handle)
    }

    private fun process(buffer: ShortArray, offset: Int, length: Int) {
        for (packet in GraywolfNative.process(nativeHandle, buffer, offset, length)) {
            onPacket(packet.copyOf())
        }
    }

    private fun ensureOpen() {
        check(nativeHandle != 0L) { "decoder is closed" }
    }

    companion object {
        val isNativeAvailable: Boolean
            get() = GraywolfNative.isAvailable
    }
}

private object GraywolfNative {
    private const val LIBRARY_NAME = "aprs_graywolf"

    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary(LIBRARY_NAME)
    }.exceptionOrNull()

    val isAvailable: Boolean
        get() = loadFailure == null

    fun create(sampleRateHz: Int): Long {
        ensureLoaded()
        return nativeCreate(sampleRateHz)
    }

    fun process(
        handle: Long,
        samples: ShortArray,
        offset: Int,
        length: Int,
    ): Array<ByteArray> {
        ensureLoaded()
        return nativeProcess(handle, samples, offset, length)
    }

    fun destroy(handle: Long) {
        if (!isAvailable) return
        nativeDestroy(handle)
    }

    private fun ensureLoaded() {
        loadFailure?.let { failure ->
            throw IllegalStateException("Graywolf native decoder is unavailable", failure)
        }
    }

    private external fun nativeCreate(sampleRateHz: Int): Long

    private external fun nativeProcess(
        handle: Long,
        samples: ShortArray,
        offset: Int,
        length: Int,
    ): Array<ByteArray>

    private external fun nativeDestroy(handle: Long)
}
