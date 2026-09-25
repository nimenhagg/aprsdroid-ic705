package org.aprsdroid.app.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioProfileTest {

    @Test
    fun ic705UsbPresetHasCorrectParameters() {
        val profile = RadioProfile.IC705_USB
        assertEquals("Icom IC-705 (USB)", profile.name)
        assertEquals(3085, profile.hamlibModelId)
        assertEquals(19200, profile.defaultBaudRate)
        assertEquals(0xA4, profile.defaultCivAddress)
        assertEquals(16000, profile.defaultAudioSampleRateHz)
        assertFalse(profile.isExperimental)
        assertTrue(profile.isIcom)
        assertTrue(profile.supportedBaudRates.contains(19200))
        assertTrue(profile.supportedBaudRates.contains(115200))
    }

    @Test
    fun ic7100And7300Presets() {
        val ic7100 = RadioProfile.IC7100_USB
        assertEquals(3070, ic7100.hamlibModelId)
        assertEquals(0x88, ic7100.defaultCivAddress)
        assertTrue(ic7100.isExperimental)

        val ic7300 = RadioProfile.IC7300_USB
        assertEquals(3073, ic7300.hamlibModelId)
        assertEquals(0x94, ic7300.defaultCivAddress)
        assertTrue(ic7300.isExperimental)
    }

    @Test
    fun dummyRigPreset() {
        val dummy = RadioProfile.HAMLIB_DUMMY
        assertEquals(1, dummy.hamlibModelId)
        assertNull(dummy.defaultCivAddress)
        assertFalse(dummy.isIcom)
        assertFalse(dummy.isExperimental)
    }

    @Test
    fun findByModelIdAndName() {
        assertEquals(RadioProfile.IC705_USB, RadioProfile.findByModelId(3085))
        assertEquals(RadioProfile.IC7100_USB, RadioProfile.findByModelId(3070))
        assertEquals(RadioProfile.FT891_USB, RadioProfile.findByModelId(1036))
        assertEquals(RadioProfile.FT991A_USB, RadioProfile.findByModelId(1035))
        assertEquals(RadioProfile.HAMLIB_DUMMY, RadioProfile.findByModelId(1))
        assertNull(RadioProfile.findByModelId(999999))

        assertEquals(RadioProfile.IC705_USB, RadioProfile.findByName("Icom IC-705 (USB)"))
        assertEquals(RadioProfile.IC705_USB, RadioProfile.findByName("icom ic-705 (usb)"))
        assertEquals(RadioProfile.FT891_USB, RadioProfile.findByName("Yaesu FT-891 (USB)"))
        assertEquals(RadioProfile.FT991A_USB, RadioProfile.findByName("Yaesu FT-991/A (USB)"))
        assertNull(RadioProfile.findByName("Unknown Radio"))
    }

    @Test
    fun customProfileCreation() {
        val custom = RadioProfile.createCustom(
            name = "FT-710",
            modelId = 1045,
            baudRate = 38400,
            civAddress = null,
        )
        assertEquals("FT-710", custom.name)
        assertEquals(1045, custom.hamlibModelId)
        assertEquals(38400, custom.defaultBaudRate)
        assertNull(custom.defaultCivAddress)
        assertTrue(custom.isExperimental)
    }

    @Test
    fun manufacturerCategorization() {
        assertEquals("Icom", RadioProfile.IC705_USB.manufacturer)
        assertEquals("Yaesu", RadioProfile.FT891_USB.manufacturer)
        assertEquals("Kenwood", RadioProfile.TS590SG_USB.manufacturer)
        assertEquals("Hamlib", RadioProfile.HAMLIB_DUMMY.manufacturer)
        assertTrue(RadioProfile.MANUFACTURERS.contains("Icom"))
        assertTrue(RadioProfile.MANUFACTURERS.contains("Yaesu"))
        assertTrue(RadioProfile.MANUFACTURERS.contains("Kenwood"))
    }
}
