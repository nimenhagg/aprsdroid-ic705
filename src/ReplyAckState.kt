package org.aprsdroid.app

/**
 * Per-correspondent APRS 1.1 Reply-ACK state.
 *
 * The database keeps only the local message number (MM). The latest incoming MM
 * from a Reply-ACK-capable peer is appended as AA only when a packet is actually
 * transmitted, so retries automatically carry the newest acknowledgement owed.
 */
internal class ReplyAckState {
    private val latestAckOwed = mutableMapOf<String, String>()

    @Synchronized
    fun rememberIncoming(call: String, messageNumber: String) {
        if (messageNumber.isEmpty()) return
        latestAckOwed[AprsPacket.normalizeMessageCallsign(call)] = messageNumber
    }

    @Synchronized
    fun decorateOutgoing(call: String, messageNumber: String): String {
        val ack = latestAckOwed[AprsPacket.normalizeMessageCallsign(call)].orEmpty()
        return "$messageNumber}$ack"
    }
}
