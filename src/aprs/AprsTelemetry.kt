package org.aprsdroid.app.aprs

import java.util.Locale

/**
 * APRS 101 Chapter 13: Telemetry Data & Definitions.
 *
 * Telemetry report format:
 * `T#sss,aaa,bbb,ccc,ddd,eee,bbbbbbbb[ comment]`
 * - sss: Sequence number (000-999)
 * - aaa, bbb, ccc, ddd, eee: 5 analog channels (0-255 or 0-999)
 * - bbbbbbbb: 8 digital binary bits (each 0 or 1, representing D1..D8)
 * - comment: optional remarks following telemetry
 */
data class TelemetryData(
    val sequenceNumber: Int,
    val analogChannels: List<Int>, // 5 channels
    val digitalBits: String, // 8-char string of '0'/'1'
    val digitalFlags: List<Boolean>, // 8 booleans
    val comment: String? = null,
    val rawPayload: String,
) {
    fun formatSummary(): String {
        val analogStr = analogChannels.mapIndexed { i, v -> "A${i + 1}:$v" }.joinToString(", ")
        return "#$sequenceNumber · $analogStr · D:$digitalBits"
    }

    fun formatSummaryZh(): String {
        val analogStr = analogChannels.mapIndexed { i, v -> "A${i + 1}:$v" }.joinToString(", ")
        return "序号 #$sequenceNumber · $analogStr · 数字通道:$digitalBits"
    }
}

data class TelemetryCoefficients(
    val a: Double,
    val b: Double,
    val c: Double,
) {
    fun apply(rawValue: Int): Double = a * (rawValue * rawValue) + b * rawValue + c
}

data class TelemetryDefinitions(
    val station: String,
    val paramNames: List<String> = emptyList(), // up to 5 analog + 8 digital
    val unitNames: List<String> = emptyList(), // up to 5 analog + 8 digital
    val equations: List<TelemetryCoefficients> = emptyList(), // 5 channels
    val bitSense: String? = null,
    val projectTitle: String? = null,
)

object AprsTelemetry {
    // Standard APRS T# regex matching sequence, 5 analog values, digital bits, and optional comment
    private val TELEMETRY_REGEX = Regex(
        """^T#\s*(\d{1,5})\s*,\s*(\d{1,5})\s*,\s*(\d{1,5})\s*,\s*(\d{1,5})\s*,\s*(\d{1,5})\s*,\s*(\d{1,5})\s*,\s*([01]{1,8})(?:[,\s]+(.*))?$"""
    )

    fun isTelemetry(payload: String): Boolean = payload.startsWith("T#")

    /**
     * Parse raw APRS telemetry payload (e.g. "T#042,128,095,012,230,005,10100001 Balloon").
     */
    fun parse(payload: String): TelemetryData? {
        val match = TELEMETRY_REGEX.find(payload.trim()) ?: return null
        val seq = match.groupValues[1].toIntOrNull() ?: 0
        val a1 = match.groupValues[2].toIntOrNull() ?: 0
        val a2 = match.groupValues[3].toIntOrNull() ?: 0
        val a3 = match.groupValues[4].toIntOrNull() ?: 0
        val a4 = match.groupValues[5].toIntOrNull() ?: 0
        val a5 = match.groupValues[6].toIntOrNull() ?: 0

        val rawDigital = match.groupValues[7]
        val digitalBits = rawDigital.padEnd(8, '0').take(8)
        val digitalFlags = digitalBits.map { it == '1' }

        val comment = match.groupValues.getOrNull(8)?.trim()?.takeIf { it.isNotEmpty() }

        return TelemetryData(
            sequenceNumber = seq,
            analogChannels = listOf(a1, a2, a3, a4, a5),
            digitalBits = digitalBits,
            digitalFlags = digitalFlags,
            comment = comment,
            rawPayload = payload,
        )
    }

    /**
     * Parse APRS Telemetry Definition messages:
     * - PARM.A1,A2,A3,A4,A5,D1,D2,D3,D4,D5,D6,D7,D8
     * - UNIT.U1,U2,U3,U4,U5,U1,U2,U3,U4,U5,U6,U7,U8
     * - EQNS.a,b,c,d,e,f,g,h,i,j,k,l,m,n,o
     * - BITS.11111111,Title
     */
    fun parseParameterNames(body: String): List<String>? {
        if (!body.startsWith("PARM.")) return null
        return body.substring(5).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun parseUnitNames(body: String): List<String>? {
        if (!body.startsWith("UNIT.")) return null
        return body.substring(5).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun parseEquations(body: String): List<TelemetryCoefficients>? {
        if (!body.startsWith("EQNS.")) return null
        val parts = body.substring(5).split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size < 15) return null
        return (0 until 5).map { i ->
            TelemetryCoefficients(parts[i * 3], parts[i * 3 + 1], parts[i * 3 + 2])
        }
    }

    fun parseBitsAndTitle(body: String): Pair<String, String>? {
        if (!body.startsWith("BITS.")) return null
        val content = body.substring(5)
        val comma = content.indexOf(',')
        return if (comma >= 0) {
            Pair(content.substring(0, comma).trim(), content.substring(comma + 1).trim())
        } else {
            Pair(content.trim(), "")
        }
    }
}
