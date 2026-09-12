package org.aprsdroid.app

import org.junit.Assert.fail
import org.junit.Test

class ProfileImportSchemaTest {
    @Test
    fun unsetStringPreferenceRejectsBooleanPollution() {
        expectIllegalArgument {
            ProfileImportSchema.validateType("ic705.address", existing = null, incoming = true)
        }
    }

    @Test
    fun unsetStringPreferenceAcceptsString() {
        ProfileImportSchema.validateType("ic705.address", existing = null, incoming = "192.168.59.1")
    }

    @Test
    fun knownBooleanPreferenceUsesSchemaEvenWhenUnset() {
        ProfileImportSchema.validateType("afsk.btsco", existing = null, incoming = true)
        expectIllegalArgument {
            ProfileImportSchema.validateType("afsk.btsco", existing = null, incoming = "true")
        }
    }

    @Test
    fun existingStoredTypeRemainsAuthoritativeForLegacyKeys() {
        ProfileImportSchema.validateType("legacy.key", existing = 1L, incoming = 1)
        expectIllegalArgument {
            ProfileImportSchema.validateType("legacy.key", existing = 1, incoming = "1")
        }
    }

    private inline fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
