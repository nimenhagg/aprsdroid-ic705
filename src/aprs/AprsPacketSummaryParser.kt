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
    val phg: PhgData? = null,
    val dao: DaoOffset? = null,
    val weather: WeatherData? = null,
    val rngMiles: Double? = null,
    val telemetry: TelemetryData? = null,
    val thirdPartyGateways: List<ThirdPartyEnvelope> = emptyList(),
)

object AprsPacketSummaryParser {
    private val altitudeRegex = Regex("""(?:^|/)A=(\d{6})(?:$|[^0-9])""")

    fun parse(rawInput: String): ParsedAprsPacket {
        val raw = AprsCharsetDecoder.repairString(rawInput)

        // 1. Recursive third-party unwrapping
        val unwrapped = AprsThirdParty.unwrapRecursively(raw)
        val thirdPartyGateways = unwrapped.gateways
        val effectiveRaw = unwrapped.innermostRaw

        val colon = effectiveRaw.indexOf(':')
        val header = if (colon >= 0) effectiveRaw.substring(0, colon) else effectiveRaw
        val payload = if (colon >= 0 && colon + 1 <= effectiveRaw.length) effectiveRaw.substring(colon + 1) else ""
        val gt = header.indexOf('>')
        val source = header.takeIf { gt > 0 }?.substring(0, gt)?.trim()?.takeIf { it.isNotBlank() }
        val routing = if (gt >= 0 && gt + 1 < header.length) header.substring(gt + 1).split(',') else emptyList()
        val destination = routing.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val path = if (routing.size > 1) routing.drop(1).map { it.trim() }.filter { it.isNotBlank() } else emptyList()

        var kind = kindFromPayload(payload)
        var latitude: Double? = null
        var longitude: Double? = null
        var course: Int? = null
        var speed: Int? = null
        var commentCandidate: String? = payload.drop(1).trim().takeIf { it.isNotEmpty() && kind == AprsPacketKind.STATUS }
        var message: String? = null
        var phg: PhgData? = null

        // 2. Underlying parser
        runCatching { Parser.parse(effectiveRaw) }.getOrNull()?.aprsInformation?.let { info ->
            when (info) {
                is PositionPacket -> {
                    kind = AprsPacketKind.POSITION
                    latitude = info.position.latitude
                    longitude = info.position.longitude
                    val cse = info.extension as? CourseAndSpeedExtension
                    course = cse?.course
                    speed = cse?.speed
                    val phgExt = info.extension as? net.ab0oo.aprs.parser.PHGExtension
                    if (phgExt != null) {
                        phg = PhgData(
                            powerWatts = phgExt.power,
                            heightFeet = phgExt.height,
                            gainDb = phgExt.gain,
                            directivityDeg = phgExt.directivity,
                            rawCode = "PHG${AprsPhg.powerToCode(phgExt.power.toDouble())}${AprsPhg.heightToCode(phgExt.height.toDouble())}${phgExt.gain}${AprsPhg.directivityToCode(phgExt.directivity)}",
                        )
                    }
                    commentCandidate = AprsCharsetDecoder.repairNullable(info.comment?.trim())?.takeIf { it.isNotEmpty() }
                }
                is ObjectPacket -> {
                    kind = AprsPacketKind.OBJECT
                    latitude = info.position.latitude
                    longitude = info.position.longitude
                    val cse = info.extension as? CourseAndSpeedExtension
                    course = cse?.course
                    speed = cse?.speed
                    val phgExt = info.extension as? net.ab0oo.aprs.parser.PHGExtension
                    if (phgExt != null) {
                        phg = PhgData(
                            powerWatts = phgExt.power,
                            heightFeet = phgExt.height,
                            gainDb = phgExt.gain,
                            directivityDeg = phgExt.directivity,
                            rawCode = "PHG${AprsPhg.powerToCode(phgExt.power.toDouble())}${AprsPhg.heightToCode(phgExt.height.toDouble())}${phgExt.gain}${AprsPhg.directivityToCode(phgExt.directivity)}",
                        )
                    }
                    commentCandidate = AprsCharsetDecoder.repairNullable(info.comment?.trim())?.takeIf { it.isNotEmpty() }
                }
                is MessagePacket -> {
                    kind = AprsPacketKind.MESSAGE
                    message = AprsCharsetDecoder.repairNullable(info.messageBody?.trim())?.takeIf { it.isNotEmpty() }
                }
            }
        }

        // 3. Telemetry deep decoding
        var telemetry: TelemetryData? = null
        if (kind == AprsPacketKind.TELEMETRY || payload.startsWith("T#")) {
            val parsedTelemetry = AprsTelemetry.parse(payload)
            if (parsedTelemetry != null) {
                telemetry = parsedTelemetry
                kind = AprsPacketKind.TELEMETRY
                commentCandidate = parsedTelemetry.comment
            }
        }

        // 4. High-Precision DAO parsing
        var dao: DaoOffset? = null
        if (latitude != null && longitude != null && commentCandidate != null) {
            val parsedDao = AprsDao.parseDao(commentCandidate, isNorth = latitude >= 0, isEast = longitude >= 0)
            if (parsedDao != null) {
                dao = parsedDao
                latitude = latitude + parsedDao.deltaLatDegrees
                longitude = longitude + parsedDao.deltaLonDegrees
            }
        }

        // 5. Weather decoding
        val weather = AprsWeather.parse(commentCandidate ?: payload)
        if (weather != null && (kind == AprsPacketKind.UNKNOWN || kind == AprsPacketKind.POSITION)) {
            if (kind == AprsPacketKind.UNKNOWN) {
                kind = AprsPacketKind.WEATHER
            }
        }

        // 6. Altitude, Frequency, PHG, RNG
        val altitude = altitudeRegex.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val frequency = AprsPacket.parseQrg(commentCandidate ?: payload)
        val phgResult = phg ?: AprsPacket.parsePhg(commentCandidate ?: payload)
        val rngMiles = AprsPhg.parseRng(commentCandidate ?: payload)
            ?: phgResult?.let { AprsPhg.estimateRadioRangeMiles(it) }

        // 7. Clean comment/remarks: strip all protocol extensions (DAO, PHG, RNG, Alt, Freq, WX)
        val cleanComment = AprsCommentCleaner.clean(commentCandidate)

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
            comment = cleanComment,
            message = message,
            phg = phgResult,
            dao = dao,
            weather = weather,
            rngMiles = rngMiles,
            telemetry = telemetry,
            thirdPartyGateways = thirdPartyGateways,
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
