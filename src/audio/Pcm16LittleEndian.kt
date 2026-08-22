package org.aprsdroid.app.audio

/** Stateless conversion helpers for signed 16-bit little-endian PCM. */
object Pcm16LittleEndian {
    fun decode(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ): ShortArray {
        checkRange(bytes.size, offset, length)
        require(length % BYTES_PER_SAMPLE == 0) {
            "PCM16LE byte length must be a multiple of $BYTES_PER_SAMPLE"
        }

        val result = ShortArray(length / BYTES_PER_SAMPLE)
        var inputIndex = offset
        for (outputIndex in result.indices) {
            val low = bytes[inputIndex].toInt() and 0xff
            val high = bytes[inputIndex + 1].toInt() and 0xff
            result[outputIndex] = ((high shl 8) or low).toShort()
            inputIndex += BYTES_PER_SAMPLE
        }
        return result
    }

    fun encode(
        samples: ShortArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
    ): ByteArray {
        checkRange(samples.size, offset, length)
        val result = ByteArray(length * BYTES_PER_SAMPLE)
        var outputIndex = 0
        for (i in offset until offset + length) {
            val sample = samples[i].toInt()
            result[outputIndex++] = (sample and 0xff).toByte()
            result[outputIndex++] = ((sample ushr 8) and 0xff).toByte()
        }
        return result
    }

    internal fun decodeToFloats(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        destination: FloatArray,
    ): Int {
        checkRange(bytes.size, offset, length)
        require(length % BYTES_PER_SAMPLE == 0) {
            "PCM16LE byte length must be a multiple of $BYTES_PER_SAMPLE"
        }

        val sampleCount = length / BYTES_PER_SAMPLE
        require(destination.size >= sampleCount) {
            "destination is smaller than the requested sample count"
        }

        var inputIndex = offset
        for (outputIndex in 0 until sampleCount) {
            val low = bytes[inputIndex].toInt() and 0xff
            val high = bytes[inputIndex + 1].toInt() and 0xff
            val sample = ((high shl 8) or low).toShort()
            destination[outputIndex] = sample.toInt() / PCM16_SCALE
            inputIndex += BYTES_PER_SAMPLE
        }
        return sampleCount
    }

    internal fun normalizeToFloats(
        samples: ShortArray,
        offset: Int,
        length: Int,
        destination: FloatArray,
    ): Int {
        checkRange(samples.size, offset, length)
        require(destination.size >= length) {
            "destination is smaller than the requested sample count"
        }

        for (index in 0 until length) {
            destination[index] = samples[offset + index].toInt() / PCM16_SCALE
        }
        return length
    }

    internal fun checkRange(size: Int, offset: Int, length: Int) {
        require(offset >= 0) { "offset must not be negative" }
        require(length >= 0) { "length must not be negative" }
        require(offset <= size - length) { "offset and length exceed the buffer" }
    }

    private const val BYTES_PER_SAMPLE = 2
    private const val PCM16_SCALE = 32768.0f
}
