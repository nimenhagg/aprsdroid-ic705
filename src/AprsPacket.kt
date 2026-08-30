package org.aprsdroid.app

import android.location.Location
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Position
import java.util.Locale
import kotlin.math.abs

object AprsPacket {
    private val QRG_RE = Regex(".*?(\\d{2,3}[.,]\\d{3,4}).*?")
    const val DirectionsLatitude = "NS"
    const val DirectionsLongitude = "EW"
    @JvmField
    val APRS_AMBIGUITY_METERS = intArrayOf(6, 37185, 6200, 620, 62)

    @JvmStatic
    fun passcode(callssid: String): Int {
        val call = callssid.split("-")[0].uppercase(Locale.US) + "\u0000"
        var hash = 0x73e2
        for (i in 0 until call.length - 1 step 2) {
            hash = hash xor (call[i].code shl 8)
            hash = hash xor call[i + 1].code
        }
        return hash and 0x7fff
    }

    @JvmStatic
    fun passcodeAllowed(callssid: String, pass: String?, optional: Boolean): Boolean {
        return when (pass) {
            null, "", "-1" -> optional
            else -> passcode(callssid).toString() == pass
        }
    }

    @JvmStatic
    fun formatCallSsid(callsign: String, ssid: String?): String {
        val normalizedSsid = ssid?.trim().orEmpty()
        return if (normalizedSsid.isEmpty() || normalizedSsid == "0") {
            callsign
        } else {
            "$callsign-$normalizedSsid"
        }
    }

    @JvmStatic
    fun m2ft(meter: Double): Int = (meter * 3.2808399).toInt()

    @JvmStatic
    fun mps2kt(mps: Double): Int = (mps * 1.94384449).toInt()

    @JvmStatic
    fun formatAltitude(location: Location): String {
        return if (location.hasAltitude()) {
            String.format(Locale.US, "/A=%06d", m2ft(location.altitude))
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatCourseSpeed(location: Location): String {
        return if (location.hasSpeed() && location.hasBearing()) {
            String.format(Locale.US, "%03d/%03d", location.bearing.toInt(), mps2kt(location.speed.toDouble()))
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatFreq(csespd: String, freq: Float): String {
        return if (freq == 0f) {
            ""
        } else {
            val prefix = if (csespd.isNotEmpty()) "/" else ""
            prefix + String.format(Locale.US, "%07.3fMHz", freq)
        }
    }

    @JvmStatic
    fun formatLogin(callsign: String, ssid: String?, passcode: String, version: String): String {
        return String.format(Locale.US, "user %s pass %s vers %s", formatCallSsid(callsign, ssid), passcode, version)
    }

    @JvmStatic
    fun formatRangeFilter(loc: Location?, range: Int): String {
        return if (loc != null) {
            String.format(Locale.US, "r/%1.3f/%1.3f/%d", loc.latitude, loc.longitude, range)
        } else {
            ""
        }
    }

    @JvmStatic
    fun formatDMS(coordinate: Float, nesw: String): String {
        val dms = Location.convert(abs(coordinate.toDouble()), Location.FORMAT_SECONDS).split(":")
        val neswIdx = if (coordinate < 0) 1 else 0
        return String.format(Locale.US, "%2s° %2s' %s\" %s", dms[0], dms[1], dms[2], nesw[neswIdx])
    }

    @JvmStatic
    fun formatCoordinates(latitude: Float, longitude: Float): Pair<String, String> {
        return Pair(formatDMS(latitude, DirectionsLatitude), formatDMS(longitude, DirectionsLongitude))
    }

    @JvmStatic
    fun parseQrg(comment: String?): String? {
        if (comment == null) return null
        val match = QRG_RE.find(comment)
        return match?.groupValues?.getOrNull(1)
    }

    @JvmStatic
    fun parseHostPort(hostport: String, defaultport: Int): Pair<String, Int> {
        val splits = hostport.trim().split(":")
        return try {
            Pair(splits[0], splits[1].toInt())
        } catch (_: Throwable) {
            Pair(splits[0], defaultport)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun position2location(ts: Long, p: Position, cse: CourseAndSpeedExtension? = null): Location {
        val l = Location("APRS")
        l.latitude = p.latitude
        l.longitude = p.longitude
        l.time = ts
        l.accuracy = APRS_AMBIGUITY_METERS[p.positionAmbiguity].toFloat()
        if (cse != null) {
            l.bearing = cse.course.toFloat()
            l.speed = cse.speed / 1.94384449f
        }
        return l
    }
}
