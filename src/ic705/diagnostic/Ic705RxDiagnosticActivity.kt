package org.aprsdroid.app.ic705.diagnostic

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class Ic705RxDiagnosticActivity : AppCompatActivity() {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.diagnostic_toolbar) }
    private val address: TextInputEditText by lazy { findViewById(R.id.diagnostic_address) }
    private val port: TextInputEditText by lazy { findViewById(R.id.diagnostic_port) }
    private val username: TextInputEditText by lazy { findViewById(R.id.diagnostic_username) }
    private val password: TextInputEditText by lazy { findViewById(R.id.diagnostic_password) }
    private val connect: MaterialButton by lazy { findViewById(R.id.diagnostic_connect) }
    private val disconnect: MaterialButton by lazy { findViewById(R.id.diagnostic_disconnect) }
    private val status: TextView by lazy { findViewById(R.id.diagnostic_status) }
    private val statAudioBlocks: TextView by lazy { findViewById(R.id.stat_audio_blocks) }
    private val statPcmSamples: TextView by lazy { findViewById(R.id.stat_pcm_samples) }
    private val statAx25Frames: TextView by lazy { findViewById(R.id.stat_ax25_frames) }
    private val statAudioResets: TextView by lazy { findViewById(R.id.stat_audio_resets) }
    private val eventLog: TextView by lazy { findViewById(R.id.diagnostic_event_log) }

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
        setContentView(R.layout.activity_ic705_rx_diagnostic)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        activityStartedAtMillis = SystemClock.elapsedRealtime()

        restoreConnectionFields()

        connect.setOnClickListener { startReceiveOnlySession() }
        disconnect.setOnClickListener { stopReceiveOnlySession(R.string.ic705_rx_stopped) }

        renderStatistics()
        recordEvent(attempt = 0, event = "ACTIVITY_CREATED")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        activityDestroyed = true
        super.onDestroy()
        stopReceiveOnlySession(null)
    }

    private fun startReceiveOnlySession() {
        val targetAddress = address.text.toString().trim()
        val targetPort = port.text.toString().trim().toIntOrNull() ?: 50001
        val targetUsername = username.text.toString()
        val targetPassword = password.text.toString()

        val parsedAddress = try {
            parseNumericIpv4(targetAddress)
        } catch (e: Exception) {
            Toast.makeText(this, "无效的 IPv4 地址: $targetAddress", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "配置错误: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val callbacks = Ic705RxSessionCallbacks(
            onStateChanged = { state ->
                runOnUiThreadFor(attempt) {
                    when (state.phase) {
                        Ic705RxSessionEngine.Phase.RECEIVING -> {
                            status.setText(R.string.ic705_rx_running)
                            status.setTextColor(getColor(R.color.md3_primary))
                        }
                        Ic705RxSessionEngine.Phase.STOPPED -> {
                            status.setText(R.string.ic705_rx_stopped)
                            status.setTextColor(getColor(R.color.md3_on_surface_variant))
                            stopReceiveOnlySession(null)
                        }
                        Ic705RxSessionEngine.Phase.FAILED -> {
                            status.text = state.failureReason ?: "连接失败"
                            status.setTextColor(getColor(R.color.md3_on_surface_variant))
                            stopReceiveOnlySession(null)
                        }
                        else -> {
                            status.setText(R.string.ic705_rx_starting)
                            status.setTextColor(getColor(R.color.md3_primary))
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
        connect.isEnabled = false
        disconnect.isEnabled = true
        status.setText(R.string.ic705_rx_starting)
        status.setTextColor(getColor(R.color.md3_primary))
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
        if (statusMessage != null) {
            status.setText(statusMessage)
            status.setTextColor(getColor(R.color.md3_on_surface_variant))
        }
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
        status.postDelayed(
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
        eventLog.text = if (snapshot.isEmpty()) getString(R.string.ic705_rx_log_empty) else snapshot
    }

    private fun restoreConnectionFields() {
        address.setText(prefs.getString("ic705.address", "192.168.59.1"))
        port.setText(prefs.getString("ic705.control_port", "50001"))
        username.setText(prefs.getString("ic705.username", ""))
        password.setText(prefs.getString("ic705.password", ""))
    }

    private fun renderStatistics() {
        statAudioBlocks.text = acceptedAudioBlocks.get().toString()
        statPcmSamples.text = acceptedAudioSamples.get().toString()
        statAx25Frames.text = decodedAx25Frames.get().toString()
        statAudioResets.text = audioResets.get().toString()
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
