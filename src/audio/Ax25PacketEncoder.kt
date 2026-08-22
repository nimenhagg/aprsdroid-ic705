package org.aprsdroid.app.audio

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import net.ab0oo.aprs.parser.APRSPacket
import sivantoledo.ax25.Packet

/**
 * Encodes APRS packets into AX.25 [Packet] instances suitable for AFSK1200 modulation.
 * Note: in AX.25 wire format and [Packet] constructor, Destination comes first, then Source.
 */
object Ax25PacketEncoder {
    private val DEFAULT_CHARSET: Charset = StandardCharsets.ISO_8859_1

    /**
     * Converts an [APRSPacket] into an AX.25 [Packet].
     */
    fun encode(packet: APRSPacket, charset: Charset = DEFAULT_CHARSET): Packet {
        val source = packet.sourceCall ?: "N0CALL"
        val destination = packet.destinationCall ?: "APRS"
        val digis = packet.digipeaters?.map { it.toString() }?.toTypedArray() ?: emptyArray()
        val info = packet.aprsInformation?.toString() ?: ""
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
}
