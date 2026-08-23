package org.aprsdroid.app.audio

import sivantoledo.ax25.Afsk1200Modulator
import sivantoledo.ax25.Packet

/**
 * Generates mono PCM16LE audio samples from AX.25 [Packet] frames
 * using [Afsk1200Modulator]. Default sample rate is 12 kHz, matching
 * the IC-705 LAN audio session rate ([DEFAULT_SAMPLE_RATE_HZ]).
 */
class Afsk1200PcmGenerator(
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val txDelayMs: Int = DEFAULT_TX_DELAY_MS,
    val txTail: Int = DEFAULT_TX_TAIL,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(txDelayMs >= 0) { "txDelayMs must be non-negative" }
        require(txTail >= 0) { "txTail must be non-negative" }
    }

    /**
     * Modulates the given [packet] into 16-bit signed PCM samples.
     * Note: Afsk1200Modulator.setTxDelay expects delay in 10ms (centisecond) units.
     */
    fun generateSamples(packet: Packet): ShortArray {
        val modulator = Afsk1200Modulator(sampleRateHz)
        if (txDelayMs > 0) {
            val delayUnits = (txDelayMs / 10).coerceAtLeast(1)
            modulator.setTxDelay(delayUnits)
        }
        if (txTail > 0) {
            modulator.setTxTail(txTail)
        }
        modulator.prepareToTransmit(packet)

        val samples = ArrayList<Float>()
        while (true) {
            val buffer = modulator.txSamplesBuffer
            val count = modulator.samples
            if (count <= 0) break
            for (i in 0 until count) {
                samples.add(buffer[i])
            }
        }

        return ShortArray(samples.size) { i ->
            val clamped = samples[i].coerceIn(-1.0f, 1.0f)
            (clamped * 32767.0f).toInt().toShort()
        }
    }

    /**
     * Modulates the given [packet] directly into Little-Endian PCM16 byte array.
     */
    fun generatePcm16LeBytes(packet: Packet): ByteArray {
        val shorts = generateSamples(packet)
        return Pcm16LittleEndian.encode(shorts)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 12_000
        const val DEFAULT_TX_DELAY_MS = 250
        const val DEFAULT_TX_TAIL = 4

        fun generateSilenceSamples(sampleCount: Int): ShortArray = ShortArray(sampleCount)

        fun generateSilenceBytes(sampleCount: Int): ByteArray =
            ByteArray(sampleCount * PcmEncoding.PCM_16_LE.bytesPerSample)
    }
}
