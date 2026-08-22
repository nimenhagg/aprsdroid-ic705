package org.aprsdroid.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmFormatTest {
    @Test
    fun computesBytesPerFrame() {
        val format = PcmFormat(sampleRateHz = 48_000, channelCount = 2)

        assertEquals(4, format.bytesPerFrame)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveSampleRate() {
        PcmFormat(sampleRateHz = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveChannelCount() {
        PcmFormat(sampleRateHz = 8_000, channelCount = 0)
    }
}
