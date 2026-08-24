package org.aprsdroid.app.ic705.session

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.aprsdroid.app.ic705.protocol.Ic705CivCommands

/**
 * States of the IC-705 PTT coordinator.
 */
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
 * Fail-safe PTT and TX audio coordinator for IC-705 over Wi-Fi CI-V and Audio UDP,
 * modeled after FT8CN's proven concurrent PTT and tracked audio streaming architecture.
 *
 * Safety mechanisms:
 * - **Absolute watchdog**: if the PTT remains asserted for longer than
 *   [absoluteWatchdogMs], the state machine automatically force-releases to
 *   prevent indefinite transmission (e.g. due to Wi-Fi packet loss).
 * - **NAK handling**: a CI-V NAK from the radio triggers immediate force-release.
 * - **Reliable release**: PTT OFF is retried while waiting for a CI-V ACK. If
 *   every local send fails, the state remains asserted instead of reporting a
 *   false idle state.
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
    private var lastReleaseSendSucceeded = false

    init {
        require(ackTimeoutMs > 0L) { "ackTimeoutMs must be positive" }
        require(absoluteWatchdogMs > 0L) { "absoluteWatchdogMs must be positive" }
        require(maxReleaseAttempts > 0) { "maxReleaseAttempts must be positive" }
    }

    val isTransmitting: Boolean
        get() = state != Ic705PttState.RX_IDLE

    val isRadioPttOn: Boolean
        get() = isPttAsserted.get()

    @Synchronized
    fun onCivReceived(civFrame: ByteArray) {
        if (civFrame.size >= 6 &&
            (civFrame[0].toInt() and 0xff == 0xfe) &&
            (civFrame[1].toInt() and 0xff == 0xfe) &&
            (civFrame[2].toInt() and 0xff == controllerAddress) &&
            (civFrame[3].toInt() and 0xff == radioAddress) &&
            (civFrame[civFrame.size - 1].toInt() and 0xff == 0xfd)
        ) {
            val code = civFrame[4].toInt() and 0xff
            when (code) {
                CIV_ACK -> handleAck()
                CIV_NAK -> handleNak()
            }
        }
    }

    @Synchronized
    fun beginTransmission(): Boolean {
        if (state != Ic705PttState.RX_IDLE) return false

        cancelReleaseTimer()
        pendingCommand = PendingPttCommand.ON
        try {
            sendPttCommand(true)
        } catch (error: Exception) {
            pendingCommand = null
            throw error
        }
        isPttAsserted.set(true)
        scheduleWatchdog()
        transitionTo(Ic705PttState.TX_STREAMING)
        return true
    }

    @Synchronized
    fun onAudioStreamingStarted(): Boolean {
        if (state != Ic705PttState.TX_STREAMING) return false
        return true
    }

    @Synchronized
    fun onAudioStreamingFinished(): Boolean {
        if (state != Ic705PttState.TX_STREAMING) return false
        transitionTo(Ic705PttState.DRAINING)
        return true
    }

    @Synchronized
    fun finishTransmission() {
        if (state == Ic705PttState.RX_IDLE) return
        cancelWatchdog()
        if (state == Ic705PttState.TX_STREAMING) {
            transitionTo(Ic705PttState.DRAINING)
        }
        startRelease("Transmission finished")
    }

    @Synchronized
    fun forceRelease(reason: String = "Forced release") {
        cancelWatchdog()
        if (isPttAsserted.get() || state != Ic705PttState.RX_IDLE) {
            Log.w(TAG, "forceRelease: $reason (state=$state, pttAsserted=${isPttAsserted.get()})")
            if (state == Ic705PttState.TX_STREAMING) {
                transitionTo(Ic705PttState.DRAINING)
            }
            startRelease(reason)
        }
    }

    private fun handleAck() {
        when (pendingCommand) {
            PendingPttCommand.ON -> {
                pendingCommand = null
                Log.d(TAG, "CI-V PTT ON ACK received (state=$state)")
            }
            PendingPttCommand.OFF -> {
                Log.d(TAG, "CI-V PTT OFF ACK received after $releaseAttempts attempt(s)")
                completeRelease()
            }
            null -> Log.d(TAG, "CI-V ACK received with no pending PTT command (state=$state)")
        }
    }

    private fun handleNak() {
        when (pendingCommand) {
            PendingPttCommand.OFF -> {
                Log.e(TAG, "Radio rejected PTT OFF attempt $releaseAttempts (NAK)")
                cancelReleaseTimer()
                lastReleaseSendSucceeded = false
                if (releaseAttempts < maxReleaseAttempts) {
                    attemptRelease("Radio rejected PTT OFF command (NAK)")
                } else {
                    pendingCommand = null
                    Log.e(TAG, "PTT OFF rejected after $releaseAttempts attempts; retaining asserted state")
                }
            }
            PendingPttCommand.ON -> forceRelease("Radio rejected PTT ON command (NAK)")
            null -> {
                if (isTransmitting) {
                    forceRelease("Radio returned an unexpected NAK while transmitting")
                } else {
                    Log.w(TAG, "CI-V NAK received with no pending PTT command")
                }
            }
        }
    }

    private fun startRelease(reason: String) {
        cancelReleaseTimer()
        releaseAttempts = 0
        lastReleaseSendSucceeded = false
        attemptRelease(reason)
    }

    private fun attemptRelease(reason: String) {
        if (!isPttAsserted.get() && state == Ic705PttState.RX_IDLE) return

        releaseAttempts += 1
        pendingCommand = PendingPttCommand.OFF
        lastReleaseSendSucceeded = try {
            sendPttCommand(false)
            true
        } catch (error: Exception) {
            pendingCommand = null
            Log.e(TAG, "PTT OFF attempt $releaseAttempts/$maxReleaseAttempts failed: $reason", error)
            false
        }

        if (releaseAttempts < maxReleaseAttempts || lastReleaseSendSucceeded) {
            scheduleReleaseTimer(reason)
        } else {
            Log.e(TAG, "All $releaseAttempts PTT OFF sends failed; retaining asserted state")
        }
    }

    private fun scheduleReleaseTimer(reason: String) {
        cancelReleaseTimer()
        releaseFuture = watchdogExecutor.schedule(
            {
                synchronized(this@Ic705PttStateMachine) {
                    releaseFuture = null
                    if (!isPttAsserted.get() && state == Ic705PttState.RX_IDLE) {
                        return@synchronized
                    }
                    if (releaseAttempts < maxReleaseAttempts) {
                        Log.w(TAG, "No PTT OFF ACK after ${ackTimeoutMs}ms; retrying")
                        attemptRelease(reason)
                    } else if (lastReleaseSendSucceeded) {
                        Log.w(TAG, "No PTT OFF ACK after $releaseAttempts sends; assuming release for compatibility")
                        completeRelease()
                    }
                }
            },
            ackTimeoutMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun completeRelease() {
        cancelReleaseTimer()
        pendingCommand = null
        releaseAttempts = 0
        lastReleaseSendSucceeded = false
        isPttAsserted.set(false)
        transitionTo(Ic705PttState.RX_IDLE)
    }

    private fun sendPttCommand(pttOn: Boolean) {
        val frame = Ic705CivCommands.buildPttFrame(
            pttOn = pttOn,
            radioAddress = radioAddress,
            controllerAddress = controllerAddress,
        )
        actions.sendCivFrame(frame)
    }

    private fun scheduleWatchdog() {
        cancelWatchdog()
        watchdogFuture = watchdogExecutor.schedule(
            {
                synchronized(this@Ic705PttStateMachine) {
                    if (isTransmitting) {
                        Log.e(TAG, "PTT watchdog fired after ${absoluteWatchdogMs}ms! Force-releasing PTT.")
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
        Log.i(TAG, "PttStateMachine: $state -> $newState")
        state = newState
        actions.onStateChanged(newState)
    }

    companion object {
        private const val TAG = "APRSdroid.IC705"
        const val CIV_ACK = 0xfb
        const val CIV_NAK = 0xfa
        const val DEFAULT_ACK_TIMEOUT_MS = 500L
        const val DEFAULT_WATCHDOG_MS = 5_000L
        const val DEFAULT_RELEASE_ATTEMPTS = 3

        private enum class PendingPttCommand {
            ON,
            OFF,
        }

        private val defaultWatchdogExecutor: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "ic705-ptt-watchdog").apply { isDaemon = true }
            }
        }
    }
}
