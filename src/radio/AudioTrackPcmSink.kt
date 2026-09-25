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
) : DrainablePcmSink {

    companion object {
        const val DEFAULT_BUFFER_CAPACITY_SAMPLES = 8192
    }

    private val audioTrack: AudioTrack
    private var totalSamplesWritten: Long = 0
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
    }

    override fun write(buffer: ShortArray, offset: Int, length: Int) {
        check(!closed) { "PcmSink is closed" }
        if (length <= 0) return
        if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.play()
        }
        var written = 0
        while (written < length && !closed) {
            val res = audioTrack.write(buffer, offset + written, length - written)
            if (res < 0) {
                throw IllegalStateException("AudioTrack.write failed with code $res")
            }
            written += res
            totalSamplesWritten += res
        }
    }

    override fun flush() {
        // In streaming mode, write() sends data directly to the track ring buffer.
    }

    override fun drain(timeoutMs: Long) {
        if (totalSamplesWritten == 0L || audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) return
        val targetHead = totalSamplesWritten and 0xFFFFFFFFL
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!closed && System.currentTimeMillis() < deadline) {
            val currentHead = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (currentHead >= targetHead) {
                break
            }
            Thread.sleep(10)
        }
        runCatching {
            audioTrack.stop()
        }
    }

    override fun reset() {
        totalSamplesWritten = 0
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
            audioTrack.stop()
            audioTrack.release()
        }
    }
}
