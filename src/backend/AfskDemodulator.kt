package org.aprsdroid.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import androidx.core.content.ContextCompat
import sivantoledo.ax25.Afsk1200Demodulator

class AfskDemodulator(
    private val au: AfskUploader,
    private val inType: Int,
    private val samplerate: Int
) : Thread("AFSK demodulator") {

    companion object {
        const val TAG = "APRSdroid.AfskDemod"
        const val BUF_SIZE = 8192
    }

    private val bufferS = ShortArray(BUF_SIZE)
    private val bufferF = FloatArray(BUF_SIZE)
    private val demod = Afsk1200Demodulator(samplerate, 1, 6, au)
    private var recorder: AudioRecord? = null

    init {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
    }

    override fun run() {
        Log.d(TAG, "running...")
        if (ContextCompat.checkSelfPermission(au.service, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Audio recording permission is not granted")
            au.postAbort("Audio recording permission is not granted")
            return
        }
        try {
            var zeroReads = 0
            val rec = AudioRecord(
                inType, samplerate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                4 * BUF_SIZE
            )
            recorder = rec
            rec.startRecording()
            while (!isInterrupted && (rec.recordingState != AudioRecord.RECORDSTATE_STOPPED)) {
                val count = rec.read(bufferS, 0, BUF_SIZE)
                Log.d(TAG, "read $count samples")
                if (count == 0) {
                    zeroReads += 1
                    if (zeroReads == 10) throw RuntimeException("recorder.read() not delivering data!")
                } else if (count < 0) {
                    throw RuntimeException("recorder.read() = $count")
                } else {
                    zeroReads = 0
                }

                for (i in 0 until count) {
                    bufferF[i] = bufferS[i].toFloat() / 32768.0f
                }

                demod.addSamples(bufferF, count)
                au.notifyMicLevel(demod.peak())
            }
        } catch (e: Exception) {
            Log.e(TAG, "run(): $e")
            e.printStackTrace()
            au.postAbort(e.toString())
        }
        Log.d(TAG, "closed.")
    }

    fun close() {
        try {
            interrupt()
            recorder?.stop()
            join(50)
            recorder?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "close(): $e")
        } catch (_: NullPointerException) {
        }
    }
}
