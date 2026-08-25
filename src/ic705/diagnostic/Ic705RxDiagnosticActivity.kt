package org.aprsdroid.app.ic705.diagnostic

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.android.Ic705AndroidSocketFactoryProvider
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705RadioSession
import org.aprsdroid.app.ic705.session.Ic705RxSession
import org.aprsdroid.app.ic705.session.Ic705RxSessionCallbacks
import org.aprsdroid.app.ic705.session.Ic705RxSessionConfig
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory
import org.aprsdroid.app.ui.screen.Ic705RxDiagnosticScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class Ic705RxDiagnosticActivity : ComponentActivity() {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val addressState = mutableStateOf("192.168.59.1")
    private val portState = mutableStateOf("50001")
    private val usernameState = mutableStateOf("")
    private val passwordState = mutableStateOf("")
    private val isRunningState = mutableStateOf(false)
    private val statusTextState = mutableStateOf("")
    private val acceptedAudioBlocksState = mutableLongStateOf(0L)
    private val acceptedAudioSamplesState = mutableLongStateOf(0L)
    private val decodedAx25FramesState = mutableLongStateOf(0L)
    private val audioResetsState = mutableLongStateOf(0L)
    private val eventLogTextState = mutableStateOf("")

    private val acceptedAudioBlocks = AtomicLong()
    private val acceptedAudioSamples = AtomicLong()
    private val decodedAx25Frames = AtomicLong()
    private val audioResets = AtomicLong()

    private val eventLogLock = Any()
    private val diagnosticEvents = ArrayDeque<String>()

    private var activeSession: Ic705RadioSession? = null
    private var activeDecoder: PcmSink? = null
    private var activeAttempt: Long = 0
    private var activityDestroyed: Boolean = false
    private var activityStartedAtMillis: Long = 0
    private var lastLoggedCounters: CounterSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityStartedAtMillis = SystemClock.elapsedRealtime()
        statusTextState.value = getString(R.string.ic705_rx_stopped)
        restoreConnectionFields()

        setContent {
            AprsTheme {
                Ic705RxDiagnosticScreen(
                    address = addressState.value,
                    port = portState.value,
                    username = usernameState.value,
                    password = passwordState.value,
                    isRunning = isRunningState.value,
                    statusText = statusTextState.value,
                    acceptedAudioBlocks = acceptedAudioBlocksState.longValue,
                    acceptedAudioSamples = acceptedAudioSamplesState.longValue,
                    decodedAx25Frames = decodedAx25FramesState.longValue,
                    audioResets = audioResetsState.longValue,
                    eventLogText = eventLogTextState.value,
                    onAddressChange = { addressState.value = it },
                    onPortChange = { portState.value = it },
                    onUsernameChange = { usernameState.value = it },
                    onPasswordChange = { passwordState.value = it },
                    onStartDiagnostics = { startReceiveOnlySession() },
                    onStopDiagnostics = { stopReceiveOnlySession(R.string.ic705_rx_stopped) },
                    onBack = { finish() }
                )
            }
        }

        recordEvent(attempt = 0, event = "ACTIVITY_CREATED")
    }

    override fun onDestroy() {
        activityDestroyed = true
        super.onDestroy()
        stopReceiveOnlySession(null)
    }

    private fun startReceiveOnlySession() {
        val targetAddress = addressState.value.trim()
        val targetPort = portState.value.trim().toIntOrNull() ?: 50001
        val targetUsername = usernameState.value
        val targetPassword = passwordState.value

        val parsedAddress = try {
            parseNumericIpv4(targetAddress)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.ic705_rx_invalid_ipv4, targetAddress), Toast.LENGTH_SHORT).show()
            return
        }

        stopReceiveOnlySession(null)

        acceptedAudioBlocks.set(0)
        acceptedAudioSamples.set(0)
        decodedAx25Frames.set(0)
        audioResets.set(0)
        lastLoggedCounters = null
        renderStatistics()

        val attempt = ++activeAttempt

        val format = PcmFormat(Ic705AudioPacketCodec.SAMPLE_RATE_HZ, 1)
        val decoder = FeedableAfskDecoder(format) {
            decodedAx25Frames.incrementAndGet()
            runOnUiThreadFor(attempt) { renderStatistics() }
        }
        activeDecoder = decoder

        val countingSink = object : PcmSink {
            override val format: PcmFormat get() = format
            override fun write(buffer: ShortArray, offset: Int, length: Int) {
                acceptedAudioBlocks.incrementAndGet()
                acceptedAudioSamples.addAndGet(length.toLong())
                decoder.write(buffer, offset, length)
            }
            override fun close() {
                decoder.close()
            }
        }

        val socketFactory: Ic705DatagramSocketFactory = try {
            Ic705AndroidSocketFactoryProvider.forCurrentWifi(this) ?: object : Ic705DatagramSocketFactory {
                override fun create(localAddress: InetSocketAddress): DatagramSocket {
                    return DatagramSocket(localAddress).apply { broadcast = true }
                }
            }
        } catch (_: Exception) {
            object : Ic705DatagramSocketFactory {
                override fun create(localAddress: InetSocketAddress): DatagramSocket {
                    return DatagramSocket(localAddress).apply { broadcast = true }
                }
            }
        }

        val config = try {
            Ic705RxSessionConfig(
                radioAddress = parsedAddress,
                controlPort = targetPort,
                username = targetUsername.ifBlank { "ic705" },
                password = targetPassword,
                clientName = "APRSdroid",
                autoReconnect = false
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.ic705_rx_config_error, e.message), Toast.LENGTH_LONG).show()
            return
        }

        val callbacks = Ic705RxSessionCallbacks(
            onStateChanged = { state ->
                runOnUiThreadFor(attempt) {
                    when (state.phase) {
                        Ic705RxSessionEngine.Phase.RECEIVING -> {
                            statusTextState.value = getString(R.string.ic705_rx_running)
                        }
                        Ic705RxSessionEngine.Phase.STOPPED -> {
                            statusTextState.value = getString(R.string.ic705_rx_stopped)
                            stopReceiveOnlySession(null)
                        }
                        Ic705RxSessionEngine.Phase.FAILED -> {
                            statusTextState.value = state.failureReason ?: getString(R.string.ic705_rx_connection_failed)
                            stopReceiveOnlySession(null)
                        }
                        else -> {
                            statusTextState.value = getString(R.string.ic705_rx_starting)
                        }
                    }
                    recordEvent(attempt = attempt, event = "PHASE_${state.phase.name}")
                }
            },
            onIssue = { issue ->
                recordEvent(attempt = attempt, event = "ISSUE_${issue.code.name}", channel = issue.channel?.name)
            },
            onAudioReset = { reset ->
                audioResets.incrementAndGet()
                recordEvent(attempt = attempt, event = "AUDIO_RESET_${reset.reason.name}")
            }
        )

        val session = Ic705RxSession(
            config = config,
            audioSink = countingSink,
            callbacks = callbacks,
            socketFactory = socketFactory
        )

        activeSession = session
        isRunningState.value = true
        statusTextState.value = getString(R.string.ic705_rx_starting)
        recordEvent(attempt = attempt, event = "SESSION_START")
        scheduleStatistics(attempt)
        session.start()
    }

    private fun stopReceiveOnlySession(statusMessage: Int?) {
        val closingAttempt = activeAttempt
        val stoppedAttempt = ++activeAttempt
        val previous = activeSession
        val previousDecoder = activeDecoder
        activeSession = null
        activeDecoder = null
        isRunningState.value = false
        if (statusMessage != null) {
            statusTextState.value = getString(statusMessage)
        }
        if (previous != null) {
            recordEvent(attempt = closingAttempt, event = "SESSION_STOP")
            previous.close {
                runCatching { previousDecoder?.close() }
                runOnUiThread {
                    if (!activityDestroyed && activeAttempt == stoppedAttempt && activeSession == null) {
                        recordEvent(attempt = closingAttempt, event = "SESSION_CLOSED")
                    }
                }
            }
        } else {
            runCatching { previousDecoder?.close() }
        }
    }

    private fun parseNumericIpv4(value: String): InetAddress {
        val octets = value.split('.')
        require(octets.size == 4)
        val bytes = ByteArray(4) { index ->
            val octet = octets[index]
            require(octet.isNotEmpty() && octet.all(Char::isDigit))
            octet.toInt().also { require(it in 0..255) }.toByte()
        }
        return InetAddress.getByAddress(bytes)
    }

    private fun scheduleStatistics(attempt: Long) {
        mainHandler.postDelayed(
            {
                if (activeAttempt == attempt && activeSession != null) {
                    renderStatistics()
                    recordCountersIfChanged(attempt)
                    scheduleStatistics(attempt)
                }
            },
            STATISTICS_PERIOD_MILLIS
        )
    }

    private fun recordCountersIfChanged(attempt: Long) {
        val snapshot = CounterSnapshot(
            audioBlocks = acceptedAudioBlocks.get(),
            pcmSamples = acceptedAudioSamples.get(),
            ax25Frames = decodedAx25Frames.get(),
            audioResets = audioResets.get(),
        )
        if (snapshot == lastLoggedCounters) return
        lastLoggedCounters = snapshot
        recordEvent(
            attempt = attempt,
            event = "COUNTERS",
            counters = snapshot,
        )
    }

    private fun recordEvent(
        attempt: Long,
        event: String,
        channel: String? = null,
        counters: CounterSnapshot? = null,
    ) {
        if (activityDestroyed) return
        val elapsedMillis = (SystemClock.elapsedRealtime() - activityStartedAtMillis).coerceAtLeast(0L)
        val entry = buildString {
            append("[+")
            append(elapsedMillis)
            append("ms] att=").append(attempt)
            append(" ev=").append(event)
            channel?.let { append(" ch=").append(it) }
            counters?.let {
                append(" blk=").append(it.audioBlocks)
                append(" pcm=").append(it.pcmSamples)
                append(" ax25=").append(it.ax25Frames)
                append(" rst=").append(it.audioResets)
            }
        }
        synchronized(eventLogLock) {
            while (diagnosticEvents.size >= MAX_DIAGNOSTIC_EVENTS) diagnosticEvents.removeFirst()
            diagnosticEvents.addLast(entry)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            renderEventLog()
        } else {
            runOnUiThread { renderEventLog() }
        }
    }

    private fun renderEventLog() {
        if (activityDestroyed) return
        val snapshot = synchronized(eventLogLock) { diagnosticEvents.joinToString(separator = "\n") }
        eventLogTextState.value = snapshot
    }

    private fun restoreConnectionFields() {
        addressState.value = prefs.getString("ic705.address", "192.168.59.1")
        portState.value = prefs.getString("ic705.control_port", "50001")
        usernameState.value = prefs.getString("ic705.username", "")
        passwordState.value = prefs.getString("ic705.password", "")
    }

    private fun renderStatistics() {
        acceptedAudioBlocksState.longValue = acceptedAudioBlocks.get()
        acceptedAudioSamplesState.longValue = acceptedAudioSamples.get()
        decodedAx25FramesState.longValue = decodedAx25Frames.get()
        audioResetsState.longValue = audioResets.get()
    }

    private fun runOnUiThreadFor(attempt: Long, block: () -> Unit) {
        runOnUiThread {
            if (!activityDestroyed && activeAttempt == attempt && activeSession != null) block()
        }
    }

    private data class CounterSnapshot(
        val audioBlocks: Long,
        val pcmSamples: Long,
        val ax25Frames: Long,
        val audioResets: Long,
    )

    private companion object {
        const val STATISTICS_PERIOD_MILLIS = 1_000L
        const val MAX_DIAGNOSTIC_EVENTS = 100
    }
}
