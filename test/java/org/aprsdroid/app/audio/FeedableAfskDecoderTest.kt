package org.aprsdroid.app.audio

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Test

class FeedableAfskDecoderTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonMonoPcmBeforeNativeInitialization() {
        FeedableAfskDecoder(PcmFormat(8_000, channelCount = 2)) { }
    }

    @Test
    fun missingGraywolfNativeIsFatalInsteadOfFallingBack() {
        assumeFalse(
            "This host-JVM contract test only applies when the Android Graywolf library is absent",
            GraywolfAfskDecoder.isNativeAvailable,
        )

        try {
            FeedableAfskDecoder(PcmFormat(8_000)) { }
            fail("IC-705 decoder must not silently fall back when Graywolf native is unavailable")
        } catch (expected: IllegalStateException) {
            assertTrue(
                expected.message?.contains("Graywolf native decoder is unavailable") == true ||
                    expected.cause != null,
            )
        }
    }
}
