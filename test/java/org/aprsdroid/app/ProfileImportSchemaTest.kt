package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileImportSchemaTest {
    @Test
    fun stringPreferenceRejectsBooleanPollution() {
        expectIllegalArgument {
            ProfileImportSchema.validateType("ic705.address", true)
        }
        ProfileImportSchema.validateType("ic705.address", "192.168.59.1")
    }

    @Test
    fun booleanPreferencesRemainBooleanEvenOnCleanInstall() {
        assertEquals(ProfilePreferenceType.BOOLEAN, ProfileImportSchema.typeFor("keepscreen"))
        assertEquals(ProfilePreferenceType.BOOLEAN, ProfileImportSchema.typeFor("conn_log"))
        ProfileImportSchema.validateType("keepscreen", true)
        expectIllegalArgument {
            ProfileImportSchema.validateType("keepscreen", "true")
        }
    }

    @Test
    fun currentBackendAndLocationKeysAreExplicitlyImportable() {
        for (key in listOf(
            "baudrate",
            "manual_lat",
            "manual_lon",
            "interval",
            "sb.fastrate",
            "sb.turntime",
            "station_tap_action",
            "kenwood.gps",
        )) {
            assertTrue("Expected importable key: $key", ProfileImportSchema.isImportableKey(key))
        }
    }

    @Test
    fun stringSetPreferenceHasStableSchema() {
        assertEquals(
            ProfilePreferenceType.STRING_SET,
            ProfileImportSchema.typeFor("digi_path_user_presets"),
        )
        ProfileImportSchema.validateType(
            "digi_path_user_presets",
            setOf("WIDE1-1", "WIDE2-1"),
        )
        expectIllegalArgument {
            ProfileImportSchema.validateType("digi_path_user_presets", "WIDE1-1")
        }
    }

    @Test
    fun mapCameraStateUsesFloatSchema() {
        assertEquals(ProfilePreferenceType.FLOAT, ProfileImportSchema.typeFor("map_lat"))
        ProfileImportSchema.validateType("map_lat", 38.0)
        ProfileImportSchema.validateType("map_zoom", 12)
    }

    @Test
    fun runtimeKeysAreBlockedAndUnknownKeysAreNotImportable() {
        assertTrue(ProfileImportSchema.isBlockedKey("service_running"))
        assertTrue(ProfileImportSchema.isBlockedKey("firstrun"))
        assertFalse(ProfileImportSchema.isImportableKey("service_running"))
        assertNull(ProfileImportSchema.typeFor("future.unrecognized.key"))
    }

    private inline fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
