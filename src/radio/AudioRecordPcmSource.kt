package org.aprsdroid.app.radio

import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.annotation.RequiresPermission
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSource

/**
 * [PcmSource] backed by Android [AudioRecord] with explicit [AudioDeviceInfo] routing.
 */
class AudioRecordPcmSource @RequiresPermission(Manifest.permission.RECORD_AUDIO) constructor(
    override val format: PcmFormat,
    preferredDevice: AudioDeviceInfo? = null,
    val bufferCapacitySamples: Int = DEFAULT_BUFFER_CAPACITY_SAMPLES,
) : PcmSource {

    companion object {
        const val DEFAULT_BUFFER_CAPACITY_SAMPLES = 8192
    }

    private val audioRecord: AudioRecord
    private var closed = false
    private var started = false

    init {
        val minBufferSize = AudioRecord.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSizeBytes = maxOf(minBufferSize, bufferCapacitySamples * 2)

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()

        preferredDevice?.let { audioRecord.preferredDevice = it }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (started || closed) return
        audioRecord.startRecording()
        started = true
    }

    override fun read(buffer: ShortArray, offset: Int, length: Int): Int {
        if (closed) return -1
        if (!started) {
            audioRecord.startRecording()
            started = true
        }
        val res = audioRecord.read(buffer, offset, length)
        return when {
            res > 0 -> res
            res == 0 -> 0
            else -> if (closed) -1 else throw IllegalStateException("AudioRecord.read failed with code $res")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            audioRecord.stop()
            audioRecord.release()
        }
    }
}
