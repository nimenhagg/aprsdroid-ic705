package org.aprsdroid.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat

/**
 * Microphone / routed-audio AFSK1200 receiver.
 *
 * Production demodulation is Graywolf-only. The legacy Java and multimon receive
 * paths are intentionally not used here; the legacy modem dependency remains
 * available only for transmit-side audio generation.
 */
class AfskDemodulator(
    private val au: AfskUploader,
    private val inType: Int,
    private val samplerate: Int,
) : Thread("AFSK demodulator") {

    companion object {
        const val TAG = "APRSdroid.AfskDemod"
        const val BUF_SIZE = 8192
    }

    private val bufferS = ShortArray(BUF_SIZE)

    @Volatile
    private var recorder: AudioRecord? = null

    override fun run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        Log.d(TAG, "running Graywolf at $samplerate Hz...")

        if (
            ContextCompat.checkSelfPermission(au.service, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Audio recording permission is not granted")
            au.postAbort("Audio recording permission is not granted")
            return
        }

        var rec: AudioRecord? = null
        var decoder: FeedableAfskDecoder? = null
        try {
            rec = AudioRecord(
                inType,
                samplerate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                4 * BUF_SIZE,
            )
            recorder = rec

            decoder = FeedableAfskDecoder(PcmFormat(sampleRateHz = samplerate)) { frame ->
                au.handlePacket(frame)
            }

            rec.startRecording()
            var zeroReads = 0
            while (!isInterrupted && rec.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                val count = rec.read(bufferS, 0, BUF_SIZE)
                Log.d(TAG, "read $count samples")
                when {
                    count == 0 -> {
                        zeroReads += 1
                        if (zeroReads == 10) {
                            throw RuntimeException("recorder.read() not delivering data!")
                        }
                    }
                    count < 0 -> throw RuntimeException("recorder.read() = $count")
                    else -> {
                        zeroReads = 0
                        decoder.write(bufferS, 0, count)
                        au.notifyMicLevel(micLevel(bufferS, count))
                    }
                }
            }
        } catch (e: LinkageError) {
            Log.e(TAG, "Graywolf native decoder linkage failure", e)
            au.postAbort("Graywolf native decoder unavailable: $e")
        } catch (e: Exception) {
            Log.e(TAG, "run(): $e", e)
            au.postAbort(e.toString())
        } finally {
            runCatching { decoder?.close() }
                .onFailure { Log.w(TAG, "decoder close failed", it) }
            val localRec = rec
            if (localRec != null) {
                runCatching {
                    if (localRec.recordingState != AudioRecord.RECORDSTATE_STOPPED) localRec.stop()
                }.onFailure { Log.w(TAG, "AudioRecord stop failed", it) }
                runCatching { localRec.release() }
                    .onFailure { Log.w(TAG, "AudioRecord release failed", it) }
            }
            if (recorder === localRec) recorder = null
            Log.d(TAG, "closed.")
        }
    }

    fun close() {
        interrupt()
        recorder?.let { rec ->
            runCatching { rec.stop() }
                .onFailure { Log.w(TAG, "close(): stop failed", it) }
        }
        try {
            join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun micLevel(samples: ShortArray, count: Int): Int {
        var peak = 0
        for (index in 0 until count) {
            peak = maxOf(peak, abs(samples[index].toInt()))
        }
        return ((peak * 100L) / 32768L).toInt().coerceIn(0, 100)
    }
}
