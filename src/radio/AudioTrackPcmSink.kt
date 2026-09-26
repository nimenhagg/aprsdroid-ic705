package org.aprsdroid.app.radio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import org.aprsdroid.app.audio.DrainablePcmSink
import org.aprsdroid.app.audio.PcmFormat

/**
 * [DrainablePcmSink] backed by Android [AudioTrack] with explicit [AudioDeviceInfo] routing and TX drain.
 */
class AudioTrackPcmSink(
    override val format: PcmFormat,
    preferredDevice: AudioDeviceInfo? = null,
    val bufferCapacitySamples: Int = DEFAULT_BUFFER_CAPACITY_SAMPLES,
    val drainTailMarginMs: Long = DEFAULT_DRAIN_TAIL_MARGIN_MS,
    val txVolumePercent: Int = DEFAULT_TX_VOLUME_PERCENT,
) : DrainablePcmSink {

    companion object {
        const val DEFAULT_BUFFER_CAPACITY_SAMPLES = 8192
        const val DEFAULT_DRAIN_TAIL_MARGIN_MS = 100L
        const val DEFAULT_TX_VOLUME_PERCENT = 100

        /**
         * Calculates the maximum expected duration (in ms) to wait for [samples] to physically play out
         * at [sampleRateHz], accounting for elapsed time since transmission began and a tail margin.
         */
        fun calculateDrainTimeoutMs(
            samples: Long,
            sampleRateHz: Int,
            elapsedSinceStartMs: Long = 0L,
            tailMarginMs: Long = DEFAULT_DRAIN_TAIL_MARGIN_MS,
            maxTimeoutCeilingMs: Long = 5000L,
        ): Long {
            if (samples <= 0L || sampleRateHz <= 0) return 0L
            val totalAudioDurationMs = (samples * 1000L) / sampleRateHz
            val remainingAudioMs = (totalAudioDurationMs - elapsedSinceStartMs).coerceAtLeast(0L)
            val calculatedWaitMs = remainingAudioMs + tailMarginMs
            return minOf(maxTimeoutCeilingMs, calculatedWaitMs)
        }
    }

    private val audioTrack: AudioTrack
    private var totalSamplesWritten: Long = 0
    private var burstSamplesWritten: Long = 0
    private var burstStartTimeMs: Long = 0
    private var burstStartHeadPosition: Long = 0
    private var closed = false

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSizeBytes = maxOf(minBufferSize, bufferCapacitySamples * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        preferredDevice?.let { audioTrack.preferredDevice = it }
        setVolume(txVolumePercent)
    }

    /**
     * Dynamically adjusts the TX audio output volume percentage (0..100).
     */
    fun setVolume(volumePercent: Int) {
        val gain = volumePercent.coerceIn(0, 100) / 100.0f
        runCatching { audioTrack.setVolume(gain) }
    }

    override fun write(buffer: ShortArray, offset: Int, length: Int) {
        check(!closed) { "PcmSink is closed" }
        if (length <= 0) return
        if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.play()
        }
        if (burstSamplesWritten == 0L) {
            burstStartTimeMs = System.currentTimeMillis()
            burstStartHeadPosition = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        }
        var written = 0
        while (written < length && !closed) {
            val res = audioTrack.write(buffer, offset + written, length - written)
            if (res < 0) {
                throw IllegalStateException("AudioTrack.write failed with code $res")
            }
            written += res
            burstSamplesWritten += res
            totalSamplesWritten += res
        }
    }

    override fun flush() {
        // In streaming mode, write() sends data directly to the track ring buffer.
    }

    override fun drain(timeoutMs: Long) {
        if (burstSamplesWritten == 0L || audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) return

        val burstDurationMs = (burstSamplesWritten * 1000L) / format.sampleRateHz
        val elapsedSinceStartMs = System.currentTimeMillis() - burstStartTimeMs
        val remainingWaitMs = calculateDrainTimeoutMs(
            samples = burstSamplesWritten,
            sampleRateHz = format.sampleRateHz,
            elapsedSinceStartMs = elapsedSinceStartMs,
            tailMarginMs = drainTailMarginMs,
            maxTimeoutCeilingMs = timeoutMs,
        )
        val deadline = System.currentTimeMillis() + remainingWaitMs
        val targetHead = burstStartHeadPosition + burstSamplesWritten

        var lastHead = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        var stallCount = 0

        while (!closed && System.currentTimeMillis() < deadline) {
            val currentHead = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (currentHead >= targetHead) {
                // Playback head reached or surpassed all written samples; brief tail for DAC output
                Thread.sleep(minOf(drainTailMarginMs, 50L))
                break
            }

            // If real elapsed time exceeds total audio duration, check if head position has stalled (underrun)
            if (System.currentTimeMillis() >= burstStartTimeMs + burstDurationMs) {
                if (currentHead == lastHead) {
                    stallCount++
                    if (stallCount >= 3) {
                        // Position hasn't advanced for 30ms past audio duration; buffer is drained
                        Thread.sleep(minOf(drainTailMarginMs, 50L))
                        break
                    }
                } else {
                    stallCount = 0
                    lastHead = currentHead
                }
            }

            Thread.sleep(10)
        }

        runCatching {
            audioTrack.stop()
            audioTrack.flush()
        }
        burstSamplesWritten = 0L
        burstStartTimeMs = 0L
        burstStartHeadPosition = 0L
    }

    override fun reset() {
        burstSamplesWritten = 0L
        burstStartTimeMs = 0L
        burstStartHeadPosition = 0L
        totalSamplesWritten = 0L
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        burstSamplesWritten = 0L
        burstStartTimeMs = 0L
        burstStartHeadPosition = 0L
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
            audioTrack.stop()
            audioTrack.release()
        }
    }
}
