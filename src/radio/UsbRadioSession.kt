package org.aprsdroid.app.radio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.audio.PcmSource
import org.aprsdroid.app.diagnostic.AppLog

/**
 * Coordinated session managing a complete USB Radio (CAT Control + USB Audio + APRS AFSK).
 *
 * Implements ROADMAP PR 6:
 * - Coordinates LocalLoopbackCatBridge, Hamlib RadioControl, and RadioAudioBackend.
 * - Enforces safe initialization and strict teardown sequences.
 * - Handles sudden USB detach by instantly releasing PTT and stopping audio.
 */
class UsbRadioSession(
    val profile: RadioProfile,
    val catStream: RadioByteStream,
    val audioSource: PcmSource,
    val audioSink: PcmSink,
    val radioControlFactory: (RadioTransport) -> RadioControl,
    val decoder: PcmSink? = null,
    val onPacketReceived: (ByteArray) -> Unit = {},
    val onStateChanged: (State, String?) -> Unit = { _, _ -> },
) : AutoCloseable {

    companion object {
        private const val TAG = "RADIO.USB_SESSION"
    }

    enum class State {
        IDLE,
        STARTING,
        RUNNING,
        STOPPED,
        ERROR,
    }

    private val currentState = AtomicReference(State.IDLE)
    private val closed = AtomicBoolean(false)

    val state: State get() = currentState.get()
    val isRunning: Boolean get() = state == State.RUNNING
    val isClosed: Boolean get() = closed.get()

    private var bridge: LocalLoopbackCatBridge? = null
    private var radioControl: RadioControl? = null
    private var audioBackend: RadioAudioBackend? = null

    @Synchronized
    fun start() {
        check(!closed.get()) { "Session is closed" }
        if (!currentState.compareAndSet(State.IDLE, State.STARTING)) {
            return
        }
        notifyState(State.STARTING, "Starting CAT bridge and radio audio...")

        try {
            // 1. Start local loopback CAT bridge
            val localBridge = LocalLoopbackCatBridge(catStream)
            bridge = localBridge
            localBridge.start()
            AppLog.i(TAG, "cat_bridge_started", mapOf("endpoint" to localBridge.endpoint))

            // 2. Instantiate and open RadioControl via loopback endpoint
            val transport = RadioTransport(RadioTransport.Kind.LOOPBACK, localBridge.endpoint)
            val control = radioControlFactory(transport)
            radioControl = control
            control.open()
            AppLog.i(TAG, "radio_control_opened", mapOf("radio" to control.radio.model))

            // 3. Instantiate RadioAudioBackend
            val backend = if (decoder != null) {
                RadioAudioBackend(
                    radioControl = control,
                    audioSource = audioSource,
                    audioSink = audioSink,
                    decoder = decoder,
                    txSampleRateHz = profile.defaultAudioSampleRateHz,
                )
            } else {
                RadioAudioBackend.create(
                    radioControl = control,
                    audioSource = audioSource,
                    audioSink = audioSink,
                    rxSampleRateHz = profile.defaultAudioSampleRateHz,
                    txSampleRateHz = profile.defaultAudioSampleRateHz,
                    onPacketReceived = onPacketReceived,
                )
            }
            audioBackend = backend
            backend.start()

            currentState.set(State.RUNNING)
            notifyState(State.RUNNING, "Connected to ${profile.name}")
        } catch (e: Exception) {
            AppLog.w(TAG, "session_start_failed", error = e)
            teardownResources()
            currentState.set(State.ERROR)
            notifyState(State.ERROR, e.message ?: "Failed to start radio session")
            throw e
        }
    }

    /**
     * Transmits an APRS packet via the coordinated audio and PTT sequence.
     */
    @Synchronized
    fun transmit(packet: APRSPacket): RadioPttSequence.Result {
        check(isRunning) { "Session is not running (state=$state)" }
        val backend = audioBackend ?: throw IllegalStateException("Audio backend not initialized")
        return backend.transmit(packet)
    }

    /**
     * Handles physical USB detachment immediately:
     * Releases PTT, tears down audio and serial bridges, and enters STOPPED state.
     */
    fun onUsbDetached() {
        if (closed.get()) return
        AppLog.w(TAG, "usb_detached_event")
        close()
        notifyState(State.STOPPED, "USB device detached")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        AppLog.i(TAG, "closing_usb_radio_session")
        teardownResources()
        currentState.set(State.STOPPED)
        notifyState(State.STOPPED, "Session closed")
    }

    private fun teardownResources() {
        // Strict teardown ordering:
        // 1. Stop audio backend (aborts TX, stops RX loop, ensures PTT is OFF)
        runCatching { audioBackend?.close() }
        audioBackend = null

        // 2. Close RadioControl
        runCatching { radioControl?.close() }
        radioControl = null

        // 3. Close CAT loopback bridge
        runCatching { bridge?.close() }
        bridge = null

        // 4. Close underlying streams
        runCatching { audioSource.close() }
        runCatching { audioSink.close() }
        runCatching { catStream.close() }
    }

    private fun notifyState(state: State, message: String?) {
        runCatching { onStateChanged(state, message) }
    }
}
