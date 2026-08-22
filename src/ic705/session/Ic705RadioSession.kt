package org.aprsdroid.app.ic705.session

import java.io.Closeable
import net.ab0oo.aprs.parser.APRSPacket

/**
 * Narrow lifecycle and transmission surface the APRS backend needs from an IC-705
 * radio session. The real implementation is [Ic705RxSession]; backend tests substitute
 * a fake so they can drive phases and close timing without sockets or threads.
 */
interface Ic705RadioSession : Closeable {
    val state: Ic705RxSessionEngine.State
    val isTransmitting: Boolean
        get() = false

    fun start()
    fun stop()

    /**
     * Queues and transmits an [APRSPacket] via AX.25, AFSK1200 modulation,
     * CI-V PTT ON, 48kHz audio streaming, and CI-V PTT OFF.
     * Returns true if the transmission was successfully initiated.
     */
    fun transmit(packet: APRSPacket): Boolean = false

    /** Calls [onClosed] after sockets close and the audio worker has exited. */
    fun close(onClosed: () -> Unit)
}
