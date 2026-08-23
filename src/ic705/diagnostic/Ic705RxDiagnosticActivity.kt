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
import org.aprsdroid.app.afsk.AfskDecoder
import org.aprsdroid.app.ic705.audio.Ic705AudioSink
import org.aprsdroid.app.ic705.control.Ic705ControlPort
import org.aprsdroid.app.ic705.session.Ic705PacketDiagnostic
import org.aprsdroid.app.ic705.session.Ic705ReceiveOnlySession
import java.net.InetAddress
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

    private var activeSession: Ic705ReceiveOnlySession? = null
    private var activeDecoder: AfskDecoder? = null
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
        saveConnectionFields()
    }

    private fun startReceiveOnlySession() {
        val targetAddress = address.text.toString().trim()
        val targetPort = port.text.toString().trim().toIntOrNull() ?: Ic705ControlPort.DEFAULT
        val targetUsername = username.text.toString()
        val targetPassword = password.text.toString()

        val parsedAddress = try {
            parseNumericIpv4(targetAddress)
        } catch (e: Exception) {
            Toast.makeText(this, "无效的 IPv4 地址: $targetAddress", Toast.LENGTH_SHORT).show()
            return
        }

        saveConnectionFields()
        stopReceiveOnlySession(null)

        acceptedAudioBlocks.set(0)
        acceptedAudioSamples.set(0)
        decodedAx25Frames.set(0)
        audioResets.set(0)
        lastLoggedCounters = null
        renderStatistics()

        val attempt = ++activeAttempt
        val decoder = AfskDecoder(this, 12000, true) {
            decodedAx25Frames.incrementAndGet()
            runOnUiThreadFor(attempt) { renderStatistics() }
        }
        activeDecoder = decoder

        val countingSink = object : Ic705AudioSink {
            override fun acceptAudioBlock(buffer: ByteArray, offset: Int, length: Int) {
                acceptedAudioBlocks.incrementAndGet()
                acceptedAudioSamples.addAndGet((length / 2).toLong())
                decoder.parse(buffer, offset, length)
            }
            override fun resetAudio() {
                audioResets.incrementAndGet()
            }
        }

        val session = Ic705ReceiveOnlySession(
            destinationAddress = parsedAddress,
            destinationPort = targetPort,
            username = targetUsername,
            password = targetPassword,
            sink = countingSink,
            onConnecting = {
                runOnUiThreadFor(attempt) {
                    status.setText(R.string.ic705_rx_starting)
                    status.setTextColor(getColor(R.color.md3_primary))
                }
            },
            onConnected = {
                runOnUiThreadFor(attempt) {
                    status.setText(R.string.ic705_rx_running)
                    status.setTextColor(getColor(R.color.md3_primary))
                }
            },
            onDisconnected = { message ->
                runOnUiThreadFor(attempt) {
                    status.text = message
                    status.setTextColor(getColor(R.color.md3_on_surface_variant))
                    stopReceiveOnlySession(null)
                }
            },
            onDiagnosticEvent = { event, phase, issue, channel, countName, count, packet, occurrence, reason, kind, expSeq, actSeq, missCount ->
                recordEvent(
                    attempt = attempt,
                    event = event,
                    phase = phase,
                    issue = issue,
                    channel = channel,
                    countName = countName,
                    count = count,
                    packet = packet,
                    occurrence = occurrence,
                    resetReason = reason,
                    discontinuityKind = kind,
                    expectedSequence = expSeq,
                    actualSequence = actSeq,
                    missingPacketCount = missCount
                )
            }
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
            append("ms] att=").append(attempt)
            append(" ev=").append(event)
            phase?.let { append(" ph=").append(it) }
            issue?.let { append(" is=").append(it) }
            channel?.let { append(" ch=").append(it) }
            occurrence?.let { append(" occ=").append(it) }
            resetReason?.let { append(" rsn=").append(it) }
            discontinuityKind?.let { append(" knd=").append(it) }
            expectedSequence?.let { append(" exp=").append(it) }
            actualSequence?.let { append(" act=").append(it) }
            missingPacketCount?.let { append(" mis=").append(it) }
            if (countName != null && count != null) append(' ').append(countName).append('=').append(count)
            packet?.let {
                append(" len=").append(it.length)
                it.declaredLength?.let { value -> append(" dcl=").append(value) }
                append(" rx=").append(it.receiverKind.name)
            }
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
        } else if (::eventLog.isInitialized) {
            eventLog.post(::renderEventLog)
        }
    }

    private fun renderEventLog() {
        if (activityDestroyed || !::eventLog.isInitialized) return
        val snapshot = synchronized(eventLogLock) { diagnosticEvents.joinToString(separator = "\n") }
        eventLog.text = if (snapshot.isEmpty()) getString(R.string.ic705_rx_log_empty) else snapshot
    }

    private fun restoreConnectionFields() {
        val defaultAddr = prefs.getString("ic705.address", "192.168.59.1")
        val defaultPort = prefs.getString("ic705.control_port", "50001")
        val defaultUser = prefs.getString("ic705.username", "")
        val defaultPass = prefs.getString("ic705.password", "")

        val shared = getSharedPreferences(CONNECTION_PREFERENCES, MODE_PRIVATE)
        address.setText(shared.getString(KEY_ADDRESS, defaultAddr))
        port.setText(shared.getString(KEY_PORT, defaultPort))
        username.setText(shared.getString(KEY_USERNAME, defaultUser))
        password.setText(shared.getString(KEY_PASSWORD, defaultPass))
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
        const val CONNECTION_PREFERENCES = "ic705_connection"
        const val KEY_ADDRESS = "address"
        const val KEY_PORT = "control_port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
