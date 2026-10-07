package org.aprsdroid.app.aprs

/**
 * APRS 101 Chapter 14: Third-Party Message Format ('}').
 *
 * Third-party packets encapsulate packets relayed across networks (e.g. APRS-IS to RF igates)
 * in the form: `GATEWAY>APRS,TCPIP*:}ORIGIN>DEST,PATH:PAYLOAD`
 *
 * This utility recursively unwraps nested third-party envelopes, extracting all gateway hops
 * while recovering the true originator and original payload for deep packet decoding.
 */
data class ThirdPartyEnvelope(
    val gatewaySource: String,
    val gatewayDestination: String,
    val gatewayPath: List<String>,
    val rawHeader: String,
)

data class UnwrappedAprsPacket(
    val innermostRaw: String,
    val innermostSource: String,
    val innermostDestination: String,
    val innermostPath: List<String>,
    val innermostPayload: String,
    val gateways: List<ThirdPartyEnvelope>,
)

object AprsThirdParty {

    fun isThirdPartyPayload(payload: String): Boolean = payload.startsWith("}")

    /**
     * Recursively unwrap third-party packets until reaching the innermost APRS packet
     * or reaching [maxHops].
     */
    fun unwrapRecursively(rawPacket: String, maxHops: Int = 10): UnwrappedAprsPacket {
        var currentRaw = rawPacket.trim()
        val gateways = mutableListOf<ThirdPartyEnvelope>()

        var currentSource = ""
        var currentDest = ""
        var currentPath = emptyList<String>()
        var currentPayload = ""

        var hops = 0
        while (hops < maxHops) {
            val colon = currentRaw.indexOf(':')
            if (colon <= 0) break
            val header = currentRaw.substring(0, colon).trim()
            val payload = currentRaw.substring(colon + 1)

            val gt = header.indexOf('>')
            if (gt <= 0) break
            val src = header.substring(0, gt).trim()
            val routing = header.substring(gt + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val dst = routing.firstOrNull() ?: ""
            val path = if (routing.size > 1) routing.drop(1) else emptyList()

            currentSource = src
            currentDest = dst
            currentPath = path
            currentPayload = payload

            if (payload.startsWith("}")) {
                gateways.add(
                    ThirdPartyEnvelope(
                        gatewaySource = src,
                        gatewayDestination = dst,
                        gatewayPath = path,
                        rawHeader = header,
                    )
                )
                // Inner raw packet follows immediately after '}'
                val innerRaw = payload.substring(1).trim()
                if (innerRaw.contains(':') && innerRaw.contains('>')) {
                    currentRaw = innerRaw
                    hops++
                } else {
                    // Not a valid standard AX.25 TNC-2 header inside
                    break
                }
            } else {
                break
            }
        }

        return UnwrappedAprsPacket(
            innermostRaw = currentRaw,
            innermostSource = currentSource,
            innermostDestination = currentDest,
            innermostPath = currentPath,
            innermostPayload = currentPayload,
            gateways = gateways,
        )
    }
}
