package org.aprsdroid.app.ic705.session

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.diagnostic.Ic705DiagnosticState
import org.aprsdroid.app.ic705.protocol.Ic705CivCommands

/** States of the IC-705 PTT coordinator. */
enum class Ic705PttState {
    RX_IDLE,
    TX_STREAMING,
    DRAINING,
}

/** Callbacks for PTT state machine actions. */
interface Ic705PttActions {
    fun sendCivFrame(frame: ByteArray)
    fun sendAudioDatagram(datagram: ByteArray)
    fun onStateChanged(state: Ic705PttState)
}

/**
 * Fail-safe PTT and TX audio coordinator for IC-705 over Wi-Fi CI-V and Audio UDP.
 *
 * A release is considered confirmed only after a radio ACK. If local sends fail
 * or the ACK is lost, the state deliberately remains asserted and the absolute
 * watchdog keeps retrying rather than reporting a false RX state.
 *
 * [shutdown] terminates this coordinator's local lifetime only. It deliberately
 * does not report RX_IDLE or clear [isRadioPttOn], because losing the local
 * session is not evidence that the radio actually released PTT.
 */
class Ic705PttStateMachine(
    private val actions: Ic705PttActions,
    val radioAddress: Int = Ic705CivCommands.DEFAULT_RADIO_ADDRESS,
    val controllerAddress: Int = Ic705CivCommands.DEFAULT_CONTROLLER_ADDRESS,
    val ackTimeoutMs: Long = DEFAULT_ACK_TIMEOUT_MS,
    val absoluteWatchdogMs: Long = DEFAULT_WATCHDOG_MS,
    val maxReleaseAttempts: Int = DEFAULT_RELEASE_ATTEMPTS,
    private val watchdogExecutor: ScheduledExecutorService = defaultWatchdogExecutor,
) {
    @Volatile
    var state: Ic705PttState = Ic705PttState.RX_IDLE
        private set

    private val isPttAsserted = AtomicBoolean(false)
    private var watchdogFuture: ScheduledFuture<*>? = null
    private var releaseFuture: ScheduledFuture<*>? = null
    private var pendingCommand: PendingPttCommand? = null
    private var releaseAttempts = 0
    private var shutdown = false

    init {
        require(ackTimeoutMs > 0L) { "ackTimeoutMs must be positive" }
        require(absoluteWatchdogMs > 0L) { "absoluteWatchdogMs must be positive" }
        require(maxReleaseAttempts > 0) { "maxReleaseAttempts must be positive" }
        publishState()
    }

    /** True while the radio may still have PTT asserted. */
    val isTransmitting: Boolean
        get() = state != Ic705PttState.RX_IDLE

    /** True only while TX audio is allowed to continue being emitted. */
    val canStreamAudio: Boolean
        get() = state == Ic705PttState.TX_STREAMING && synchronized(this) { !shutdown }

    val isRadioPttOn: Boolean
        get() = isPttAsserted.get()

    val isShutdown: Boolean
        get() = synchronized(this) { shutdown }

    @Synchronized
    fun onCivReceived(civFrame: ByteArray) {
        if (shutdown) return
        if (civFrame.size >= 6 &&
            (civFrame[0].toInt() and 0xff == 0xfe) &&
            (civFrame[1].toInt() and 0xff == 0xfe) &&
            (civFrame[2].toInt() and 0xff == controllerAddress) &&
            (civFrame[3].toInt() and 0xff == radioAddress) &&
            (civFrame[civFrame.size - 1].toInt() and 0xff == 0xfd)
        ) {
            when (civFrame[4].toInt() and 0xff) {
                CIV_ACK -> handleAck()
                CIV_NAK -> handleNak()
            }
        }
    }

    @Synchronized
    fun beginTransmission(): Boolean {
        if (shutdown || state != Ic705PttState.RX_IDLE) return false
        AppLog.i("IC705.PTT", "ptt_on_requested")
        cancelReleaseTimer()
        pendingCommand = PendingPttCommand.ON
        try {
            sendPttCommand(true)
        } catch (error: Exception) {
            pendingCommand = null
            AppLog.e("IC705.PTT", "ptt_on_send_failed", error = error)
            throw error
        }
        isPttAsserted.set(true)
        scheduleWatchdog()
        transitionTo(Ic705PttState.TX_STREAMING)
        return true
    }

    @Synchronized
    fun onAudioStreamingStarted(): Boolean =
        !shutdown && state == Ic705PttState.TX_STREAMING

    @Synchronized
    fun onAudioStreamingFinished(): Boolean {
        if (shutdown || state != Ic705PttState.TX_STREAMING) return false
        transitionTo(Ic705PttState.DRAINING)
        return true
    }

    @Synchronized
    fun finishTransmission() {
        if (shutdown || state == Ic705PttState.RX_IDLE) return
        if (state == Ic705PttState.TX_STREAMING) transitionTo(Ic705PttState.DRAINING)
        if (pendingCommand == PendingPttCommand.OFF) return
        startRelease("Transmission finished")
    }

    @Synchronized
    fun forceRelease(reason: String = "Forced release") {
        if (shutdown) return
        cancelWatchdog()
        if (isPttAsserted.get() || state != Ic705PttState.RX_IDLE) {
            AppLog.w(
                "IC705.PTT",
                "force_release",
                mapOf("reason" to reason, "state" to state, "ptt_asserted" to isPttAsserted.get()),
            )
            if (state == Ic705PttState.TX_STREAMING) transitionTo(Ic705PttState.DRAINING)
            if (pendingCommand != PendingPttCommand.OFF) startRelease(reason)
        }
    }

    /**
     * Cancels all future local PTT callbacks without claiming the radio is in RX.
     * This is used only when the owning session is permanently being destroyed.
     */
    @Synchronized
    fun shutdown() {
        if (shutdown) return
        shutdown = true
        cancelReleaseTimer()
        cancelWatchdog()
        pendingCommand = null
        releaseAttempts = 0
        AppLog.i(
            "IC705.PTT",
            "coordinator_shutdown",
            mapOf("state" to state, "ptt_asserted" to isPttAsserted.get()),
        )
        publishState()
    }

    private fun handleAck() {
        when (pendingCommand) {
            PendingPttCommand.ON -> {
                pendingCommand = null
                AppLog.d("IC705.PTT", "ptt_on_ack", mapOf("state" to state))
            }
            PendingPttCommand.OFF -> {
                AppLog.d("IC705.PTT", "ptt_off_ack", mapOf("attempts" to releaseAttempts))
                completeRelease()
            }
            null -> AppLog.d("IC705.PTT", "unexpected_ack", mapOf("state" to state))
        }
    }

    private fun handleNak() {
        when (pendingCommand) {
            PendingPttCommand.OFF -> {
                AppLog.e("IC705.PTT", "ptt_off_nak", mapOf("attempt" to releaseAttempts))
                cancelReleaseTimer()
                if (releaseAttempts < maxReleaseAttempts) {
                    attemptRelease("Radio rejected PTT OFF command (NAK)")
                } else {
                    pendingCommand = null
                    AppLog.e(
                        "IC705.PTT",
                        "ptt_off_rejected",
                        mapOf("attempts" to releaseAttempts, "retaining_asserted_state" to true),
                    )
                    scheduleWatchdog()
                }
            }
            PendingPttCommand.ON -> forceRelease("Radio rejected PTT ON command (NAK)")
            null -> {
                if (isTransmitting) forceRelease("Radio returned an unexpected NAK while transmitting")
                else AppLog.w("IC705.PTT", "unexpected_nak")
            }
        }
    }

    private fun startRelease(reason: String) {
        if (shutdown) return
        cancelReleaseTimer()
        releaseAttempts = 0
        attemptRelease(reason)
    }

    private fun attemptRelease(reason: String) {
        if (shutdown || (!isPttAsserted.get() && state == Ic705PttState.RX_IDLE)) return
        releaseAttempts += 1
        pendingCommand = PendingPttCommand.OFF
        AppLog.i(
            "IC705.PTT",
            "ptt_off_attempt",
            mapOf("attempt" to releaseAttempts, "max_attempts" to maxReleaseAttempts, "reason" to reason),
        )
        val sendSucceeded = try {
            sendPttCommand(false)
            true
        } catch (error: Exception) {
            pendingCommand = null
            AppLog.e(
                "IC705.PTT",
                "ptt_off_send_failed",
                mapOf("attempt" to releaseAttempts, "max_attempts" to maxReleaseAttempts, "reason" to reason),
                error,
            )
            false
        }

        if (releaseAttempts < maxReleaseAttempts) {
            scheduleReleaseTimer(reason)
        } else if (sendSucceeded) {
            scheduleReleaseTimer(reason)
        } else {
            AppLog.e(
                "IC705.PTT",
                "ptt_off_all_sends_failed",
                mapOf("attempts" to releaseAttempts, "retaining_asserted_state" to true),
            )
            scheduleWatchdog()
        }
    }

    private fun scheduleReleaseTimer(reason: String) {
        if (shutdown) return
        cancelReleaseTimer()
        releaseFuture = watchdogExecutor.schedule(
            {
                synchronized(this@Ic705PttStateMachine) {
                    releaseFuture = null
                    if (shutdown) return@synchronized
                    if (!isPttAsserted.get() && state == Ic705PttState.RX_IDLE) return@synchronized
                    if (releaseAttempts < maxReleaseAttempts) {
                        AppLog.w(
                            "IC705.PTT",
                            "ptt_off_ack_timeout_retry",
                            mapOf("timeout_ms" to ackTimeoutMs, "attempt" to releaseAttempts),
                        )
                        attemptRelease(reason)
                    } else {
                        pendingCommand = null
                        AppLog.e(
                            "IC705.PTT",
                            "ptt_off_ack_timeout",
                            mapOf("attempts" to releaseAttempts, "retaining_asserted_state" to true),
                        )
                        scheduleWatchdog()
                    }
                }
            },
            ackTimeoutMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun completeRelease() {
        if (shutdown) return
        cancelReleaseTimer()
        cancelWatchdog()
        pendingCommand = null
        releaseAttempts = 0
        isPttAsserted.set(false)
        transitionTo(Ic705PttState.RX_IDLE)
    }

    private fun sendPttCommand(pttOn: Boolean) {
        actions.sendCivFrame(
            Ic705CivCommands.buildPttFrame(
                pttOn = pttOn,
                radioAddress = radioAddress,
                controllerAddress = controllerAddress,
            ),
        )
    }

    private fun scheduleWatchdog() {
        if (shutdown) return
        cancelWatchdog()
        watchdogFuture = watchdogExecutor.schedule(
            {
                synchronized(this@Ic705PttStateMachine) {
                    if (shutdown) return@synchronized
                    if (isTransmitting) {
                        AppLog.e(
                            "IC705.PTT",
                            "absolute_watchdog_fired",
                            mapOf("watchdog_ms" to absoluteWatchdogMs, "state" to state),
                        )
                        forceRelease("PTT absolute watchdog timeout (${absoluteWatchdogMs}ms)")
                    }
                }
            },
            absoluteWatchdogMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun cancelWatchdog() {
        watchdogFuture?.cancel(false)
        watchdogFuture = null
    }

    private fun cancelReleaseTimer() {
        releaseFuture?.cancel(false)
        releaseFuture = null
    }

    private fun transitionTo(newState: Ic705PttState) {
        if (state == newState) return
        val oldState = state
        state = newState
        AppLog.i("IC705.PTT", "state_transition", mapOf("from" to oldState, "to" to newState))
        publishState()
        actions.onStateChanged(newState)
    }

    private fun publishState() {
        Ic705DiagnosticState.set("ptt_state", state)
        Ic705DiagnosticState.set("ptt_asserted_possible", isPttAsserted.get() || state != Ic705PttState.RX_IDLE)
        Ic705DiagnosticState.set("can_stream_audio", canStreamAudio)
    }

    companion object {
        const val CIV_ACK = 0xfb
        const val CIV_NAK = 0xfa
        const val DEFAULT_ACK_TIMEOUT_MS = 500L
        const val DEFAULT_WATCHDOG_MS = 5_000L
        const val DEFAULT_RELEASE_ATTEMPTS = 3

        private enum class PendingPttCommand { ON, OFF }

        private val defaultWatchdogExecutor: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "ic705-ptt-watchdog").apply { isDaemon = true }
            }
        }
    }
}
