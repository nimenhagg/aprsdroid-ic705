package org.aprsdroid.app.radio

import org.aprsdroid.app.hamlib.HamlibHandle
import org.aprsdroid.app.hamlib.HamlibRig

/**
 * RadioControl implementation backed by the generic Hamlib JNI layer.
 *
 * The transport is deliberately supplied separately from [HamlibRig]:
 * selecting a radio model does not select USB, serial or network transport.
 *
 * This class is not wired into the existing IC-705 WLAN backend yet. In
 * particular, [HamlibHandle.setPtt] must not replace the IC-705 ACK-based PTT
 * state machine.
 */
class HamlibRadioControl(
    override val radio: RadioDescriptor,
    override val capabilities: RadioCapabilities,
    private val handle: HamlibHandle,
    val transport: RadioTransport,
) : RadioControl {

    override val isOpen: Boolean
        get() = handle.isOpen

    override fun open() {
        handle.open(transport.endpoint)
    }

    override fun closePort() {
        handle.closePort()
    }

    override fun frequencyHz(): Double = handle.frequencyHz()

    override fun setFrequencyHz(hz: Double) {
        handle.setFrequencyHz(hz)
    }

    override fun mode(): RadioMode {
        val value = handle.mode()
        return RadioMode(value.mode, value.passbandHz)
    }

    override fun setMode(mode: Long, passbandHz: Long) {
        handle.setMode(mode, passbandHz)
    }

    override fun isPttOn(): Boolean = handle.isPttOn()

    override fun setPtt(on: Boolean) {
        handle.setPtt(on)
    }

    override fun close() {
        handle.close()
    }

    companion object {
        /**
         * Builds a generic control from an enumerated Hamlib rig.
         *
         * The caller owns the transport choice; this method never infers one
         * from the model number.
         */
        fun create(
            rig: HamlibRig,
            transport: RadioTransport,
        ): HamlibRadioControl {
            val handle = HamlibHandle.create(rig.modelId)
            return HamlibRadioControl(
                radio = RadioDescriptor(
                    modelId = rig.modelId,
                    manufacturer = rig.manufacturer,
                    model = rig.model,
                    driverVersion = rig.driverVersion,
                    driverStable = rig.isStable,
                ),
                capabilities = RadioCapabilities(
                    canGetFrequency = rig.capabilities.canGetFrequency,
                    canSetFrequency = rig.capabilities.canSetFrequency,
                    canGetMode = rig.capabilities.canGetMode,
                    canSetMode = rig.capabilities.canSetMode,
                    canGetPtt = rig.capabilities.canGetPtt,
                    canSetPtt = rig.capabilities.canSetPtt,
                    supportedModesMask = rig.capabilities.supportedModesMask,
                ),
                handle = handle,
                transport = transport,
            )
        }
    }
}
