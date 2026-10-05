// SPDX-License-Identifier: GPL-2.0-or-later

package org.aprsdroid.app.ic705.civ

import android.location.Location

data class ParsedNmeaPos(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speedMps: Float? = null,
    val bearing: Float? = null,
) {
    fun toLocation(): Location = Location("IC-705").also {
        it.latitude = latitude
        it.longitude = longitude
        altitude?.let { a -> it.altitude = a }
        speedMps?.let { s -> it.speed = s }
        bearing?.let { b -> it.bearing = b }
    }
}

/**
 * CI-V Protocol Parser for ICOM IC-705.
 *
 * Implements frequency extraction and GPS sentence decoding from raw CI-V frames.
 */
object Ic705CivParser {

    const val CIV_START: Byte = 0xFE.toByte()
    const val CIV_END: Byte = 0xFD.toByte()
    const val CONTROLLER_ADDRESS: Int = 0xE0

    fun createReadFrequencyCommand(radioAddr: Int): ByteArray {
        return byteArrayOf(
            CIV_START, CIV_START,
            radioAddr.toByte(), CONTROLLER_ADDRESS.toByte(),
            0x03.toByte(),
            CIV_END
        )
    }

    fun createReadGpsCommand(radioAddr: Int): ByteArray {
        return byteArrayOf(
            CIV_START, CIV_START,
            radioAddr.toByte(), CONTROLLER_ADDRESS.toByte(),
            0x23.toByte(), 0x00.toByte(),
            CIV_END
        )
    }

    /**
     * Decode operating frequency in MHz from CI-V frame (cmd 0x00 or 0x03).
     * Returns null if frame is not a frequency report.
     */
    fun parseOperatingFrequency(frame: ByteArray): Double? {
        if (frame.size < 11) return null
        if (frame[0] != CIV_START || frame[1] != CIV_START) return null
        if (frame[frame.size - 1] != CIV_END) return null

        val cmd = frame[4].toInt() and 0xFF
        val dataOffset: Int
        if (cmd == 0x00 || cmd == 0x03) {
            dataOffset = 5
        } else if (cmd == 0x25 && (frame[5].toInt() and 0xFF) == 0x00) {
            // Selected VFO frequency
            dataOffset = 6
        } else {
            return null
        }

        if (dataOffset + 5 > frame.size - 1) return null

        var hz = 0L
        var multiplier = 1L
        for (i in 0 until 5) {
            val b = frame[dataOffset + i].toInt() and 0xFF
            val lo = b and 0x0F
            val hi = (b shr 4) and 0x0F
            if (lo > 9 || hi > 9) return null
            val decimalVal = hi * 10 + lo
            hz += decimalVal * multiplier
            multiplier *= 100
        }

        if (hz <= 0) return null
        return hz / 1_000_000.0
    }

    /**
     * Parse GPS sentence or NMEA from CI-V frame (cmd 0x23).
     */
    fun parseGpsNmea(frame: ByteArray): String? {
        if (frame.size < 8) return null
        if (frame[0] != CIV_START || frame[1] != CIV_START || frame[frame.size - 1] != CIV_END) return null

        val cmd = frame[4].toInt() and 0xFF
        if (cmd != 0x23) return null

        val subCmd = frame[5].toInt() and 0xFF
        if (subCmd == 0x01) {
            // NMEA string stream
            val asciiBytes = frame.copyOfRange(6, frame.size - 1)
            return String(asciiBytes, Charsets.US_ASCII).trim()
        }
        return null
    }

    /**
     * Parse GPS sentence directly from CI-V frame.
     */
    fun parseGpsSentence(frame: ByteArray): ParsedNmeaPos? = parseGpsNmea(frame)?.let { parseNmea(it) }

    fun parseNmea(nmea: String): ParsedNmeaPos? {
        if (!nmea.startsWith("$") && !nmea.startsWith("!")) return null
        val parts = nmea.substringBefore('*').split(',')
        if (parts.isEmpty()) return null

        val type = parts[0]
        if (type.endsWith("RMC")) {
            // $--RMC,time,status,lat,N/S,lon,E/W,speed,course,date,...
            if (parts.size < 7) return null
            val status = parts[2]
            if (status != "A") return null // A = Active/Valid, V = Void

            val rawLat = parts[3]
            val latNs = parts[4]
            val rawLon = parts[5]
            val lonEw = parts[6]

            val lat = parseNmeaCoordinate(rawLat, 2, latNs == "N") ?: return null
            val lon = parseNmeaCoordinate(rawLon, 3, lonEw == "E") ?: return null

            var speedMps: Float? = null
            var bearing: Float? = null
            if (parts.size > 7 && parts[7].isNotEmpty()) {
                parts[7].toFloatOrNull()?.let { knots ->
                    speedMps = (knots * 0.514444).toFloat()
                }
            }
            if (parts.size > 8 && parts[8].isNotEmpty()) {
                parts[8].toFloatOrNull()?.let { cse ->
                    bearing = cse
                }
            }
            return ParsedNmeaPos(
                latitude = lat,
                longitude = lon,
                speedMps = speedMps,
                bearing = bearing,
            )
        } else if (type.endsWith("GGA")) {
            // $--GGA,time,lat,N/S,lon,E/W,quality,numSat,hdop,alt,altUnit,...
            if (parts.size < 6) return null
            val quality = parts.getOrNull(6)?.toIntOrNull() ?: 0
            if (quality == 0) return null

            val rawLat = parts[2]
            val latNs = parts[3]
            val rawLon = parts[4]
            val lonEw = parts[5]

            val lat = parseNmeaCoordinate(rawLat, 2, latNs == "N") ?: return null
            val lon = parseNmeaCoordinate(rawLon, 3, lonEw == "E") ?: return null

            var alt: Double? = null
            if (parts.size > 9 && parts[9].isNotEmpty()) {
                alt = parts[9].toDoubleOrNull()
            }
            return ParsedNmeaPos(
                latitude = lat,
                longitude = lon,
                altitude = alt,
            )
        }
        return null
    }

    /**
     * Parse standard NMEA $GPRMC or $GPGGA string into Android Location.
     */
    fun parseNmeaLocation(nmea: String): Location? = parseNmea(nmea)?.toLocation()

    private fun parseNmeaCoordinate(coord: String, degreeDigits: Int, isPositive: Boolean): Double? {
        if (coord.length < degreeDigits + 2) return null
        val degStr = coord.substring(0, degreeDigits)
        val minStr = coord.substring(degreeDigits)
        val deg = degStr.toDoubleOrNull() ?: return null
        val min = minStr.toDoubleOrNull() ?: return null
        val decimal = deg + (min / 60.0)
        return if (isPositive) decimal else -decimal
    }
}
