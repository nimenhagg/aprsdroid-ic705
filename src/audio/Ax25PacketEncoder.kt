package org.aprsdroid.app.audio

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import net.ab0oo.aprs.parser.APRSPacket
import sivantoledo.ax25.Packet

/**
 * Encodes APRS packets into AX.25 [Packet] instances suitable for AFSK1200 modulation.
 * Note: in AX.25 wire format and [Packet] constructor, Destination comes first, then Source.
 */
class Ax25PayloadEncodingException(charset: Charset) :
    IllegalArgumentException("AX.25 payload cannot be encoded losslessly as ${charset.name()}")

object Ax25PacketEncoder {
    private val DEFAULT_CHARSET: Charset = StandardCharsets.ISO_8859_1

    /**
     * Converts an [APRSPacket] into an AX.25 [Packet].
     *
     * Every address is validated against [Ax25Limits.MAX_CALLSIGN_CHARS] first.
     * Without this, an over-long callsign would either throw from inside the
     * vendor library or — worse — be silently truncated there, putting a frame
     * on the air addressed to a station that does not exist.
     */
    fun encode(packet: APRSPacket, charset: Charset = DEFAULT_CHARSET): Packet {
        val source = packet.sourceCall ?: "N0CALL"
        val destination = packet.destinationCall ?: "APRS"
        val digis = packet.digipeaters?.map { it.toString() }?.toTypedArray() ?: emptyArray()
        validateAddresses(source, destination, digis)
        val info = packet.aprsInformation?.toString() ?: ""
        if (!charset.newEncoder().canEncode(info)) {
            throw Ax25PayloadEncodingException(charset)
        }
        val payload = info.toByteArray(charset)

        val ax25 = Packet(
            destination,
            source,
            digis,
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            payload,
        )
        ax25.parse()
        return ax25
    }

    /**
     * Constructs a direct AX.25 [Packet] from callsigns and raw payload.
     */
    fun create(
        source: String,
        destination: String,
        digis: Array<String> = emptyArray(),
        payload: ByteArray,
    ): Packet {
        validateAddresses(source, destination, digis)
        val ax25 = Packet(
            destination,
            source,
            digis,
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            payload,
        )
        ax25.parse()
        return ax25
    }

    /**
     * Rejects any address that cannot be represented in an AX.25 address field.
     *
     * The SSID suffix is not part of the 6-character limit, so `BG7XXX-5` is
     * valid while `BG7XXXX-5` is not. Silent truncation is never acceptable
     * here: the operator must learn that the configured callsign is unusable.
     */
    private fun validateAddresses(source: String, destination: String, digis: Array<String>) {
        sequenceOf(source, destination)
            .plus(digis.asSequence())
            .forEach { address ->
                val base = address.substringBefore('-')
                if (!Ax25Limits.fitsCallsign(base)) throw Ax25CallsignException(address)
            }
    }
}
