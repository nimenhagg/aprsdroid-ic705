package org.aprsdroid.app.ic705.protocol

import java.nio.charset.StandardCharsets

data class Ic705ConnectionInfoAnnouncement(
    val header: Ic705AuthPacketHeader,
    val radioIdentityBlock: ByteArray,
    val radioName: String,
    val isBusy: Boolean,
    /** Client name owning the stream when [isBusy] is true. */
    val busyClientName: String?,
)

data class Ic705ConnectionParameters(
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val innerSequence: Int,
    val tokenRequest: Int,
    val token: Int,
    val radioIdentityBlock: ByteArray,
    val radioName: String,
    val username: String,
    val localCivPort: Int,
    val localAudioPort: Int,
    val receiveEnabled: Boolean = true,
    val transmitEnabled: Boolean = false,
    val receiveSampleRateHz: Int = Ic705AudioPacketCodec.SAMPLE_RATE_HZ,
    val transmitSampleRateHz: Int = Ic705AudioPacketCodec.SAMPLE_RATE_HZ,
    val transmitBufferSamples: Int = Ic705ConnectionInfoCodec.DEFAULT_TX_BUFFER_SAMPLES,
) {
    init {
        requireUInt16("sequence", sequence)
        requireUInt16("innerSequence", innerSequence)
        requireUInt16("tokenRequest", tokenRequest)
        require(radioIdentityBlock.size == Ic705ConnectionInfoCodec.IDENTITY_BLOCK_SIZE) {
            "radioIdentityBlock must be ${Ic705ConnectionInfoCodec.IDENTITY_BLOCK_SIZE} bytes"
        }
        require(localCivPort in 1..0xffff) { "localCivPort must be a valid UDP port" }
        require(localAudioPort in 1..0xffff) { "localAudioPort must be a valid UDP port" }
        require(receiveSampleRateHz > 0) { "receiveSampleRateHz must be positive" }
        require(transmitSampleRateHz > 0) { "transmitSampleRateHz must be positive" }
        require(transmitBufferSamples > 0) { "transmitBufferSamples must be positive" }
    }
}

/** Codec for the 0x90 connection-information negotiation packet. */
object Ic705ConnectionInfoCodec {
    const val PACKET_SIZE = 0x90
    const val IDENTITY_BLOCK_SIZE = 0x20
    const val RADIO_NAME_SIZE = 0x20
    const val CODEC_LPCM_MONO_16_BIT = 0x04
    /** Buffer value observed in a successful RS-BA1/IC-705 48 kHz LAN session. */
    const val DEFAULT_TX_BUFFER_SAMPLES = 0xf0

    private const val REQUEST_TYPE_CONNECTION = 0x03
    private const val CONVERT_AUDIO = 0x01

    /** Identity placeholder used by FT8CN for the first client-originated 0x90 request. */
    fun initialClientIdentityBlock(): ByteArray = ByteArray(IDENTITY_BLOCK_SIZE).apply {
        this[0x06] = 0x10
        this[0x07] = 0x80.toByte()
    }

    /**
     * Encodes the client half of the 0x90 exchange.
     *
     * [Ic705ConnectionParameters.transmitEnabled] defaults to false, so merely
     * constructing an RX-only session does not advertise an audio transmit path.
     */
    fun encodeParameters(parameters: Ic705ConnectionParameters): ByteArray {
        val result = ByteArray(PACKET_SIZE)
        writeAuthPacketHeader(
            destination = result,
            packetSize = PACKET_SIZE,
            header = Ic705AuthPacketHeader(
                type = 0,
                sequence = parameters.sequence,
                senderId = parameters.senderId,
                receiverId = parameters.receiverId,
                requestReply = Ic705HandshakeCodec.REQUEST_REPLY_REQUEST,
                requestType = REQUEST_TYPE_CONNECTION,
                innerSequence = parameters.innerSequence,
                tokenRequest = parameters.tokenRequest,
                token = parameters.token,
            ),
        )
        parameters.radioIdentityBlock.copyInto(result, destinationOffset = 0x20)
        encodeFixedAscii(parameters.radioName, RADIO_NAME_SIZE, "radioName")
            .copyInto(result, destinationOffset = 0x40)
        Ic705HandshakeCodec.encodeCredentialPassCode(parameters.username)
            .copyInto(result, destinationOffset = 0x60)
        result[0x70] = if (parameters.receiveEnabled) 1 else 0
        result[0x71] = if (parameters.transmitEnabled) 1 else 0
        result[0x72] = CODEC_LPCM_MONO_16_BIT.toByte()
        result[0x73] = CODEC_LPCM_MONO_16_BIT.toByte()
        Ic705WireByteOrder.writeInt32Be(result, 0x74, parameters.receiveSampleRateHz)
        Ic705WireByteOrder.writeInt32Be(result, 0x78, parameters.transmitSampleRateHz)
        Ic705WireByteOrder.writeInt32Be(result, 0x7c, parameters.localCivPort)
        Ic705WireByteOrder.writeInt32Be(result, 0x80, parameters.localAudioPort)
        Ic705WireByteOrder.writeInt32Be(result, 0x84, parameters.transmitBufferSamples)
        result[0x88] = CONVERT_AUDIO.toByte()
        return result
    }

    /** Parses the radio half of the 0x90 exchange without interpreting unused union fields. */
    fun decodeAnnouncement(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): Ic705ConnectionInfoAnnouncement {
        val header = decodeAuthPacketHeader(
            data = data,
            expectedPacketSize = PACKET_SIZE,
            expectedReceiverId = expectedReceiverId,
        )
        val isBusy = data[0x60] != 0.toByte()
        return Ic705ConnectionInfoAnnouncement(
            header = header,
            radioIdentityBlock = data.copyOfRange(0x20, 0x20 + IDENTITY_BLOCK_SIZE),
            radioName = decodeFixedAscii(data, 0x40, RADIO_NAME_SIZE),
            // FT8CN observes the busy discriminator in the first byte of this union.
            isBusy = isBusy,
            // wfview compares this field with the name sent in the login packet to
            // distinguish our newly-claimed stream from a radio owned by another client.
            busyClientName = if (isBusy) decodeFixedAscii(data, 0x64, RADIO_NAME_SIZE) else null,
        )
    }

    private fun encodeFixedAscii(value: String, size: Int, fieldName: String): ByteArray {
        require(value.all { it.code <= 0x7f }) { "$fieldName must contain US-ASCII only" }
        val encoded = value.toByteArray(StandardCharsets.US_ASCII)
        require(encoded.size <= size) { "$fieldName must be at most $size bytes" }
        return ByteArray(size).also { encoded.copyInto(it) }
    }

    private fun decodeFixedAscii(data: ByteArray, offset: Int, size: Int): String {
        val firstNull = (offset until offset + size).firstOrNull { data[it] == 0.toByte() }
        val end = firstNull ?: offset + size
        return String(data, offset, end - offset, StandardCharsets.US_ASCII).trimEnd(' ')
    }
}
