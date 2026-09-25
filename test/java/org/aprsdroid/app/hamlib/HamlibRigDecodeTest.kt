package org.aprsdroid.app.hamlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HamlibRigDecodeTest {

    private fun line(
        modelId: Int = 1,
        mfg: String = "Hamlib",
        model: String = "Dummy",
        version: String = "0.9",
        status: Int = HamlibRig.DRIVER_STATUS_STABLE,
        flags: Int = 0b111111,
        modes: Long = HamlibModes.AM or HamlibModes.FM,
        portType: Int = 0,
    ): String = listOf(modelId, mfg, model, version, status, flags, modes, portType)
        .joinToString("\t")

    @Test
    fun decodesAllFields() {
        val rig = HamlibRig.decode(line())
        assertTrue(rig != null)
        assertEquals(1, rig!!.modelId)
        assertEquals("Hamlib Dummy", rig.displayName)
        assertEquals("0.9", rig.driverVersion)
        assertTrue(rig.isStable)
        assertEquals(0, rig.capabilities.portType)
    }

    @Test
    fun decodesCapabilityFlagsIntoNamedBooleans() {
        val getFreqOnly = HamlibRig.decode(
            line(flags = HamlibRigCapabilities.FLAG_GET_FREQUENCY),
        )!!
        assertTrue(getFreqOnly.capabilities.canGetFrequency)
        assertFalse(getFreqOnly.capabilities.canSetFrequency)
        assertFalse(getFreqOnly.capabilities.canControlFrequency)

        val full = HamlibRig.decode(line())!!
        assertTrue(full.capabilities.canControlFrequency)
        assertTrue(full.capabilities.canControlMode)
        assertTrue(full.capabilities.canControlPtt)
    }

    @Test
    fun keepsModeMaskAsBitFlags() {
        val rig = HamlibRig.decode(
            line(modes = HamlibModes.PKTUSB or HamlibModes.FM),
        )!!
        assertEquals(HamlibModes.PKTUSB or HamlibModes.FM, rig.capabilities.supportedModesMask)
        assertTrue(rig.capabilities.supportedModesMask and HamlibModes.PKTUSB != 0L)
        assertFalse(rig.capabilities.supportedModesMask and HamlibModes.LSB != 0L)
    }

    @Test
    fun malformedLinesAreRejectedInsteadOfThrowing() {
        assertNull(HamlibRig.decodeOrNull(""))
        assertNull(HamlibRig.decodeOrNull("1\tHamlib\tDummy"))
        assertNull(HamlibRig.decodeOrNull("x\tHamlib\tDummy\t0.9\t3\t63\t1\t0"))
        assertNull(HamlibRig.decodeOrNull("1\tHamlib\tDummy\t0.9\ty\t63\t1\t0"))
    }

    @Test
    fun modeMaskSurvivesValuesAboveIntRange() {
        val highBit = 1L shl 40
        val rig = HamlibRig.decode(line(modes = highBit))!!
        assertEquals(highBit, rig.capabilities.supportedModesMask)
    }
}
