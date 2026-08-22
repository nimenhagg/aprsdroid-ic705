package org.aprsdroid.app.ic705.diagnostic

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.net.InetAddress
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.android.Ic705AndroidSocketFactoryProvider
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705PacketDiagnostic
import org.aprsdroid.app.ic705.session.Ic705RxSession
import org.aprsdroid.app.ic705.session.Ic705RxSessionCallbacks
import org.aprsdroid.app.ic705.session.Ic705RxSessionConfig
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine
import org.aprsdroid.app.ic705.session.Ic705RxSessionIssueCode

/** Temporary, receive-only hardware harness. */
class Ic705RxDiagnosticActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var port: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var status: TextView
    private lateinit var statistics: TextView
    private lateinit var eventLog: TextView

    private var activeSession: Ic705RxSession? = null
    private var activeDecoder: FeedableAfskDecoder? = null
    private val eventLogLock = Any()
    private val diagnosticEvents = ArrayDeque<String>(MAX_DIAGNOSTIC_EVENTS)
    private val activityStartedAtMillis = SystemClock.elapsedRealtime()
    private var lastLoggedCounters: CounterSnapshot? = null
    private val issueOccurrences = mutableMapOf<String, Long>()

    @Volatile
    private var activeAttempt = 0L
    @Volatile
    private var activityDestroyed = false
    private val acceptedAudioBlocks = AtomicLong()
    private val acceptedAudioSamples = AtomicLong()
    private val decodedAx25Frames = AtomicLong()
    private val audioResets = AtomicLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.ic705_rx_diagnostic)

        address = findViewById(R.id.ic705_rx_address)
        port = findViewById(R.id.ic705_rx_port)
        username = findViewById(R.id.ic705_rx_username)
        password = findViewById(R.id.ic705_rx_password)
        connect = findViewById(R.id.ic705_rx_connect)
        disconnect = findViewById(R.id.ic705_rx_disconnect)
        status = findViewById(R.id.ic705_rx_status)
        statistics = findViewById(R.id.ic705_rx_statistics)
        eventLog = findViewById(R.id.ic705_rx_event_log)

        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        restoreConnectionFields()
        connect.setOnClickListener { startReceiveOnlySession() }
        disconnect.setOnClickListener { stopReceiveOnlySession(R.string.ic705_rx_stopped) }
        renderStatistics()
        recordEvent(attempt = activeAttempt, event = "ACTIVITY_CREATED")
    }

    override fun onStop() {
        stopReceiveOnlySession(R.string.ic705_rx_stopped)
        super.onStop()
    }

    override fun onDestroy() {
        activityDestroyed = true
        synchronized(eventLogLock) {
            diagnosticEvents.clear()
        }
        super.onDestroy()
    }

    private fun startReceiveOnlySession() {
        if (activeSession != null) return
        if (Build.VERSION.SDK_INT < 22) {
            status.setText(R.string.ic705_rx_requires_api_22)
            recordEvent(attempt = activeAttempt, event = "API_UNSUPPORTED")
            return
        }

        val socketFactory = Ic705AndroidSocketFactoryProvider.forCurrentWifi(this)
        if (socketFactory == null) {
            status.setText(R.string.ic705_rx_no_wifi)
            recordEvent(attempt = activeAttempt, event = "WIFI_UNAVAILABLE")
            return
        }

        val radioAddress = runCatching {
            val value = address.text.toString().trim()
            require(value.isNotEmpty())
            parseNumericIpv4(value)
        }.getOrElse {
            status.setText(R.string.ic705_rx_bad_address)
            recordEvent(attempt = activeAttempt, event = "ADDRESS_REJECTED")
            return
        }
        val controlPort = port.text.toString().toIntOrNull()
        if (controlPort == null || controlPort !in 1..65_535) {
            status.setText(R.string.ic705_rx_bad_port)
            recordEvent(attempt = activeAttempt, event = "PORT_REJECTED")
            return
        }
        saveConnectionFields()

        val attempt = ++activeAttempt
        acceptedAudioBlocks.set(0)
        acceptedAudioSamples.set(0)
        decodedAx25Frames.set(0)
        audioResets.set(0)
        lastLoggedCounters = null
        issueOccurrences.clear()

        val decoder = FeedableAfskDecoder(
            format = PcmFormat(sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ),
            onPacket = {
                if (activeAttempt == attempt) decodedAx25Frames.incrementAndGet()
            },
        )
        val countingSink = object : PcmSink {
            override val format = decoder.format

            override fun write(buffer: ShortArray, offset: Int, length: Int) {
                if (activeAttempt != attempt) return
                decoder.write(buffer, offset, length)
                acceptedAudioBlocks.incrementAndGet()
                acceptedAudioSamples.addAndGet(length.toLong())
            }

            override fun close() = Unit
        }

        val config = runCatching {
            Ic705RxSessionConfig(
                radioAddress = radioAddress,
                controlPort = controlPort,
                username = username.text.toString(),
                password = password.text.toString(),
                clientName = "APRSdroid RX",
                // A Network handle cannot be reused after Wi-Fi disconnects. The diagnostic
                // deliberately requires a manual restart so it can select the new handle.
                autoReconnect = false,
            )
        }.getOrElse {
            status.setText(R.string.ic705_rx_bad_credentials)
            recordEvent(attempt = attempt, event = "CREDENTIALS_REJECTED")
            decoder.close()
            return
        }

        val session = Ic705RxSession(
            config = config,
            audioSink = countingSink,
            // All three UDP channels in this attempt use the same Network.
            socketFactory = socketFactory,
            callbacks = Ic705RxSessionCallbacks(
                onStateChanged = { state ->
                    runOnUiThreadFor(attempt) {
                        recordEvent(
                            attempt = attempt,
                            event = "STATE",
                            phase = state.phase.name,
                        )
                        if (state.phase == Ic705RxSessionEngine.Phase.FAILED) {
                            status.setText(R.string.ic705_rx_failed_restart)
                        } else {
                            status.text = getString(R.string.ic705_rx_phase, state.phase.name)
                        }
                    }
                },
                onIssue = { issue ->
                    runOnUiThreadFor(attempt) {
                        val channel = issue.channel?.name ?: "SESSION"
                        val signature = "${issue.code.name}|$channel|${issue.packet}"
                        val occurrence = (issueOccurrences[signature] ?: 0L) + 1L
                        issueOccurrences[signature] = occurrence
                        if (occurrence <= 3L || occurrence % ISSUE_LOG_SAMPLE_PERIOD == 0L) {
                            recordEvent(
                                attempt = attempt,
                                event = "ISSUE",
                                issue = issue.code.name,
                                channel = channel,
                                packet = issue.packet,
                                occurrence = occurrence,
                            )
                        }
                        status.text = if (issue.code == Ic705RxSessionIssueCode.SOCKET_IO) {
                            getString(R.string.ic705_rx_issue_restart, issue.code.name, channel)
                        } else {
                            getString(R.string.ic705_rx_issue, issue.code.name, channel)
                        }
                    }
                },
                onAudioReset = { reset ->
                    if (activeAttempt == attempt) {
                        val resetCount = audioResets.incrementAndGet()
                        runCatching(decoder::reset)
                        runOnUiThreadFor(attempt) {
                            recordEvent(
                                attempt = attempt,
                                event = "AUDIO_RESET",
                                countName = "count",
                                count = resetCount,
                                resetReason = reset.reason.name,
                                discontinuityKind = reset.discontinuity?.kind?.name,
                                expectedSequence = reset.discontinuity?.expectedSequence,
                                actualSequence = reset.discontinuity?.actualSequence,
                                missingPacketCount = reset.discontinuity?.missingPacketCount,
                            )
                        }
                    }
                },
            ),
        )
        activeDecoder = decoder
        activeSession = session
        connect.isEnabled = false
        disconnect.isEnabled = true
        status.setText(R.string.ic705_rx_starting)
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
        connect.isEnabled = previous == null
        disconnect.isEnabled = false
        if (statusMessage != null) status.setText(statusMessage)
        if (previous != null) {
            recordEvent(attempt = closingAttempt, event = "SESSION_STOP")
            previous.close {
                runCatching { previousDecoder?.close() }
                runOnUiThread {
                    if (!activityDestroyed && activeAttempt == stoppedAttempt && activeSession == null) {
                        connect.isEnabled = true
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
        statistics.postDelayed(
            {
                if (activeAttempt == attempt && activeSession != null) {
                    renderStatistics()
                    recordCountersIfChanged(attempt)
                    scheduleStatistics(attempt)
                }
            },
            STATISTICS_PERIOD_MILLIS,
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

    /**
     * Adds a bounded, memory-only diagnostic event. Every argument comes from local enums,
     * constants, or counters; credentials, IDs, tokens, endpoints, and datagram bytes are never
     * accepted by this API.
     */
    private fun recordEvent(
        attempt: Long,
        event: String,
        phase: String? = null,
        issue: String? = null,
        channel: String? = null,
        countName: String? = null,
        count: Long? = null,
        counters: CounterSnapshot? = null,
        packet: Ic705PacketDiagnostic? = null,
        occurrence: Long? = null,
        resetReason: String? = null,
        discontinuityKind: String? = null,
        expectedSequence: Int? = null,
        actualSequence: Int? = null,
        missingPacketCount: Int? = null,
    ) {
        if (activityDestroyed) return
        val elapsedMillis = (SystemClock.elapsedRealtime() - activityStartedAtMillis).coerceAtLeast(0L)
        val entry = buildString {
            append("[+")
            append(elapsedMillis)
            append("ms] attempt=")
            append(attempt)
            append(" event=")
            append(event)
            phase?.let { append(" phase=").append(it) }
            issue?.let { append(" issue=").append(it) }
            channel?.let { append(" channel=").append(it) }
            occurrence?.let { append(" occurrence=").append(it) }
            resetReason?.let { append(" reason=").append(it) }
            discontinuityKind?.let { append(" kind=").append(it) }
            expectedSequence?.let { append(" expected=").append(it) }
            actualSequence?.let { append(" actual=").append(it) }
            missingPacketCount?.let { append(" missing=").append(it) }
            if (countName != null && count != null) append(' ').append(countName).append('=').append(count)
            packet?.let {
                append(" length=").append(it.length)
                it.declaredLength?.let { value -> append(" declared=").append(value) }
                it.commonType?.let { value -> append(" commonType=").append(hex(value, 4)) }
                append(" receiver=").append(it.receiverKind.name)
                it.payloadLength?.let { value -> append(" payloadLength=").append(value) }
                it.requestReply?.let { value -> append(" requestReply=").append(hex(value, 2)) }
                it.requestType?.let { value -> append(" requestType=").append(hex(value, 2)) }
                append(" rejection=").append(it.rejection.name)
            }
            counters?.let {
                append(" audioBlocks=").append(it.audioBlocks)
                append(" pcmSamples=").append(it.pcmSamples)
                append(" ax25Frames=").append(it.ax25Frames)
                append(" audioResets=").append(it.audioResets)
            }
        }
        synchronized(eventLogLock) {
            while (diagnosticEvents.size >= MAX_DIAGNOSTIC_EVENTS) diagnosticEvents.removeFirst()
            diagnosticEvents.addLast(entry)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            renderEventLog()
        } else if (::eventLog.isInitialized) {
            eventLog.post(::renderEventLog)
        }
    }

    private fun renderEventLog() {
        if (activityDestroyed || !::eventLog.isInitialized) return
        val snapshot = synchronized(eventLogLock) { diagnosticEvents.joinToString(separator = "\n") }
        eventLog.text = if (snapshot.isEmpty()) getString(R.string.ic705_rx_log_empty) else snapshot
    }

    private fun hex(value: Int, width: Int): String =
        "0x" + value.toString(16).uppercase().padStart(width, '0')

    private fun restoreConnectionFields() {
        val preferences = getSharedPreferences(CONNECTION_PREFERENCES, MODE_PRIVATE)
        address.setText(preferences.getString(KEY_ADDRESS, address.text.toString()))
        port.setText(preferences.getString(KEY_PORT, port.text.toString()))
        username.setText(preferences.getString(KEY_USERNAME, ""))
        password.setText(preferences.getString(KEY_PASSWORD, ""))
    }

    private fun saveConnectionFields() {
        getSharedPreferences(CONNECTION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(KEY_ADDRESS, address.text.toString().trim())
            .putString(KEY_PORT, port.text.toString().trim())
            .putString(KEY_USERNAME, username.text.toString())
            .putString(KEY_PASSWORD, password.text.toString())
            .apply()
    }

    private fun renderStatistics() {
        statistics.text = getString(
            R.string.ic705_rx_statistics,
            acceptedAudioBlocks.get(),
            acceptedAudioSamples.get(),
            decodedAx25Frames.get(),
            audioResets.get(),
        )
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
        const val ISSUE_LOG_SAMPLE_PERIOD = 25L
        const val CONNECTION_PREFERENCES = "ic705_connection"
        const val KEY_ADDRESS = "address"
        const val KEY_PORT = "control_port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
