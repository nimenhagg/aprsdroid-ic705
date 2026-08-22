package org.aprsdroid.app.ic705.backend

import android.content.Context
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.Ax25PacketConsumer
import org.aprsdroid.app.Ax25SubmitSink
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.ic705.android.Ic705AndroidSocketFactoryProvider
import org.aprsdroid.app.ic705.session.Ic705RxSession
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705RadioSession
import org.aprsdroid.app.ic705.session.Ic705RxSessionCallbacks
import org.aprsdroid.app.ic705.session.Ic705RxSessionConfig
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory

/** Minimal Android-facing surface the controller needs from [AprsService]. */
interface Ic705BackendService {
    fun postPosterStarted()
    fun postAbort(message: String)
    fun postSubmit(text: String)
    fun getString(resId: Int): String
}

/** Parsed IC-705 connection settings, decoupled from SharedPreferences for JVM tests. */
interface Ic705BackendPrefs {
    val address: String
    val controlPort: Int
    val username: String
    val password: String
}

/** Creates the radio session for a resolved socket factory. */
fun interface Ic705RadioSessionFactory {
    fun create(
        config: Ic705RxSessionConfig,
        audioSink: PcmSink,
        callbacks: Ic705RxSessionCallbacks,
        socketFactory: Ic705DatagramSocketFactory,
    ): Ic705RadioSession
}

/** Creates the demodulator sink that turns PCM into raw AX.25 frames. */
fun interface Ic705DecoderFactory {
    fun create(format: PcmFormat, onPacket: (ByteArray) -> Unit): PcmSink
}

/**
 * Testable lifecycle logic for the IC-705 Wi-Fi receive-only backend.
 *
 * The radio session deliberately keeps its own reconnect disabled in production:
 * an Android Network handle is generation-specific and cannot safely be reused
 * after Wi-Fi changes. Recoverable session failures are therefore promoted to
 * this controller, which fully closes the old generation, waits for close to
 * complete, backs off, then calls [socketFactoryProvider] again before creating
 * a new decoder/session pair.
 *
 * This is intentionally still RX-only. No TX audio or PTT path exists here.
 */
