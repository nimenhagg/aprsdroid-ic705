package org.aprsdroid.app.radio

/**
 * Transport-neutral PTT control contract.
 *
 * APRS audio is handled separately from this interface. Frequency and mode
 * remain user-controlled on the radio and are intentionally not exposed here.
 * This contract must not replace the IC-705 WLAN ACK-based PTT state machine.
 */
interface RadioControl : AutoCloseable {
    val radio: RadioDescriptor
    val capabilities: RadioCapabilities
    val isOpen: Boolean

    fun open()
    fun closePort()

    fun isPttOn(): Boolean
    fun setPtt(on: Boolean)

    override fun close()
}

/** Stable identity metadata independent of the selected transport. */
data class RadioDescriptor(
    val modelId: Int,
    val manufacturer: String,
    val model: String,
    val driverVersion: String = "",
    val driverStable: Boolean = false,
)

/** Capabilities required by the PTT-only generic control layer. */
data class RadioCapabilities(
    val canGetPtt: Boolean,
    val canSetPtt: Boolean,
) {
    val canControlPtt: Boolean
        get() = canGetPtt && canSetPtt
}

/**
 * Transport information is descriptive. A RadioControl implementation owns
 * the actual I/O lifecycle and validation.
 */
data class RadioTransport(
    val kind: Kind,
    val endpoint: String? = null,
) {
    enum class Kind {
        SERIAL,
        NETWORK,
        LOOPBACK,
    }
}
