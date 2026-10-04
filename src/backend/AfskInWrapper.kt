package org.aprsdroid.app

/** Owns the single Graywolf-backed microphone/routed-audio receive thread. */
class AfskInWrapper(
    au: AfskUploader,
    inType: Int,
    samplerate: Int,
) {
    private val demodulator = AfskDemodulator(au, inType, samplerate)

    @Synchronized
    fun start() {
        if (!demodulator.isAlive && demodulator.state == Thread.State.NEW) {
            demodulator.start()
        }
    }

    fun close() {
        demodulator.close()
    }
}