class Ic705WifiBackendController(
    private val service: Ic705BackendService,
    private val prefs: Ic705BackendPrefs,
    private val sdkAtLeast: (Int) -> Boolean,
    private val socketFactoryProvider: () -> Ic705DatagramSocketFactory?,
    private val sessionFactory: Ic705RadioSessionFactory,
    private val decoderFactory: Ic705DecoderFactory = Ic705DecoderFactory { format, onPacket ->
        FeedableAfskDecoder(format, onPacket)
    },
    private val reconnectScheduler: Ic705ReconnectScheduler = Ic705ExecutorReconnectScheduler(),
    private val reconnectBackoff: Ic705ReconnectBackoff = Ic705ReconnectBackoff(),
) {
    private val stopped = AtomicBoolean(false)
    private val startRequested = AtomicBoolean(false)
    private val startedReported = AtomicBoolean(false)
    private val nextGeneration = AtomicLong(0L)
    private val lock = Any()
    private val packetConsumer = Ax25PacketConsumer(
        Ax25SubmitSink { service.postSubmit(it) },
        TAG,
    )

    @Volatile
    private var config: Ic705RxSessionConfig? = null

    @Volatile
    private var activeGeneration: Long = NO_GENERATION

    @Volatile
    private var session: Ic705RadioSession? = null

    @Volatile
    private var decoder: PcmSink? = null

    private var activeSawReconnectWait = false
    private var retryAttempt = 0
    private var pendingRetry: Ic705RetryHandle? = null

    /** Startup completes asynchronously at RECEIVING; returns false like other async backends. */
    fun start(): Boolean {
        if (!startRequested.compareAndSet(false, true)) return false
        if (!sdkAtLeast(22)) return fail(R.string.ic705_backend_requires_api_22)

        val parsedConfig = runCatching {
            require(prefs.address.isNotEmpty())
            Ic705RxSessionConfig(
                radioAddress = InetAddress.getByName(prefs.address),
                controlPort = prefs.controlPort,
                username = prefs.username,
                password = prefs.password,
                clientName = "APRSdroid",
                // Reconnect above this layer so each generation gets a fresh Android Network.
                autoReconnect = false,
            )
        }.getOrElse {
            return fail(R.string.ic705_backend_invalid_settings)
        }
        config = parsedConfig

        // Preserve the stage-1 contract: an initial start without Wi-Fi fails cleanly.
        // Once a real session exists, recoverable failures use backend-level retries.
        connectNewGeneration(parsedConfig, initialAttempt = true)
        return false
    }

    fun update(packet: APRSPacket): String {
        Log.i(TAG, "update() requested for packet: $packet")
        if (stopped.get()) {
            Log.w(TAG, "update() rejected: controller stopped")
            return service.getString(R.string.ic705_backend_not_connected)
        }
        val activeSession = synchronized(lock) { session }
        if (activeSession == null) {
            Log.w(TAG, "update() rejected: session is null")
            return service.getString(R.string.ic705_backend_not_connected)
        }
        if (activeSession.state.phase != Ic705RxSessionEngine.Phase.RECEIVING) {
            Log.w(TAG, "update() rejected: session phase is ${activeSession.state.phase}")
            return service.getString(R.string.ic705_backend_not_connected)
        }
        if (activeSession.isTransmitting) {
            Log.w(TAG, "update() rejected: session is already transmitting")
            return service.getString(R.string.ic705_backend_tx_busy)
        }
        val ok = activeSession.transmit(packet)
        Log.i(TAG, "update() activeSession.transmit() returned ok=$ok")
        return if (ok) {
            service.getString(R.string.ic705_backend_tx_ok)
        } else {
            service.getString(R.string.ic705_backend_tx_busy)
        }
    }

    /** Idempotent stop; cancels backoff and never blocks the Service caller on session close. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return

        val closing: ClosingGeneration
        synchronized(lock) {
            pendingRetry?.cancel()
            pendingRetry = null
            closing = detachActiveLocked()
        }
        reconnectScheduler.close()
        closeGeneration(closing)
    }

    private fun connectNewGeneration(
        sessionConfig: Ic705RxSessionConfig,
        initialAttempt: Boolean,
    ) {
        if (stopped.get()) return

        // This call is intentionally repeated for every recovery attempt so an
        // obsolete Android Network object is never carried into a new session.
        val socketFactory = runCatching(socketFactoryProvider).getOrNull()
        if (socketFactory == null) {
            if (initialAttempt) fail(R.string.ic705_backend_no_wifi) else scheduleReconnect()
            return
        }

        val generation = nextGeneration.incrementAndGet()
        var newDecoder: PcmSink? = null
        try {
            val decoderForCallbacks = decoderFactory.create(
                PcmFormat(sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ),
            ) { data ->
                if (isActive(generation)) packetConsumer.accept(data)
            }
            newDecoder = decoderForCallbacks
            val newSession = sessionFactory.create(
                config = sessionConfig,
                audioSink = decoderForCallbacks,
                callbacks = Ic705RxSessionCallbacks(
                    onStateChanged = { state -> onSessionState(generation, state) },
                    onAudioReset = {
                        if (isActive(generation)) runCatching(decoderForCallbacks::reset)
                    },
                ),
                socketFactory = socketFactory,
            )

            synchronized(lock) {
                if (stopped.get()) {
                    newSession.close { runCatching { newDecoder.close() } }
                    return
                }
                check(session == null && decoder == null) {
                    "new IC-705 generation created before previous generation closed"
                }
                activeGeneration = generation
                activeSawReconnectWait = false
                session = newSession
                decoder = newDecoder
            }
            newSession.start()
        } catch (_: Exception) {
            runCatching { newDecoder?.close() }
            if (stopped.get()) return
            if (initialAttempt) {
                fail(R.string.ic705_backend_invalid_settings)
            } else {
                scheduleReconnect()
            }
        }
    }

    private fun onSessionState(generation: Long, state: Ic705RxSessionEngine.State) {
        if (!isActive(generation)) return

        when (state.phase) {
            Ic705RxSessionEngine.Phase.RECEIVING -> {
                synchronized(lock) {
                    if (activeGeneration == generation) retryAttempt = 0
                }
                if (!stopped.get() && startedReported.compareAndSet(false, true)) {
                    service.postPosterStarted()
                }
            }

            Ic705RxSessionEngine.Phase.RECONNECT_WAIT -> synchronized(lock) {
                if (activeGeneration == generation) activeSawReconnectWait = true
            }

            Ic705RxSessionEngine.Phase.FAILED -> {
                val recoverable = synchronized(lock) {
                    activeGeneration == generation && activeSawReconnectWait
                }
                if (recoverable) recoverGeneration(generation) else failGeneration(generation)
            }

            else -> Unit
        }
    }

    /** Fully close the old fixed-port generation before a reconnect timer is armed. */
    private fun recoverGeneration(generation: Long) {
        val closing = synchronized(lock) {
            if (activeGeneration != generation || stopped.get()) return
            detachActiveLocked()
        }
        closeGeneration(closing) {
            if (!stopped.get()) scheduleReconnect()
        }
    }

    /** Unrecoverable session failures still follow the Service abort/error path. */
    private fun failGeneration(generation: Long) {
        val closing = synchronized(lock) {
            if (activeGeneration != generation || stopped.get()) return
            detachActiveLocked()
        }
        closeGeneration(closing) {
            if (!stopped.get()) service.postAbort(service.getString(R.string.ic705_backend_failed))
        }
    }

    private fun scheduleReconnect() {
        if (stopped.get()) return
        val scheduled: Ic705RetryHandle
        synchronized(lock) {
            if (stopped.get() || pendingRetry != null) return
            val delay = reconnectBackoff.delayMillis(retryAttempt++)
            scheduled = reconnectScheduler.schedule(delay) {
                val shouldRun = synchronized(lock) {
                    pendingRetry = null
                    !stopped.get()
                }
                if (shouldRun) config?.let { connectNewGeneration(it, initialAttempt = false) }
            }
            pendingRetry = scheduled
        }
    }

    private fun isActive(generation: Long): Boolean =
        !stopped.get() && activeGeneration == generation

    /** Must be called under [lock]. Invalidates callbacks before close begins. */
    private fun detachActiveLocked(): ClosingGeneration {
        val closing = ClosingGeneration(session, decoder)
        activeGeneration = NO_GENERATION
        activeSawReconnectWait = false
        session = null
        decoder = null
        return closing
    }

    private fun closeGeneration(closing: ClosingGeneration, onClosed: () -> Unit = {}) {
        val closingDecoder = closing.decoder
        val closingSession = closing.session
        if (closingSession == null) {
            runCatching { closingDecoder?.close() }
            onClosed()
        } else {
            closingSession.close {
                runCatching { closingDecoder?.close() }
                onClosed()
            }
        }
    }

    private fun fail(message: Int): Boolean {
        service.postAbort(service.getString(message))
        return false
    }

    private data class ClosingGeneration(
        val session: Ic705RadioSession?,
        val decoder: PcmSink?,
    )

    companion object {
        private const val TAG = "APRSdroid.IC705"
        private const val NO_GENERATION = -1L

        @JvmStatic
        fun createDefault(
            service: Ic705BackendService,
            prefs: Ic705BackendPrefs,
            context: Context,
        ): Ic705WifiBackendController = Ic705WifiBackendController(
            service = service,
            prefs = prefs,
            sdkAtLeast = { Build.VERSION.SDK_INT >= it },
            socketFactoryProvider = { Ic705AndroidSocketFactoryProvider.forCurrentWifi(context) },
            sessionFactory = { config, audioSink, callbacks, socketFactory ->
                Ic705RxSession(config, audioSink, callbacks, socketFactory)
            },
        )
    }
}
