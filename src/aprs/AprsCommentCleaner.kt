package org.aprsdroid.app.aprs

/**
 * Strips APRS protocol data extensions (DAO, PHG, RNG, Altitude, Frequency, WX tokens)
 * from comment/remarks, normalizing remaining text and trimming delimiter debris.
 *
 * This ensures raw protocol codes (such as "PHG2430" or "/A=000120") are not dumped
 * into user-facing remarks/comment fields.
 */
object AprsCommentCleaner {
    private val DAO_REGEX = Regex("""!([wWyY])([!-~])([!-~])!""")
    private val PHG_REGEX = Regex("""\bPHG[0-9]{4}(?!\d)""")
    private val RNG_REGEX = Regex("""\bRNG[0-9]{4}\b""")
    private val ALTITUDE_REGEX = Regex("""(?:^|[\s/])A=(\d{6})(?!\d)""")
    private val FREQ_REGEX = Regex("""(?:^|[\s/])\d{2,4}\.\d{3,4}MHz\b""", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Strips all protocol tokens and returns only clean human remarks, or null if none remain.
     */
    fun clean(comment: String?): String? {
        if (comment.isNullOrBlank()) return null

        var text = comment
        text = text.replace(DAO_REGEX, " ")
        text = text.replace(PHG_REGEX, " ")
        text = text.replace(RNG_REGEX, " ")
        text = text.replace(ALTITUDE_REGEX, " ")
        text = text.replace(FREQ_REGEX, " ")
        text = AprsWeather.stripWeather(text)

        // Normalize spaces
        text = MULTI_SPACE_REGEX.replace(text, " ").trim()

        // Strip leading/trailing punctuation artifacts like '/', '-', '|'
        text = text.trimStart('/', '-', '|', ' ').trimEnd('/', '-', '|', ' ').trim()

        return text.takeIf { it.isNotEmpty() }
    }

    fun stripPhg(comment: String?): String? {
        if (comment.isNullOrBlank()) return null
        return MULTI_SPACE_REGEX.replace(comment.replace(PHG_REGEX, " "), " ").trim().takeIf { it.isNotEmpty() }
    }

    fun stripRng(comment: String?): String? {
        if (comment.isNullOrBlank()) return null
        return MULTI_SPACE_REGEX.replace(comment.replace(RNG_REGEX, " "), " ").trim().takeIf { it.isNotEmpty() }
    }

    fun stripAltitude(comment: String?): String? {
        if (comment.isNullOrBlank()) return null
        return MULTI_SPACE_REGEX.replace(comment.replace(ALTITUDE_REGEX, " "), " ").trim().trimStart('/').takeIf { it.isNotEmpty() }
    }

    fun stripFrequency(comment: String?): String? {
        if (comment.isNullOrBlank()) return null
        return MULTI_SPACE_REGEX.replace(comment.replace(FREQ_REGEX, " "), " ").trim().trimStart('/').takeIf { it.isNotEmpty() }
    }
}
