package org.aprsdroid.app.aprs

import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.ObjectPacket
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.AprsPacket

enum class AprsPacketKind {
    POSITION,
    MESSAGE,
    STATUS,
    OBJECT,
    ITEM,
    WEATHER,
    TELEMETRY,
    MICE,
    THIRD_PARTY,
    UNKNOWN,
}

data class ParsedAprsPacket(
    val raw: String,
    val source: String?,
    val destination: String?,
    val path: List<String>,
    val payload: String,
    val kind: AprsPacketKind,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val course: Int? = null,
    val speedKnots: Int? = null,
    val altitudeFeet: Int? = null,
    val frequency: String? = null,
    val comment: String? = null,
    val message: String? = null,
)

object AprsPacketSummaryParser {
    private val altitudeRegex = Regex("""(?:^|/)A=(\d{6})(?:$|[^0-9])""")

    fun parse(raw: String): ParsedAprsPacket {
        val colon = raw.indexOf(':')
        val header = if (colon >= 0) raw.substring(0, colon) else raw
        val payload = if (colon >= 0 && colon + 1 <= raw.length) raw.substring(colon + 1) else ""
        val gt = header.indexOf('>')
        val source = header.takeIf { gt > 0 }?.substring(0, gt)?.takeIf { it.isNotBlank() }
        val routing = if (gt >= 0 && gt + 1 < header.length) header.substring(gt + 1).split(',') else emptyList()
        val destination = routing.firstOrNull()?.takeIf { it.isNotBlank() }
        val path = if (routing.size > 1) routing.drop(1).filter { it.isNotBlank() } else emptyList()

        var kind = kindFromPayload(payload)
        var latitude: Double? = null
        var longitude: Double? = null
        var course: Int? = null
        var speed: Int? = null
        var comment: String? = payload.drop(1).trim().takeIf { it.isNotEmpty() && kind == AprsPacketKind.STATUS }
        var message: String? = null

        runCatching { Parser.parse(raw) }.getOrNull()?.aprsInformation?.let { info ->
            when (info) {
                is PositionPacket -> {
                    kind = AprsPacketKind.POSITION
                    latitude = info.position.latitude
                    longitude = info.position.longitude
                    val cse = info.extension as? CourseAndSpeedExtension
                    course = cse?.course
                    speed = cse?.speed
                    comment = info.comment?.trim()?.takeIf { it.isNotEmpty() }
                }
                is ObjectPacket -> {
                    kind = AprsPacketKind.OBJECT
                    latitude = info.position.latitude
                    longitude = info.position.longitude
                    val cse = info.extension as? CourseAndSpeedExtension
                    course = cse?.course
                    speed = cse?.speed
                    comment = info.comment?.trim()?.takeIf { it.isNotEmpty() }
                }
                is MessagePacket -> {
                    kind = AprsPacketKind.MESSAGE
                    message = info.messageBody?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }

        val altitude = altitudeRegex.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val frequency = AprsPacket.parseQrg(comment ?: payload)

        return ParsedAprsPacket(
            raw = raw,
            source = source,
            destination = destination,
            path = path,
            payload = payload,
            kind = kind,
            latitude = latitude,
            longitude = longitude,
            course = course,
            speedKnots = speed,
            altitudeFeet = altitude,
            frequency = frequency,
            comment = comment,
            message = message,
        )
    }

    private fun kindFromPayload(payload: String): AprsPacketKind = when {
        payload.startsWith("T#") -> AprsPacketKind.TELEMETRY
        payload.isEmpty() -> AprsPacketKind.UNKNOWN
        payload[0] == '!' || payload[0] == '=' || payload[0] == '/' || payload[0] == '@' -> AprsPacketKind.POSITION
        payload[0] == ':' -> AprsPacketKind.MESSAGE
        payload[0] == '>' -> AprsPacketKind.STATUS
        payload[0] == ';' -> AprsPacketKind.OBJECT
        payload[0] == ')' -> AprsPacketKind.ITEM
        payload[0] == '_' -> AprsPacketKind.WEATHER
        payload[0] == '\'' || payload[0] == '`' -> AprsPacketKind.MICE
        payload[0] == '}' -> AprsPacketKind.THIRD_PARTY
        else -> AprsPacketKind.UNKNOWN
    }
}
