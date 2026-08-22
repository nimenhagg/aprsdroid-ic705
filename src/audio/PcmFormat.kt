package org.aprsdroid.app.audio

/** PCM encodings supported by the radio/audio boundary. */
enum class PcmEncoding(val bytesPerSample: Int) {
    PCM_16_LE(2),
}

/**
 * Description of interleaved PCM frames.
 *
 * Samples using [PcmEncoding.PCM_16_LE] are signed 16-bit values with the least
 * significant byte first. A frame contains one sample for each channel.
 */
data class PcmFormat(
    val sampleRateHz: Int,
    val channelCount: Int = 1,
    val encoding: PcmEncoding = PcmEncoding.PCM_16_LE,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
    }

    val bytesPerFrame: Int = channelCount * encoding.bytesPerSample
}
