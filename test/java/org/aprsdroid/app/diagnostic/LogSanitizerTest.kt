package org.aprsdroid.app.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun sensitiveKeysAreFullyRedacted() {
        assertEquals("[REDACTED]", LogSanitizer.sanitizeValue("password", "hunter2"))
        assertEquals("[REDACTED]", LogSanitizer.sanitizeValue("aprs_passcode", "12345"))
        assertEquals("[REDACTED]", LogSanitizer.sanitizeValue("tokenValue", "abc"))
        assertEquals("[REDACTED]", LogSanitizer.sanitizeValue("lat", "31.1234"))
    }

    @Test
    fun freeTextRedactsSecretsAndCoordinates() {
        val sanitized = LogSanitizer.sanitizeText(
            "connect failed password=hunter2 token:abc123 lat=31.1234 longitude:121.5678 retry"
        )

        assertFalse(sanitized.contains("hunter2"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("31.1234"))
        assertFalse(sanitized.contains("121.5678"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("token:[REDACTED]"))
        assertTrue(sanitized.contains("lat=[REDACTED]"))
        assertTrue(sanitized.contains("longitude:[REDACTED]"))
    }

    @Test
    fun benignTextIsPreserved() {
        assertEquals(
            "socket closed while reconnecting",
            LogSanitizer.sanitizeText("socket closed while reconnecting")
        )
    }
}
