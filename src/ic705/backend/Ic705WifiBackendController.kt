package org.aprsdroid.app.ic705.backend

import android.content.Context
import android.os.Build
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.Ax25PacketConsumer
import org.aprsdroid.app.Ax25SubmitSink
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.diagnostic.Ic705DiagnosticState
import org.aprsdroid.app.ic705.android.Ic705AndroidSocketFactoryProvider
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705RadioSession
import org.aprsdroid.app.ic705.session.Ic705RxSession
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
 * Testable lifecycle logic for the IC-705 Wi-Fi backend (full-duplex RX + TX).
 *
 * The radio session deliberately keeps its own reconnect disabled in production:
 * an Android Network handle is generation-specific and cannot safely be reused
 * after Wi-Fi changes. Recoverable session failures are therefore promoted to
 * this controller, which fully closes the old generation, waits for close to
 * complete, backs off, then calls [socketFactoryProvider] again before creating
 * a new decoder/session pair.
 *
 * TX path: [update] encodes an APRS packet via AFSK modulation, then delegates
 * to [Ic705RadioSession.transmit] which coordinates PTT ON → audio streaming →
 * PTT OFF through the [Ic705PttStateMachine].
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
        AppLog.i(
            "IC705",
            "backend_start",
            mapOf("radio_address" to prefs.address, "control_port" to prefs.controlPort),
        )
        Ic705DiagnosticState.set("controller", "STARTING")
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
        }.getOrElse { error ->
            AppLog.e("IC705", "invalid_settings", error = error)
            return fail(R.string.ic705_backend_invalid_settings)
        }
        config = parsedConfig

        // Preserve the stage-1 contract: an initial start without Wi-Fi fails cleanly.
        // Once a real session exists, recoverable failures use backend-level retries.
        connectNewGeneration(parsedConfig, initialAttempt = true)
        return false
    }

    fun update(packet: APRSPacket): String {
        AppLog.i("IC705", "tx_requested")
        if (stopped.get()) {
            AppLog.w("IC705", "tx_rejected", mapOf("reason" to "controller_stopped"))
            return service.getString(R.string.ic705_backend_not_connected)
        }
        val activeSession = synchronized(lock) { session }
        if (activeSession == null) {
            AppLog.w("IC705", "tx_rejected", mapOf("reason" to "session_null"))
            return service.getString(R.string.ic705_backend_not_connected)
        }
        if (activeSession.state.phase != Ic705RxSessionEngine.Phase.RECEIVING) {
            AppLog.w(
                "IC705",
                "tx_rejected",
                mapOf("reason" to "wrong_phase", "phase" to activeSession.state.phase),
            )
            return service.getString(R.string.ic705_backend_not_connected)
        }
        if (activeSession.isTransmitting) {
            AppLog.w("IC705", "tx_rejected", mapOf("reason" to "already_transmitting"))
            return service.getString(R.string.ic705_backend_tx_busy)
        }
        val ok = activeSession.transmit(packet)
        AppLog.i("IC705", "tx_submit_result", mapOf("accepted" to ok))
        Ic705DiagnosticState.set("ptt_asserted_possible", activeSession.isTransmitting)
        return if (ok) {
            service.getString(R.string.ic705_backend_tx_ok)
        } else {
            service.getString(R.string.ic705_backend_tx_busy)
        }
    }

    /** Idempotent stop; cancels backoff and never blocks the Service caller on session close. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        AppLog.i("IC705", "backend_stop")
        Ic705DiagnosticState.set("controller", "STOPPING")

        val closing: ClosingGeneration
        synchronized(lock) {
            pendingRetry?.cancel()
            pendingRetry = null
            closing = detachActiveLocked()
        }
        reconnectScheduler.close()
        closeGeneration(closing) {
            Ic705DiagnosticState.set("controller", "STOPPED")
            Ic705DiagnosticState.clearSession()
            AppLog.i("IC705", "backend_stopped")
        }
    }

    private fun connectNewGeneration(
        sessionConfig: Ic705RxSessionConfig,
        initialAttempt: Boolean,
    ) {
        if (stopped.get()) return

        // This call is intentionally repeated for every recovery attempt so an
        // obsolete Android Network object is never carried into a new session.
        val socketFactory = runCatching(socketFactoryProvider).onFailure { error ->
            AppLog.e("IC705", "socket_factory_failed", error = error)
        }.getOrNull()
        if (socketFactory == null) {
            AppLog.w("IC705", "wifi_socket_factory_unavailable", mapOf("initial" to initialAttempt))
            if (initialAttempt) fail(R.string.ic705_backend_no_wifi) else scheduleReconnect()
            return
        }

        val generation = nextGeneration.incrementAndGet()
        AppLog.i("IC705", "generation_create", mapOf("generation" to generation, "initial" to initialAttempt))
        Ic705DiagnosticState.set("generation", generation)
        Ic705DiagnosticState.set("controller", "CONNECTING")
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
                    onAudioReset = { reset ->
                        if (isActive(generation)) {
                            AppLog.w(
                                "IC705",
                                "audio_reset",
                                mapOf("generation" to generation, "reason" to reset.reason),
                            )
                            runCatching(decoderForCallbacks::reset)
                        }
                    },
                    onStreamRecovery = { recovery ->
                        if (isActive(generation)) {
                            AppLog.w(
                                "IC705",
                                "stream_recovery",
                                mapOf(
                                    "generation" to generation,
                                    "role" to recovery.role,
                                    "outcome" to recovery.outcome,
                                    "attempt" to recovery.attempt,
                                    "age_ms" to recovery.ageMillis,
                                ),
                            )
                            Ic705DiagnosticState.set("stream_recovery_role", recovery.role)
                            Ic705DiagnosticState.set("stream_recovery_outcome", recovery.outcome)
                            Ic705DiagnosticState.set("stream_recovery_attempt", recovery.attempt)
                        }
                    },
                    onIssue = { issue ->
                        if (isActive(generation)) {
                            AppLog.w(
                                "IC705",
                                "session_issue",
                                mapOf(
                                    "generation" to generation,
                                    "code" to issue.code,
                                    "channel" to issue.channel,
                                ),
                            )
                        }
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
        } catch (error: Exception) {
            AppLog.e("IC705", "generation_create_failed", mapOf("generation" to generation), error)
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

        val transmitting = synchronized(lock) { session?.isTransmitting == true }
        Ic705DiagnosticState.set("generation", generation)
        Ic705DiagnosticState.set("phase", state.phase)
        Ic705DiagnosticState.set("failure_reason", state.failureReason)
        Ic705DiagnosticState.set("ptt_asserted_possible", transmitting)
        AppLog.i(
            "IC705",
            "session_state",
            mapOf(
                "generation" to generation,
                "phase" to state.phase,
                "failure_reason" to state.failureReason,
                "retry_attempt" to state.retryAttempt,
                "transmitting" to transmitting,
            ),
        )

        when (state.phase) {
            Ic705RxSessionEngine.Phase.RECEIVING -> {
                synchronized(lock) {
                    if (activeGeneration == generation) retryAttempt = 0
                }
                Ic705DiagnosticState.set("controller", "CONNECTED")
                Ic705DiagnosticState.set("reconnect_attempt", 0)
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
        AppLog.w("IC705", "generation_recover", mapOf("generation" to generation))
        Ic705DiagnosticState.set("controller", "RECOVERING")
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
        AppLog.e("IC705", "generation_failed", mapOf("generation" to generation))
        Ic705DiagnosticState.set("controller", "FAILED")
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
            val attempt = retryAttempt++
            val delay = reconnectBackoff.delayMillis(attempt)
            AppLog.i(
                "IC705",
                "reconnect_scheduled",
                mapOf("attempt" to attempt, "delay_ms" to delay),
            )
            Ic705DiagnosticState.set("reconnect_attempt", attempt)
            scheduled = reconnectScheduler.schedule(delay) {
                val shouldRun = synchronized(lock) {
                    pendingRetry = null
                    !stopped.get()
                }
                if (shouldRun) {
                    AppLog.i("IC705", "reconnect_execute", mapOf("attempt" to attempt))
                    config?.let { connectNewGeneration(it, initialAttempt = false) }
                }
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
        AppLog.e("IC705", "backend_abort", mapOf("message_res" to message))
        Ic705DiagnosticState.set("controller", "FAILED")
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
