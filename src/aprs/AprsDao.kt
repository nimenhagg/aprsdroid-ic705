package org.aprsdroid.app.aprs

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class DaoOffset(
    val deltaLatDegrees: Double,
    val deltaLonDegrees: Double,
    val rawDao: String,
)

/**
 * APRS 1.2 DAO (Datum and High-Precision Offset) Extension.
 *
 * Extends standard APRS 0.01 minute (~18.5m) resolution down to ~0.2m (sub-meter)
 * using Base91 encoding (!wXY!) or decimal hundredths of a minute (!Wxy!).
 *
 * Example:
 * Coordinates: 22° 16.4567' N, 114° 10.3489' E
 * Standard APRS sends: 2216.45N / 11410.34E
 * Remainder lat: 0.0067' -> 0.67 * 91 = 61 -> 33 + 61 = 94 ('^')
 * Remainder lon: 0.0089' -> 0.89 * 91 = 81 -> 33 + 81 = 114 ('r')
 * DAO string: "!w^r!"
 */
object AprsDao {
    val DAO_REGEX = Regex("""!([wWyY])([!-~])([!-~])!""")

    /**
     * Encode high-precision Base91 DAO string (!wXY!) for WGS-84 coordinates.
     */
    fun encodeDao(latitude: Double, longitude: Double): String {
        val latMin = abs(latitude) * 60.0
        val lonMin = abs(longitude) * 60.0

        val latHundredths = latMin * 100.0
        val latRem = latHundredths - floor(latHundredths)
        val latCode = (33 + (latRem * 91.0).roundToInt().coerceIn(0, 90)).toChar()

        val lonHundredths = lonMin * 100.0
        val lonRem = lonHundredths - floor(lonHundredths)
        val lonCode = (33 + (lonRem * 91.0).roundToInt().coerceIn(0, 90)).toChar()

        return "!w$latCode$lonCode!"
    }

    /**
     * Parse DAO from packet text/comment.
     * Returns DaoOffset with the latitude/longitude adjustments in degrees.
     */
    fun parseDao(text: String?, isNorth: Boolean = true, isEast: Boolean = true): DaoOffset? {
        if (text == null) return null
        val match = DAO_REGEX.find(text) ?: return null
        val datum = match.groupValues[1]
        val cLat = match.groupValues[2][0]
        val cLon = match.groupValues[3][0]

        val (deltaLatMin, deltaLonMin) = when (datum) {
            "w", "y" -> {
                // Base91: 0..90 steps representing 0.01 minute interval
                val latFrac = (cLat.code - 33).coerceIn(0, 90) / 91.0
                val lonFrac = (cLon.code - 33).coerceIn(0, 90) / 91.0
                Pair(latFrac * 0.01, lonFrac * 0.01)
            }
            "W", "Y" -> {
                // Decimal: digit 0..9 representing 0.001 minute interval
                val dLat = cLat.digitToIntOrNull() ?: 0
                val dLon = cLon.digitToIntOrNull() ?: 0
                Pair(dLat * 0.001, dLon * 0.001)
            }
            else -> return null
        }

        val deltaLatDeg = (deltaLatMin / 60.0) * (if (isNorth) 1.0 else -1.0)
        val deltaLonDeg = (deltaLonMin / 60.0) * (if (isEast) 1.0 else -1.0)

        return DaoOffset(
            deltaLatDegrees = deltaLatDeg,
            deltaLonDegrees = deltaLonDeg,
            rawDao = match.value,
        )
    }

    /**
     * Remove the DAO extension token from comment text so it does not clutter UI.
     */
    fun stripDao(comment: String): String {
        return comment.replace(DAO_REGEX, "").trim()
    }

    fun stripDaoNullable(comment: String?): String? {
        return comment?.replace(DAO_REGEX, "")?.trim()
    }
}
