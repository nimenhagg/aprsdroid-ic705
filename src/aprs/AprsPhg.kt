package org.aprsdroid.app.aprs

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class PhgData(
    val powerWatts: Int,
    val heightFeet: Int,
    val gainDb: Int,
    val directivityDeg: Int,
    val rawCode: String,
) {
    val heightMeters: Int
        get() = (heightFeet * 0.3048).roundToInt()
}

/**
 * APRS 101 Chapter 9: Data Extensions - PHG (Power, Effective Antenna Height/Gain/Directivity)
 *
 * PHG format: "PHGphgd" (7 characters):
 * - p: Power code (0-9): P = p^2 Watts (0, 1, 4, 9, 16, 25, 36, 49, 64, 81 W)
 * - h: Height code (0-9): H = 10 * 2^h feet HAAT (10, 20, 40, 80, 160, 320, 640, 1280, 2560, 5120 ft)
 * - g: Gain code (0-9): 0-9 dB (dBi)
 * - d: Directivity code (0-8): 0=omni, 1=45° (NE) .. 8=360° (N)
 *
 * Rule: Power and Height MUST take the largest step NOT exceeding the actual value.
 */
object AprsPhg {
    val POWER_STEPS = intArrayOf(0, 1, 4, 9, 16, 25, 36, 49, 64, 81)
    val HEIGHT_STEPS_FEET = intArrayOf(10, 20, 40, 80, 160, 320, 640, 1280, 2560, 5120)
    val DIRECTIVITY_ANGLES = intArrayOf(0, 45, 90, 135, 180, 225, 270, 315, 360)

    val PHG_REGEX = Regex("""PHG([0-9])([0-9])([0-9])([0-9])(?!\d)""")

    fun m2ft(meters: Double): Double = meters * 3.2808399
    fun ft2m(feet: Double): Double = feet / 3.2808399

    /**
     * Quantize power in Watts to code digit 0..9.
     * Takes the largest step not exceeding actual power.
     */
    fun powerToCode(watts: Double): Int {
        if (!watts.isFinite() || watts <= 0.0) return 0
        var best = 0
        for (i in POWER_STEPS.indices) {
            if (POWER_STEPS[i] <= watts) {
                best = i
            }
        }
        return best
    }

    /**
     * Quantize antenna height (HAAT) in feet to code digit 0..9.
     * Takes the largest step not exceeding actual height.
     */
    fun heightToCode(feet: Double): Int {
        if (!feet.isFinite() || feet <= 0.0) return 0
        var best = 0
        for (i in HEIGHT_STEPS_FEET.indices) {
            if (HEIGHT_STEPS_FEET[i] <= feet) {
                best = i
            }
        }
        return best
    }

    /**
     * Quantize antenna gain in dB to code digit 0..9.
     */
    fun gainToCode(db: Double): Int {
        if (!db.isFinite() || db <= 0.0) return 0
        return db.roundToInt().coerceIn(0, 9)
    }

    /**
     * Quantize directivity angle (degrees) to code digit 0..8.
     * 0 = omni-directional.
     */
    fun directivityToCode(deg: Int, isOmni: Boolean = (deg == 0)): Int {
        if (isOmni || deg <= 0) return 0
        var best = 1
        var bestDiff = Int.MAX_VALUE
        for (i in 1..8) {
            val angle = DIRECTIVITY_ANGLES[i]
            val diff = abs(angle - deg)
            if (diff < bestDiff) {
                bestDiff = diff
                best = i
            }
        }
        return best
    }

    /**
     * Assemble 7-character PHG data extension: "PHGphgd".
     * Returns null if none of power, height, or gain are specified.
     */
    fun encode(
        powerWatts: Double?,
        heightFeet: Double?,
        gainDb: Double?,
        directivityDeg: Int = 0,
        isOmni: Boolean = (directivityDeg == 0),
    ): String? {
        if (powerWatts == null && heightFeet == null && gainDb == null) return null
        val p = powerToCode(powerWatts ?: 0.0)
        val h = heightToCode(heightFeet ?: 0.0)
        val g = gainToCode(gainDb ?: 0.0)
        val d = directivityToCode(directivityDeg, isOmni)
        return "PHG$p$h$g$d"
    }

    /**
     * Decode a 7-character string "PHGphgd" to PhgData.
     */
    fun decode(phg: String): PhgData? {
        val trimmed = phg.trim()
        if (trimmed.length != 7 || !trimmed.startsWith("PHG")) return null
        val pCode = trimmed[3].digitToIntOrNull() ?: return null
        val hCode = trimmed[4].digitToIntOrNull() ?: return null
        val gCode = trimmed[5].digitToIntOrNull() ?: return null
        val dCode = trimmed[6].digitToIntOrNull() ?: return null
        if (dCode > 8) return null

        val power = POWER_STEPS[pCode]
        val height = HEIGHT_STEPS_FEET[hCode]
        val gain = gCode
        val directivity = DIRECTIVITY_ANGLES[dCode]

        return PhgData(
            powerWatts = power,
            heightFeet = height,
            gainDb = gain,
            directivityDeg = directivity,
            rawCode = trimmed,
        )
    }

    fun formatDescription(phg: PhgData): String {
        val dir = if (phg.directivityDeg == 0) "Omni" else "${phg.directivityDeg}°"
        return "${phg.powerWatts}W · ${phg.heightFeet}ft (${phg.heightMeters}m) · ${phg.gainDb}dBi · $dir"
    }

    fun formatDescriptionZh(phg: PhgData): String {
        val dir = if (phg.directivityDeg == 0) "全向" else "${phg.directivityDeg}°"
        return "${phg.powerWatts}W · ${phg.heightFeet}ft (${phg.heightMeters}m) · ${phg.gainDb}dBi · $dir"
    }
}
