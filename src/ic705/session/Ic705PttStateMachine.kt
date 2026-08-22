package org.aprsdroid.app.ic705.session

import android.util.Log
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
 */
class Ic705PttStateMachine(
    private val actions: Ic705PttActions,
    val radioAddress: Int = Ic705CivCommands.DEFAULT_RADIO_ADDRESS,
    val controllerAddress: Int = Ic705CivCommands.DEFAULT_CONTROLLER_ADDRESS,
    val ackTimeoutMs: Long = DEFAULT_ACK_TIMEOUT_MS,
    val absoluteWatchdogMs: Long = DEFAULT_WATCHDOG_MS,
) {
    @Volatile
    var state: Ic705PttState = Ic705PttState.RX_IDLE
        private set

    private val isPttAsserted = AtomicBoolean(false)

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

        sendPttCommand(true)
        isPttAsserted.set(true)
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
        sendPttCommand(false)
        isPttAsserted.set(false)
        transitionTo(Ic705PttState.RX_IDLE)
    }

    @Synchronized
    fun forceRelease(reason: String = "Forced release") {
        if (isPttAsserted.get() || state != Ic705PttState.RX_IDLE) {
            runCatching { sendPttCommand(false) }
            isPttAsserted.set(false)
        }
        transitionTo(Ic705PttState.RX_IDLE)
    }

    private fun handleAck() {
        // CI-V ACK received
    }

    private fun handleNak() {
        forceRelease("Radio rejected PTT command (NAK)")
    }

    private fun sendPttCommand(pttOn: Boolean) {
        val frame = Ic705CivCommands.buildPttFrame(
            pttOn = pttOn,
            radioAddress = radioAddress,
            controllerAddress = controllerAddress,
        )
        actions.sendCivFrame(frame)
    }

    private fun transitionTo(newState: Ic705PttState) {
        Log.i("APRSdroid.IC705", "PttStateMachine: $state -> $newState")
        state = newState
        actions.onStateChanged(newState)
    }

    companion object {
        const val CIV_ACK = 0xfb
        const val CIV_NAK = 0xfa
        const val DEFAULT_ACK_TIMEOUT_MS = 500L
        const val DEFAULT_WATCHDOG_MS = 5_000L
    }
}
