package org.aprsdroid.app.radio

/**
 * Transport-neutral control contract for a radio.
 *
 * APRS/AX.25/audio code depends on this interface rather than on a particular
 * radio model or CAT protocol. Implementations own their transport details.
 *
 * This is deliberately a control API only. It does not own APRS audio, packet
 * encoding, or the IC-705 WLAN PTT safety state machine.
 */
interface RadioControl : AutoCloseable {
    val radio: RadioDescriptor
    val capabilities: RadioCapabilities
    val isOpen: Boolean

    fun open()
    fun closePort()

    fun frequencyHz(): Double
    fun setFrequencyHz(hz: Double)

    fun mode(): RadioMode
    fun setMode(mode: Long, passbandHz: Long = 0L)

    fun isPttOn(): Boolean
    fun setPtt(on: Boolean)

    override fun close()
}

/** Stable identity/capability metadata independent of the selected transport. */
data class RadioDescriptor(
    val modelId: Int,
    val manufacturer: String,
    val model: String,
    val driverVersion: String = "",
    val driverStable: Boolean = false,
)

/** The subset of radio capabilities needed by the generic control layer. */
data class RadioCapabilities(
    val canGetFrequency: Boolean,
    val canSetFrequency: Boolean,
    val canGetMode: Boolean,
    val canSetMode: Boolean,
    val canGetPtt: Boolean,
    val canSetPtt: Boolean,
    val supportedModesMask: Long = 0L,
) {
    val canControlFrequency: Boolean
        get() = canGetFrequency && canSetFrequency

    val canControlMode: Boolean
        get() = canGetMode && canSetMode

    val canControlPtt: Boolean
        get() = canGetPtt && canSetPtt
}

/** Generic mode value returned by a [RadioControl]. */
data class RadioMode(
    val mode: Long,
    val passbandHz: Long,
)

/**
 * Transport information is intentionally descriptive rather than executable.
 * A RadioControl implementation owns the actual I/O lifecycle.
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
