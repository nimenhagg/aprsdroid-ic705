package org.aprsdroid.app.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class WlanRadioModelTest {

    @Test
    fun presetsHaveCorrectDefaultParameters() {
        assertEquals("IC-705", WlanRadioModel.IC705.id)
        assertEquals("Icom IC-705", WlanRadioModel.IC705.modelName)
        assertEquals(0xA4, WlanRadioModel.IC705.defaultCivAddress)
        assertEquals("A4", WlanRadioModel.IC705.defaultCivHex)
        assertEquals(50001, WlanRadioModel.IC705.defaultPort)

        assertEquals("IC-9700", WlanRadioModel.IC9700.id)
        assertEquals("Icom IC-9700", WlanRadioModel.IC9700.modelName)
        assertEquals(0xA2, WlanRadioModel.IC9700.defaultCivAddress)
        assertEquals("A2", WlanRadioModel.IC9700.defaultCivHex)

        assertEquals("IC-7610", WlanRadioModel.IC7610.id)
        assertEquals("Icom IC-7610", WlanRadioModel.IC7610.modelName)
        assertEquals(0x98, WlanRadioModel.IC7610.defaultCivAddress)
        assertEquals("98", WlanRadioModel.IC7610.defaultCivHex)

        assertEquals("IC-905", WlanRadioModel.IC905.id)
        assertEquals("Icom IC-905", WlanRadioModel.IC905.modelName)
        assertEquals(0xAC, WlanRadioModel.IC905.defaultCivAddress)
        assertEquals("AC", WlanRadioModel.IC905.defaultCivHex)

        assertEquals("CUSTOM", WlanRadioModel.CUSTOM.id)
        assertEquals(0xA4, WlanRadioModel.CUSTOM.defaultCivAddress)
    }

    @Test
    fun findByIdLookup() {
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById("IC-705"))
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById("ic-705"))
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById("IC705"))

        assertEquals(WlanRadioModel.IC9700, WlanRadioModel.findById("IC-9700"))
        assertEquals(WlanRadioModel.IC9700, WlanRadioModel.findById("ic-9700"))
        assertEquals(WlanRadioModel.IC9700, WlanRadioModel.findById("IC9700"))

        assertEquals(WlanRadioModel.IC7610, WlanRadioModel.findById("IC-7610"))
        assertEquals(WlanRadioModel.IC7610, WlanRadioModel.findById("ic-7610"))

        assertEquals(WlanRadioModel.IC905, WlanRadioModel.findById("IC-905"))

        assertEquals(WlanRadioModel.CUSTOM, WlanRadioModel.findById("CUSTOM"))
        assertEquals(WlanRadioModel.CUSTOM, WlanRadioModel.findById("custom"))

        // Fallbacks
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById(null))
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById(""))
        assertEquals(WlanRadioModel.IC705, WlanRadioModel.findById("non-existent-model"))
    }

    @Test
    fun parseCivAddressFormats() {
        // Plain hex
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("A4"))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("a4"))
        assertEquals(0x98, WlanRadioModel.parseCivAddress("98"))
        assertEquals(0xA2, WlanRadioModel.parseCivAddress("A2"))
        assertEquals(0xAC, WlanRadioModel.parseCivAddress("AC"))

        // Hex prefixes and suffixes
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("0xA4"))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("0xa4"))
        assertEquals(0x98, WlanRadioModel.parseCivAddress("98h"))
        assertEquals(0x98, WlanRadioModel.parseCivAddress("98H"))
        assertEquals(0x98, WlanRadioModel.parseCivAddress("0x98h"))

        // Whitespace handling
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("  A4  "))

        // Invalid or empty fallbacks
        assertEquals(0xA4, WlanRadioModel.parseCivAddress(null, defaultAddress = 0xA4))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("", defaultAddress = 0xA4))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("   ", defaultAddress = 0xA4))
        assertEquals(0xA2, WlanRadioModel.parseCivAddress("invalid", defaultAddress = 0xA2))

        // Out-of-range fallbacks (valid range 1..0xEF)
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("0", defaultAddress = 0xA4))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("F0", defaultAddress = 0xA4))
        assertEquals(0xA4, WlanRadioModel.parseCivAddress("100", defaultAddress = 0xA4))
    }

    @Test
    fun formatCivAddressOutputsUppercaseHex() {
        assertEquals("A4", WlanRadioModel.formatCivAddress(0xA4))
        assertEquals("98", WlanRadioModel.formatCivAddress(0x98))
        assertEquals("A2", WlanRadioModel.formatCivAddress(0xA2))
        assertEquals("AC", WlanRadioModel.formatCivAddress(0xAC))
    }
}
