package org.aprsdroid.app.radio

import org.aprsdroid.app.hamlib.HamlibHandle
import org.aprsdroid.app.hamlib.HamlibRig

/**
 * PTT-only RadioControl implementation backed by the generic Hamlib JNI layer.
 *
 * Frequency and mode remain outside this application control contract. The
 * transport is supplied separately from [HamlibRig]. This adapter is not wired
 * into the existing IC-705 WLAN backend and must not replace its ACK-based PTT
 * safety state machine.
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

    override fun isPttOn(): Boolean = handle.isPttOn()

    override fun setPtt(on: Boolean) {
        handle.setPtt(on)
    }

    override fun close() {
        handle.close()
    }

    companion object {
        /** Builds a PTT control from an enumerated Hamlib rig. */
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
                    canGetPtt = rig.capabilities.canGetPtt,
                    canSetPtt = rig.capabilities.canSetPtt,
                ),
                handle = handle,
                transport = transport,
            )
        }
    }
}
