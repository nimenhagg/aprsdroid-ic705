package org.aprsdroid.app

import com.jazzido.PacketDroid.AudioBufferProcessor

class AfskInWrapper(private val hq: Boolean, au: AfskUploader, inType: Int, samplerate: Int) {
    private var abp: AudioBufferProcessor? = if (!hq) AudioBufferProcessor(au.service, au) else null
    private var ad: AfskDemodulator? = if (hq) AfskDemodulator(au, inType, samplerate) else null

    fun start() {
        if (!hq) abp?.start() else ad?.start()
    }

    fun close() {
        if (!hq) abp?.stopRecording() else ad?.close()
    }
}
