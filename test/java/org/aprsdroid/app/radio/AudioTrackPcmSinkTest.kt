package org.aprsdroid.app.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTrackPcmSinkTest {

    @Test
    fun calculateDrainTimeoutCalculatesExactDurationPlusTail() {
        // 16000 samples at 16000 Hz = 1000ms audio
        // Elapsed = 0ms, Tail = 100ms -> 1100ms
        val timeout = AudioTrackPcmSink.calculateDrainTimeoutMs(
            samples = 16000,
            sampleRateHz = 16000,
            elapsedSinceStartMs = 0L,
            tailMarginMs = 100L,
        )
        assertEquals(1100L, timeout)
    }

    @Test
    fun calculateDrainTimeoutDeductsElapsedWriteTime() {
        // 11960 samples at 16000 Hz = 747ms audio
        // Elapsed = 250ms -> Remaining audio = 497ms
        // Tail = 100ms -> Calculated wait = 597ms
        val timeout = AudioTrackPcmSink.calculateDrainTimeoutMs(
            samples = 11960,
            sampleRateHz = 16000,
            elapsedSinceStartMs = 250L,
            tailMarginMs = 100L,
        )
        assertEquals(597L, timeout)
    }

    @Test
    fun calculateDrainTimeoutReturnsOnlyTailWhenAudioAlreadyElapsed() {
        // 11960 samples at 16000 Hz = 747ms audio
        // Elapsed = 900ms (> 747ms) -> Remaining audio = 0ms
        // Tail = 100ms -> Calculated wait = 100ms
        val timeout = AudioTrackPcmSink.calculateDrainTimeoutMs(
            samples = 11960,
            sampleRateHz = 16000,
            elapsedSinceStartMs = 900L,
            tailMarginMs = 100L,
        )
        assertEquals(100L, timeout)
    }

    @Test
    fun calculateDrainTimeoutRespectsMaxCeiling() {
        // 160000 samples at 16000 Hz = 10000ms audio
        // Max ceiling = 2000ms -> should cap at 2000ms
        val timeout = AudioTrackPcmSink.calculateDrainTimeoutMs(
            samples = 160000,
            sampleRateHz = 16000,
            elapsedSinceStartMs = 0L,
            tailMarginMs = 100L,
            maxTimeoutCeilingMs = 2000L,
        )
        assertEquals(2000L, timeout)
    }

    @Test
    fun calculateDrainTimeoutHandlesZeroOrNegativeSamplesGracefully() {
        assertEquals(
            0L,
            AudioTrackPcmSink.calculateDrainTimeoutMs(0, 16000),
        )
        assertEquals(
            0L,
            AudioTrackPcmSink.calculateDrainTimeoutMs(-50, 16000),
        )
        assertEquals(
            0L,
            AudioTrackPcmSink.calculateDrainTimeoutMs(1000, 0),
        )
    }
}
