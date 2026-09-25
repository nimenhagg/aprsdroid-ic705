package org.aprsdroid.app.radio

import org.aprsdroid.app.hamlib.HamlibLibrary
import org.aprsdroid.app.hamlib.HamlibRigCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * PR3 contract test against Hamlib's hardware-free dummy backend.
 *
 * No IC-705 backend, USB transport, audio path or RF operation is involved.
 */
class HamlibRadioControlTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue(HamlibLibrary.isAvailable())
    }

    @Test
    fun modelAndTransportAreIndependent() {
        val rig = HamlibRigCatalog.findByModelId(HamlibRigCatalog.DUMMY_MODEL_ID)!!
        val serial = RadioTransport(RadioTransport.Kind.SERIAL, "/dev/ttyUSB0")
        val network = RadioTransport(RadioTransport.Kind.NETWORK, "127.0.0.1:4532")

        HamlibRadioControl.create(rig, serial).use { serialControl ->
            HamlibRadioControl.create(rig, network).use { networkControl ->
                assertEquals(rig.modelId, serialControl.radio.modelId)
                assertEquals(rig.modelId, networkControl.radio.modelId)
                assertEquals(RadioTransport.Kind.SERIAL, serialControl.transport.kind)
                assertEquals(RadioTransport.Kind.NETWORK, networkControl.transport.kind)
            }
        }
    }

    @Test
    fun delegatesFrequencyModeAndPttToHamlib() {
        val rig = HamlibRigCatalog.findByModelId(HamlibRigCatalog.DUMMY_MODEL_ID)!!
        HamlibRadioControl.create(
            rig,
            RadioTransport(RadioTransport.Kind.LOOPBACK),
        ).use { control ->
            control.open()
            assertTrue(control.isOpen)

            control.setFrequencyHz(14_074_000.0)
            assertEquals(14_074_000.0, control.frequencyHz(), 1.0)

            val advertised = rig.capabilities.supportedModesMask
            val mode = if (advertised == 0L) 0L else advertised and -advertised
            control.setMode(mode)
            assertEquals(mode, control.mode().mode)

            control.setPtt(true)
            assertTrue(control.isPttOn())
            control.setPtt(false)
            assertFalse(control.isPttOn())
        }
    }

    @Test
    fun capabilityAndDescriptorDoNotDependOnTransport() {
        val rig = HamlibRigCatalog.findByModelId(HamlibRigCatalog.DUMMY_MODEL_ID)!!
        HamlibRadioControl.create(
            rig,
            RadioTransport(RadioTransport.Kind.NETWORK, "dummy"),
        ).use { control ->
            assertEquals(rig.capabilities.canControlFrequency, control.capabilities.canControlFrequency)
            assertEquals(rig.capabilities.canControlMode, control.capabilities.canControlMode)
            assertEquals(rig.capabilities.canControlPtt, control.capabilities.canControlPtt)
            assertEquals(rig.manufacturer, control.radio.manufacturer)
            assertEquals(rig.model, control.radio.model)
        }
    }
}
