package org.aprsdroid.app.aprs

import java.util.Locale
import kotlin.math.roundToInt

data class WeatherData(
    val windDirDeg: Int? = null,
    val windSpeedMph: Int? = null,
    val windGustMph: Int? = null,
    val tempF: Int? = null,
    val rain1hHundredths: Int? = null,
    val rain24hHundredths: Int? = null,
    val rainMidnightHundredths: Int? = null,
    val humidityPercent: Int? = null,
    val pressureTenthsHpa: Int? = null,
    val rawReport: String = "",
) {
    val tempC: Double?
        get() = tempF?.let { (it - 32) * 5.0 / 9.0 }

    val windSpeedKmh: Double?
        get() = windSpeedMph?.let { it * 1.60934 }

    val windGustKmh: Double?
        get() = windGustMph?.let { it * 1.60934 }

    val rain1hMm: Double?
        get() = rain1hHundredths?.let { it * 0.254 }

    val rain24hMm: Double?
        get() = rain24hHundredths?.let { it * 0.254 }

    val pressureHpa: Double?
        get() = pressureTenthsHpa?.let { it / 10.0 }
}

/**
 * APRS 101 Chapter 12: Weather Reports Decoder.
 *
 * APRS WX format uses standard single-character prefixes:
 * - c: Wind direction in degrees (000-360)
 * - s: Wind speed in mph
 * - g: Gust in mph
 * - t: Temperature in Fahrenheit (t068 = 68°F, t-05 = -5°F)
 * - r: Rain in last hour (0.01 inches)
 * - p: Rain in last 24h (0.01 inches)
 * - P: Rain since midnight (0.01 inches)
 * - h: Humidity (00 = 100%, 01-99 = %)
 * - b: Barometric pressure (tenths of hPa / mbar, e.g. b10132 = 1013.2 hPa)
 */
object AprsWeather {

    private val WX_DIR_REGEX = Regex("""c(\d{3})""")
    private val WX_SPEED_REGEX = Regex("""s(\d{3})""")
    private val WX_GUST_REGEX = Regex("""g(\d{3})""")
    private val WX_TEMP_REGEX = Regex("""t(-?\d{2,3})""")
    private val WX_RAIN_1H_REGEX = Regex("""r(\d{3})""")
    private val WX_RAIN_24H_REGEX = Regex("""p(\d{3})""")
    private val WX_RAIN_MIDNIGHT_REGEX = Regex("""P(\d{3})""")
    private val WX_HUMIDITY_REGEX = Regex("""h(\d{2})""")
    private val WX_PRESSURE_REGEX = Regex("""b(\d{5})""")
    private val WX_BLOCK_REGEX = Regex("""_?\d{8}c\d{3}s\d{3}(?:g\d{3})?(?:t-?\d{2,3})?(?:r\d{3})?(?:p\d{3})?(?:P\d{3})?(?:h\d{2})?(?:b\d{5})?|c\d{3}s\d{3}(?:g\d{3})?(?:t-?\d{2,3})?(?:r\d{3})?(?:p\d{3})?(?:P\d{3})?(?:h\d{2})?(?:b\d{5})?""")

    fun stripWeather(text: String): String {
        return text.replace(WX_BLOCK_REGEX, " ")
    }

    fun parse(text: String?): WeatherData? {
        if (text == null) return null
        if (!text.contains('t') && !text.contains('s') && !text.contains('c') && !text.contains('b')) {
            return null
        }

        val dir = WX_DIR_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val speed = WX_SPEED_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val gust = WX_GUST_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val temp = WX_TEMP_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val r1h = WX_RAIN_1H_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val r24h = WX_RAIN_24H_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val rMid = WX_RAIN_MIDNIGHT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val humidityRaw = WX_HUMIDITY_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val humidity = if (humidityRaw == 0) 100 else humidityRaw
        val pressure = WX_PRESSURE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

        // If at least one essential weather property is present
        if (dir == null && speed == null && temp == null && pressure == null && humidity == null) {
            return null
        }

        return WeatherData(
            windDirDeg = dir,
            windSpeedMph = speed,
            windGustMph = gust,
            tempF = temp,
            rain1hHundredths = r1h,
            rain24hHundredths = r24h,
            rainMidnightHundredths = rMid,
            humidityPercent = humidity,
            pressureTenthsHpa = pressure,
            rawReport = text,
        )
    }

    fun formatSummary(w: WeatherData): String {
        return buildList {
            w.tempC?.let { add(String.format(Locale.US, "%.1f°C", it)) }
            w.humidityPercent?.let { add("湿度 $it%") }
            w.pressureHpa?.let { add(String.format(Locale.US, "%.1f hPa", it)) }
            w.windSpeedKmh?.let { speed ->
                val dirStr = w.windDirDeg?.let { " ($it°)" } ?: ""
                add(String.format(Locale.US, "风速 %.0f km/h%s", speed, dirStr))
            }
            w.rain24hMm?.let { add(String.format(Locale.US, "24h降雨 %.1fmm", it)) }
        }.joinToString(" · ")
    }

    fun formatWeatherSummary(w: WeatherData): String = formatSummary(w)
}
