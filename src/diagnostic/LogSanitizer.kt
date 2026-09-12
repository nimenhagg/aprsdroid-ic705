package org.aprsdroid.app.diagnostic

import java.util.Locale

internal object LogSanitizer {
    private val secretAssignment = Regex(
        """(?i)\b(password|passcode|secret|token)\s*([:=])\s*([^\s,;&]+)"""
    )
    private val coordinateAssignment = Regex(
        """(?i)\b(latitude|longitude|lat|lon)\s*([:=])\s*[-+]?\d+(?:\.\d+)?"""
    )

    fun sanitizeValue(key: String, raw: String): String {
        val lower = key.lowercase(Locale.ROOT)
        return when {
            lower.contains("password") ||
                lower.contains("passcode") ||
                lower.contains("secret") ||
                lower.contains("token") -> "[REDACTED]"
            lower.contains("latitude") ||
                lower.contains("longitude") ||
                lower == "lat" ||
                lower == "lon" -> "[REDACTED]"
            else -> sanitizeText(raw).take(2048)
        }
    }

    fun sanitizeText(raw: String): String {
        val secretsRedacted = secretAssignment.replace(raw) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
        }
        return coordinateAssignment.replace(secretsRedacted) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
        }
    }
}
