package org.aprsdroid.app.hamlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HamlibErrorTest {

    @Test
    fun okIsTheOnlySuccessCode() {
        assertTrue(HamlibError.isOk(HamlibError.OK))
        assertFalse(HamlibError.isOk(-1))
        assertFalse(HamlibError.isOk(HamlibError.UNKNOWN_HANDLE))
    }

    @Test
    fun hamlibCodesMapToTheirEnumNames() {
        // Hamlib returns the negative of enum rig_errcode_e.
        assertEquals("invalid parameter", HamlibError.name(-1))
        assertEquals("communication timed out", HamlibError.name(-5))
        assertEquals("command rejected by the rig", HamlibError.name(-9))
        assertEquals("access denied", HamlibError.name(-22))
        assertEquals("success", HamlibError.name(0))
    }

    @Test
    fun jniLayerCodesDoNotCollideWithHamlibCodes() {
        assertEquals(
            "unknown or closed Hamlib handle",
            HamlibError.name(HamlibError.UNKNOWN_HANDLE),
        )
        assertEquals(
            "too many Hamlib handles open",
            HamlibError.name(HamlibError.TOO_MANY_HANDLES),
        )
        assertTrue(HamlibError.isHandleError(HamlibError.UNKNOWN_HANDLE))
        assertFalse(HamlibError.isHandleError(-1))
    }

    @Test
    fun unknownCodesAreReportedWithoutPretendingToBeKnown() {
        assertEquals("unrecognised Hamlib code -999", HamlibError.name(-999))
    }

    @Test
    fun describePrefersNativeTextWhenItAddsInformation() {
        assertEquals("communication timed out (-5)", HamlibError.describe(-5))
        assertEquals(
            "communication timed out (-5)",
            HamlibError.describe(-5, "communication timed out"),
        )
        assertEquals(
            "communication timed out (-5): io_read failed",
            HamlibError.describe(-5, "io_read failed"),
        )
    }

    @Test
    fun requireThrowsOnlyForRealFailures() {
        HamlibError.require(HamlibError.OK, "rig_get_freq")

        val error = try {
            HamlibError.require(-5, "rig_get_freq", "timeout")
            null
        } catch (thrown: HamlibException) {
            thrown
        }
        assertTrue("HamlibException expected", error != null)
        assertEquals(-5, error!!.code)
        assertTrue(error.message!!.contains("rig_get_freq failed"))
        assertTrue(error.message!!.contains("timeout"))
    }
}
