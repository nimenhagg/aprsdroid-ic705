package org.aprsdroid.app.audio

import org.junit.Test

class FeedableAfskDecoderTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonMonoPcm() {
        FeedableAfskDecoder(PcmFormat(8_000, channelCount = 2)) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompletePcmFrame() {
        val decoder = FeedableAfskDecoder(PcmFormat(8_000)) { }
        try {
            decoder.writePcm16Le(byteArrayOf(0x00))
        } finally {
            decoder.close()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsSamplesAfterClose() {
        val decoder = FeedableAfskDecoder(PcmFormat(8_000)) { }
        decoder.close()

        decoder.write(shortArrayOf(0))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsResetAfterClose() {
        val decoder = FeedableAfskDecoder(PcmFormat(8_000)) { }
        decoder.close()

        decoder.reset()
    }
}
