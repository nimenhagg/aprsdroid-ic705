package org.aprsdroid.app.radio

import java.util.concurrent.atomic.AtomicBoolean
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.audio.Afsk1200PcmGenerator
import org.aprsdroid.app.audio.Ax25PacketEncoder
import org.aprsdroid.app.audio.DrainablePcmSink
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.audio.PcmSource
import org.aprsdroid.app.diagnostic.AppLog
import sivantoledo.ax25.Packet

/**
 * Generic Radio Audio backend orchestrating decoupled audio I/O with CAT PTT control.
 *
 * Architecture (ROADMAP Section 16.5):
 * - RX: PcmSource -> FeedableAfskDecoder -> Graywolf -> onPacketReceived
 * - TX: APRSPacket -> Ax25PacketEncoder -> Afsk1200PcmGenerator -> RadioPttSequence -> PcmSink -> drain
 * - Decoupled: Audio device and RadioControl (PTT) are separate and independent.
 */
class RadioAudioBackend(
    val radioControl: RadioControl,
    val audioSource: PcmSource,
    val audioSink: PcmSink,
    val decoder: PcmSink,
    val txSampleRateHz: Int = DEFAULT_TX_SAMPLE_RATE_HZ,
    val txDelayMs: Int = Afsk1200PcmGenerator.DEFAULT_TX_DELAY_MS,
    val txTail: Int = Afsk1200PcmGenerator.DEFAULT_TX_TAIL,
) : AutoCloseable {

    companion object {
        const val TAG = "RADIO.AUDIO_BACKEND"
        const val DEFAULT_RX_SAMPLE_RATE_HZ = 16000
        const val DEFAULT_TX_SAMPLE_RATE_HZ = 16000
        const val RX_BUFFER_SIZE = 4096

        /**
         * Factory creating a production [RadioAudioBackend] using [FeedableAfskDecoder].
         */
        fun create(
            radioControl: RadioControl,
            audioSource: PcmSource,
            audioSink: PcmSink,
            rxSampleRateHz: Int = DEFAULT_RX_SAMPLE_RATE_HZ,
            txSampleRateHz: Int = DEFAULT_TX_SAMPLE_RATE_HZ,
            txDelayMs: Int = Afsk1200PcmGenerator.DEFAULT_TX_DELAY_MS,
            txTail: Int = Afsk1200PcmGenerator.DEFAULT_TX_TAIL,
            onPacketReceived: (ByteArray) -> Unit,
        ): RadioAudioBackend {
            val decoder = FeedableAfskDecoder(
                format = PcmFormat(sampleRateHz = rxSampleRateHz),
                onPacket = onPacketReceived,
            )
            return RadioAudioBackend(
                radioControl = radioControl,
                audioSource = audioSource,
                audioSink = audioSink,
                decoder = decoder,
                txSampleRateHz = txSampleRateHz,
                txDelayMs = txDelayMs,
                txTail = txTail,
            )
        }
    }

    private val closed = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    private var rxThread: Thread? = null

    val isClosed: Boolean
        get() = closed.get()

    private val pttSequence = RadioPttSequence(radioControl)
    private val pcmGenerator = Afsk1200PcmGenerator(
        sampleRateHz = txSampleRateHz,
        txDelayMs = txDelayMs,
        txTail = txTail,
    )

    fun start() {
        check(!closed.get()) { "Backend is closed" }
        if (!isRunning.compareAndSet(false, true)) return

        AppLog.i(TAG, "starting_radio_audio_backend", mapOf("txRate" to txSampleRateHz))
        radioControl.open()

        rxThread = Thread({
            runRxLoop()
        }, "RadioAudio-RX").apply {
            isDaemon = true
            start()
        }
    }

    private fun runRxLoop() {
        val buffer = ShortArray(RX_BUFFER_SIZE)
        try {
            while (isRunning.get() && !closed.get()) {
                val samplesRead = audioSource.read(buffer, 0, buffer.size)
                if (samplesRead < 0) {
                    AppLog.i(TAG, "rx_source_eof")
                    break
                }
                if (samplesRead > 0) {
                    decoder.write(buffer, 0, samplesRead)
                }
            }
        } catch (e: Exception) {
            if (!closed.get()) {
                AppLog.w(TAG, "rx_loop_error", error = e)
            }
        }
    }

    /**
     * Transmits an APRS packet through the generic Radio Audio backend.
     * Guarantees strict PTT safety sequence:
     * 1. Request PTT ON and verify confirmation.
     * 2. Modulate AX.25 frame into PCM16 and stream to audio sink.
     * 3. Drain audio sink to ensure complete RF transmission.
     * 4. Request PTT OFF.
     */
    @Synchronized
    fun transmit(packet: APRSPacket): RadioPttSequence.Result {
        check(!closed.get()) { "Backend is closed" }
        val ax25Packet = Ax25PacketEncoder.encode(packet)
        return transmit(ax25Packet)
    }

    /**
     * Transmits a prepared AX.25 [Packet] frame.
     */
    @Synchronized
    fun transmit(packet: Packet): RadioPttSequence.Result {
        check(!closed.get()) { "Backend is closed" }
        val samples = pcmGenerator.generateSamples(packet)
        return transmitPcm(samples)
    }

    /**
     * Transmits raw PCM16 samples via the coordinated PTT sequence.
     */
    @Synchronized
    fun transmitPcm(samples: ShortArray): RadioPttSequence.Result {
        check(!closed.get()) { "Backend is closed" }
        return pttSequence.transmit {
            audioSink.write(samples, 0, samples.size)
            audioSink.flush()
            if (audioSink is DrainablePcmSink) {
                audioSink.drain()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        isRunning.set(false)
        AppLog.i(TAG, "closing_radio_audio_backend")

        rxThread?.interrupt()
        runCatching { audioSource.close() }
        runCatching { audioSink.close() }
        runCatching { decoder.close() }
        runCatching { radioControl.close() }
    }
}
