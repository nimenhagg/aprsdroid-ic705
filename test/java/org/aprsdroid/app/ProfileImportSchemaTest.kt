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

    @Test
    fun profileCallsignMustFitAnAx25AddressField() {
        ProfileImportSchema.validateValue("callsign", "BG7XXX")
        ProfileImportSchema.validateValue("callsign", "BG7XXX-5".substringBefore('-'))
        expectIllegalArgument {
            ProfileImportSchema.validateValue("callsign", "BG7XXXX")
        }
    }

    @Test
    fun profileCallsignMustNotCarryAnSsidSeparator() {
        // The identity UI stores the callsign and SSID separately, so an
        // embedded SSID would be written into the call sign field verbatim.
        expectIllegalArgument {
            ProfileImportSchema.validateValue("callsign", "BG7XXX-5")
        }
    }

    @Test
    fun nonIdentityKeysAreUnaffectedByValueValidation() {
        ProfileImportSchema.validateValue("tcp.server", "a-rather-long-server-name.example.org")
        ProfileImportSchema.validateValue("ic705.address", "192.168.59.1")
    }

    @Test
    fun sensitiveKeysAreReportedForTheConfirmationDialog() {
        assertTrue(ProfileImportSchema.isSensitiveKey("tcp.server"))
        assertTrue(ProfileImportSchema.isSensitiveKey("ic705.password"))
        assertTrue(ProfileImportSchema.isSensitiveKey("passcode"))
        assertFalse(ProfileImportSchema.isSensitiveKey("show_age"))

        // Only keys actually present in the profile are listed, in schema order.
        assertEquals(
            listOf("callsign", "tcp.server"),
            ProfileImportSchema.sensitiveKeysIn(listOf("tcp.server", "show_age", "callsign")),
        )
    }

    @Test
    fun profilesWithoutConnectionChangesReportNoSensitiveKeys() {
        assertEquals(
            emptyList<String>(),
            ProfileImportSchema.sensitiveKeysIn(listOf("show_age", "mapmode", "map_lat")),
        )
    }

    private inline fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
