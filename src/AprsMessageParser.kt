package org.aprsdroid.app

import net.ab0oo.aprs.parser.MessagePacket
import java.util.Locale

/**
 * Corrects the overly broad ACK/REJ classification in the bundled javAPRSlib.
 *
 * Incoming MessagePacket.toString() returns the original information-field bytes,
 * so reparsing here preserves text that javAPRSlib may already have collapsed to
 * "ack" or "rej".
 */
internal object AprsMessageParser {
    private const val ADDRESS_FIELD_LENGTH = 9
    private const val BODY_OFFSET = 11
    private const val MAX_ACK_MESSAGE_NUMBER_LENGTH = 5

    fun reparseIncoming(parsed: MessagePacket): MessagePacket {
        val raw = parsed.toString()
        if (
            raw.length < BODY_OFFSET ||
            raw.firstOrNull() != ':' ||
            raw.getOrNull(ADDRESS_FIELD_LENGTH + 1) != ':'
        ) {
            return parsed
        }

        val target = raw.substring(1, ADDRESS_FIELD_LENGTH + 1).trim().uppercase(Locale.US)
        val payload = raw.substring(BODY_OFFSET)

        parseAckOrRej(target, payload)?.let { return it }

        val messageNumberIndex = payload.lastIndexOf('{')
        val body: String
        val messageNumber: String
        if (messageNumberIndex >= 0) {
            body = payload.substring(0, messageNumberIndex)
            messageNumber = payload.substring(messageNumberIndex + 1)
        } else {
            body = payload
            messageNumber = ""
        }

        return MessagePacket(target, body, messageNumber).apply {
            // A numbered ordinary message whose body literally starts with/equals
            // "ack" or "rej" is not an acknowledgement/rejection. Those control
            // formats never contain the '{' message-number separator.
            setAck(false)
            setRej(false)
        }
    }

    private fun parseAckOrRej(target: String, payload: String): MessagePacket? {
        if (payload.indexOf('{') >= 0 || payload.length < 4) return null

        val prefix = payload.substring(0, 3).lowercase(Locale.US)
        if (prefix != "ack" && prefix != "rej") return null

        val messageNumber = payload.substring(3)
        if (messageNumber.isEmpty() || messageNumber.length > MAX_ACK_MESSAGE_NUMBER_LENGTH) {
            return null
        }

        return MessagePacket(target, prefix, messageNumber).apply {
            setAck(prefix == "ack")
            setRej(prefix == "rej")
        }
    }
}
