package org.aprsdroid.app

import java.util.Locale
import net.ab0oo.aprs.parser.MessagePacket

/**
 * Corrects the overly broad ACK/REJ classification in the bundled javAPRSlib
 * and exposes APRS 1.1 Reply-ACK metadata without leaking it into storage keys.
 */
internal object AprsMessageParser {
    private const val ADDRESS_FIELD_LENGTH = 9
    private const val BODY_OFFSET = 11
    private const val MAX_MESSAGE_NUMBER_LENGTH = 5

    data class Parsed(
        val packet: MessagePacket,
        /** Exact line number received on an ordinary message; used for normal ack echo. */
        val wireMessageNumber: String = packet.messageNumber,
        /** Piggyback ACK from an incoming {MM}AA message. Never set for ack/rej packets. */
        val replyAck: String? = null,
        /** True when the ordinary message used the APRS 1.1 MM}AA/MM} format. */
        val replyAckCapable: Boolean = false,
    )

    fun reparseIncoming(parsed: MessagePacket): Parsed {
        val raw = parsed.toString()
        if (
            raw.length < BODY_OFFSET ||
            raw.firstOrNull() != ':' ||
            raw.getOrNull(ADDRESS_FIELD_LENGTH + 1) != ':'
        ) {
            return Parsed(parsed)
        }

        val target = raw.substring(1, ADDRESS_FIELD_LENGTH + 1).trim().uppercase(Locale.US)
        val payload = raw.substring(BODY_OFFSET)

        parseAckOrRej(target, payload)?.let { return it }

        val messageNumberIndex = payload.lastIndexOf('{')
        if (messageNumberIndex < 0) {
            return Parsed(
                MessagePacket(target, payload, "").apply {
                    setAck(false)
                    setRej(false)
                },
            )
        }

        val body = payload.substring(0, messageNumberIndex)
        val wireMessageNumber = payload.substring(messageNumberIndex + 1)
        val replyAckSeparator = wireMessageNumber.indexOf('}')
        val messageNumber: String
        val replyAck: String?
        val replyAckCapable: Boolean

        if (replyAckSeparator >= 0) {
            messageNumber = wireMessageNumber.substring(0, replyAckSeparator)
            replyAck = wireMessageNumber.substring(replyAckSeparator + 1).takeIf { it.isNotEmpty() }
            replyAckCapable = true
        } else {
            messageNumber = wireMessageNumber
            replyAck = null
            replyAckCapable = false
        }

        return Parsed(
            packet = MessagePacket(target, body, messageNumber).apply {
                // Numbered ordinary messages whose text starts with "ack"/"rej"
                // are not control packets.
                setAck(false)
                setRej(false)
            },
            wireMessageNumber = wireMessageNumber,
            replyAck = replyAck,
            replyAckCapable = replyAckCapable,
        )
    }

    private fun parseAckOrRej(target: String, payload: String): Parsed? {
        if (payload.indexOf('{') >= 0 || payload.length < 4) return null

        val prefix = payload.substring(0, 3).lowercase(Locale.US)
        if (prefix != "ack" && prefix != "rej") return null

        val wireMessageNumber = payload.substring(3)
        if (
            wireMessageNumber.isEmpty() ||
            wireMessageNumber.length > MAX_MESSAGE_NUMBER_LENGTH
        ) {
            return null
        }

        // An ackMM}AA echoes the original line number. Only MM identifies the
        // local outbound message; AA is our own earlier free ACK echoed back.
        val messageNumber = wireMessageNumber.substringBefore('}')
        if (messageNumber.isEmpty()) return null

        return Parsed(
            packet = MessagePacket(target, prefix, messageNumber).apply {
                setAck(prefix == "ack")
                setRej(prefix == "rej")
            },
            wireMessageNumber = wireMessageNumber,
        )
    }
}
