package org.aprsdroid.app.ic705.protocol

data class Ic705AudioHeader(
    val type: Int = 0,
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val identity: Int,
    val audioSequence: Int,
    val unused: Int = 0,
    val payloadLength: Int,
) {
    init {
        requireUInt16("type", type)
        requireUInt16("sequence", sequence)
        requireUInt16("identity", identity)
        requireUInt16("audioSequence", audioSequence)
        requireUInt16("unused", unused)
        requireUInt16("payloadLength", payloadLength)
    }

    val packetLength: Int
        get() = Ic705AudioPacketCodec.HEADER_SIZE + payloadLength
}

data class Ic705AudioPacket(
    val header: Ic705AudioHeader,
    val pcmPayload: ByteArray,
)

/** Codec for the 0x18-byte Icom LAN audio header plus its PCM payload. */
object Ic705AudioPacketCodec {
    const val HEADER_SIZE = 0x18
    /** LPCM rate requested by a successful RS-BA1/IC-705 LAN session capture. */
    const val SAMPLE_RATE_HZ = 12_000
    const val SAMPLES_PER_PACKET = 0xf0
    const val PCM_BYTES_PER_SAMPLE = 2
    const val PCM_BYTES_PER_PACKET = SAMPLES_PER_PACKET * PCM_BYTES_PER_SAMPLE

    const val IDENTITY_FOR_160_BYTE_PAYLOAD = 0x8197
    const val IDENTITY_FOR_OTHER_PAYLOADS = 0x8000

    fun transmitIdentity(payloadLength: Int): Int =
        if (payloadLength == 0xa0) IDENTITY_FOR_160_BYTE_PAYLOAD else IDENTITY_FOR_OTHER_PAYLOADS

    fun encode(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        audioSequence: Int,
        pcmPayload: ByteArray,
        type: Int = 0,
        identity: Int = transmitIdentity(pcmPayload.size),
        unused: Int = 0,
    ): ByteArray {
        val header = Ic705AudioHeader(
            type = type,
            sequence = sequence,
            senderId = senderId,
            receiverId = receiverId,
            identity = identity,
            audioSequence = audioSequence,
            unused = unused,
            payloadLength = pcmPayload.size,
        )
        return encode(header, pcmPayload)
    }

    fun encode(header: Ic705AudioHeader, pcmPayload: ByteArray): ByteArray {
        require(pcmPayload.size == header.payloadLength) {
            "PCM payload size ${pcmPayload.size} does not match header length ${header.payloadLength}"
        }
        val result = ByteArray(header.packetLength)
        val encodedHeader = encodeHeader(header)
        encodedHeader.copyInto(result, destinationOffset = 0)
        pcmPayload.copyInto(result, destinationOffset = HEADER_SIZE)
        return result
    }

    fun encodeHeader(header: Ic705AudioHeader): ByteArray {
        val result = ByteArray(HEADER_SIZE)
        Ic705WireByteOrder.writeInt32Le(result, 0x00, header.packetLength)
        Ic705WireByteOrder.writeUInt16Le(result, 0x04, header.type)
        Ic705WireByteOrder.writeUInt16Le(result, 0x06, header.sequence)
        Ic705WireByteOrder.writeInt32Le(result, 0x08, header.senderId)
        Ic705WireByteOrder.writeInt32Le(result, 0x0c, header.receiverId)
        Ic705WireByteOrder.writeUInt16Be(result, 0x10, header.identity)
        Ic705WireByteOrder.writeUInt16Be(result, 0x12, header.audioSequence)
        Ic705WireByteOrder.writeUInt16Be(result, 0x14, header.unused)
        Ic705WireByteOrder.writeUInt16Be(result, 0x16, header.payloadLength)
        return result
    }

    /** Decodes a header while validating it against the complete datagram length. */
    fun decodeHeader(data: ByteArray, expectedReceiverId: Int? = null): Ic705AudioHeader {
        protocolRequire(data.size >= HEADER_SIZE) {
            "Audio datagram must contain a $HEADER_SIZE-byte header, got ${data.size} bytes"
        }
        val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
        protocolRequire(declaredLength == data.size) {
            "Audio datagram declares length $declaredLength, actual length is ${data.size}"
        }
        val payloadLength = Ic705WireByteOrder.readUInt16Be(data, 0x16)
        protocolRequire(payloadLength == data.size - HEADER_SIZE) {
            "Audio payload declares $payloadLength bytes, actual payload is ${data.size - HEADER_SIZE}"
        }

        val header = Ic705AudioHeader(
            type = Ic705WireByteOrder.readUInt16Le(data, 0x04),
            sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
            senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
            receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
            identity = Ic705WireByteOrder.readUInt16Be(data, 0x10),
            audioSequence = Ic705WireByteOrder.readUInt16Be(data, 0x12),
            unused = Ic705WireByteOrder.readUInt16Be(data, 0x14),
            payloadLength = payloadLength,
        )
        validateReceiverId(header.receiverId, expectedReceiverId)
        return header
    }

    fun decode(data: ByteArray, expectedReceiverId: Int? = null): Ic705AudioPacket {
        val header = decodeHeader(data, expectedReceiverId)
        return Ic705AudioPacket(
            header = header,
            pcmPayload = data.copyOfRange(HEADER_SIZE, data.size),
        )
    }
}
