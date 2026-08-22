package org.aprsdroid.app.ic705.protocol

import java.nio.charset.StandardCharsets

/** Common authenticated-packet fields shared by login, token, status, and connection info. */
data class Ic705AuthPacketHeader(
    val type: Int,
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val requestReply: Int,
    val requestType: Int,
    val innerSequence: Int,
    val tokenRequest: Int,
    val token: Int,
) {
    init {
        requireUInt16("type", type)
        requireUInt16("sequence", sequence)
        requireUInt8("requestReply", requestReply)
        requireUInt8("requestType", requestType)
        requireUInt16("innerSequence", innerSequence)
        requireUInt16("tokenRequest", tokenRequest)
    }
}

data class Ic705PingPacket(
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val isReply: Boolean,
    /** Low 32 bits of the sender's time value, preserved without interpreting its clock. */
    val timestampBits: Int,
) {
    init {
        requireUInt16("sequence", sequence)
    }
}

enum class Ic705CivChannelAction(val wireValue: Int) {
    CLOSE(0x00),
    OPEN(0x04),
}

data class Ic705CivOpenClosePacket(
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val civSequence: Int,
    val action: Ic705CivChannelAction,
) {
    init {
        requireUInt16("sequence", sequence)
        requireUInt16("civSequence", civSequence)
    }
}

data class Ic705LoginResponse(
    val header: Ic705AuthPacketHeader,
    val authStartId: Int,
    val errorCode: Int,
    val connectionName: String,
) {
    val isAuthenticated: Boolean
        get() = errorCode == 0
}

data class Ic705TokenPacket(
    val header: Ic705AuthPacketHeader,
    val responseCode: Int,
) {
    val isSuccessfulRenewal: Boolean
        get() = header.type == Ic705HandshakeCodec.RESPONSE_TYPE &&
            header.requestReply == Ic705HandshakeCodec.REQUEST_REPLY_RESPONSE &&
            header.requestType == Ic705HandshakeCodec.TOKEN_REQUEST_RENEWAL &&
            responseCode == 0
}

data class Ic705StatusPacket(
    val header: Ic705AuthPacketHeader,
    val errorCode: Int,
    val disconnectFlag: Int,
    val civPort: Int,
    val audioPort: Int,
) {
    val isAuthenticated: Boolean
        get() = errorCode == 0

    val isConnected: Boolean
        get() = disconnectFlag == 0
}

/**
 * Pure byte codec for the IC-705 LAN authentication and channel-opening handshake.
 *
 * This object performs no I/O and deliberately exposes no logging hooks: usernames and
 * passwords must never be included in diagnostics. The layout follows the packets used by
 * FT8CN; radio interoperability still needs validation against a real IC-705.
 */
object Ic705HandshakeCodec {
    const val PING_PACKET_SIZE = 0x15
    const val CIV_OPEN_CLOSE_PACKET_SIZE = 0x16
    const val TOKEN_PACKET_SIZE = 0x40
    const val STATUS_PACKET_SIZE = 0x50
    const val LOGIN_RESPONSE_PACKET_SIZE = 0x60
    const val LOGIN_REQUEST_PACKET_SIZE = 0x80

    const val RESPONSE_TYPE = 0x01
    const val REQUEST_REPLY_REQUEST = 0x01
    const val REQUEST_REPLY_RESPONSE = 0x02

    const val TOKEN_REQUEST_DELETE = 0x01
    const val TOKEN_REQUEST_CONFIRM = 0x02
    const val TOKEN_REQUEST_DISCONNECT = 0x04
    const val TOKEN_REQUEST_RENEWAL = 0x05

    private const val PING_TYPE = 0x07
    private const val PING_REQUEST = 0x00
    private const val PING_REPLY = 0x01
    private const val CIV_OPEN_CLOSE_MARKER = 0xc0
    private const val CREDENTIAL_SIZE = 16
    private const val TOKEN_RESET_CAPABILITY_OFFSET = 0x24
    private const val TOKEN_RESET_CAPABILITY = 0x0798

    fun encodePing(packet: Ic705PingPacket): ByteArray {
        val result = ByteArray(PING_PACKET_SIZE)
        writeBaseHeader(
            destination = result,
            packetSize = PING_PACKET_SIZE,
            type = PING_TYPE,
            sequence = packet.sequence,
            senderId = packet.senderId,
            receiverId = packet.receiverId,
        )
        result[0x10] = if (packet.isReply) PING_REPLY.toByte() else PING_REQUEST.toByte()
        Ic705WireByteOrder.writeInt32Le(result, 0x11, packet.timestampBits)
        return result
    }

    fun decodePing(data: ByteArray, expectedReceiverId: Int? = null): Ic705PingPacket {
        protocolRequire(data.size == PING_PACKET_SIZE) {
            "Ping packet must be $PING_PACKET_SIZE bytes, got ${data.size}"
        }
        val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
        // A real IC-705 sends zero in the ping length field, while FT8CN and
        // wfview-style clients send 0x15. Both layouts are interoperable.
        protocolRequire(declaredLength == 0 || declaredLength == PING_PACKET_SIZE) {
            "Ping packet declares length $declaredLength, expected 0 or $PING_PACKET_SIZE"
        }
        validateReceiverId(Ic705WireByteOrder.readInt32Le(data, 0x0c), expectedReceiverId)
        val type = Ic705WireByteOrder.readUInt16Le(data, 0x04)
        protocolRequire(type == PING_TYPE) {
            "Ping packet type must be $PING_TYPE, got $type"
        }
        val reply = data[0x10].toInt() and 0xff
        protocolRequire(reply == PING_REQUEST || reply == PING_REPLY) {
            "Ping reply marker must be 0 or 1, got $reply"
        }
        return Ic705PingPacket(
            sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
            senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
            receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
            isReply = reply == PING_REPLY,
            timestampBits = Ic705WireByteOrder.readInt32Le(data, 0x11),
        )
    }

    fun encodeCivOpenClose(packet: Ic705CivOpenClosePacket): ByteArray {
        val result = ByteArray(CIV_OPEN_CLOSE_PACKET_SIZE)
        writeBaseHeader(
            destination = result,
            packetSize = CIV_OPEN_CLOSE_PACKET_SIZE,
            type = 0,
            sequence = packet.sequence,
            senderId = packet.senderId,
            receiverId = packet.receiverId,
        )
        result[0x10] = CIV_OPEN_CLOSE_MARKER.toByte()
        // The embedded payload length is little-endian, unlike the CI-V sequence that follows.
        Ic705WireByteOrder.writeUInt16Le(result, 0x11, 1)
        Ic705WireByteOrder.writeUInt16Be(result, 0x13, packet.civSequence)
        result[0x15] = packet.action.wireValue.toByte()
        return result
    }

    fun decodeCivOpenClose(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): Ic705CivOpenClosePacket {
        validatePacketEnvelope(data, CIV_OPEN_CLOSE_PACKET_SIZE, expectedReceiverId)
        val type = Ic705WireByteOrder.readUInt16Le(data, 0x04)
        protocolRequire(type == 0) { "CI-V open/close packet type must be 0, got $type" }
        val marker = data[0x10].toInt() and 0xff
        protocolRequire(marker == CIV_OPEN_CLOSE_MARKER) {
            "CI-V open/close marker must be 0xc0, got 0x${marker.toString(16)}"
        }
        val payloadLength = Ic705WireByteOrder.readUInt16Le(data, 0x11)
        protocolRequire(payloadLength == 1) {
            "CI-V open/close payload must be 1 byte, got $payloadLength"
        }
        val actionByte = data[0x15].toInt() and 0xff
        val action = Ic705CivChannelAction.values().firstOrNull { it.wireValue == actionByte }
        protocolRequire(action != null) {
            "Unknown CI-V open/close action 0x${actionByte.toString(16)}"
        }
        return Ic705CivOpenClosePacket(
            sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
            senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
            receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
            civSequence = Ic705WireByteOrder.readUInt16Be(data, 0x13),
            action = action!!,
        )
    }

    /** Implements Icom's 16-byte `passCode` substitution used for credentials. */
    fun encodeCredentialPassCode(value: String): ByteArray {
        val source = encodeAscii(value, CREDENTIAL_SIZE, "credential")
        val encoded = ByteArray(CREDENTIAL_SIZE)
        source.forEachIndexed { index, byte ->
            var tableIndex = ((byte.toInt() and 0xff) + index) and 0xff
            if (tableIndex > 126) {
                tableIndex = 32 + tableIndex % 127
            }
            encoded[index] = when {
                tableIndex < 32 -> 0
                else -> PASS_CODE_PRINTABLE_SEQUENCE[tableIndex - 32]
            }
        }
        return encoded
    }

    fun encodeLoginRequest(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        innerSequence: Int,
        tokenRequest: Int,
        token: Int,
        username: String,
        password: String,
        clientName: String,
    ): ByteArray {
        val result = ByteArray(LOGIN_REQUEST_PACKET_SIZE)
        writeAuthPacketHeader(
            destination = result,
            packetSize = LOGIN_REQUEST_PACKET_SIZE,
            header = Ic705AuthPacketHeader(
                type = 0,
                sequence = sequence,
                senderId = senderId,
                receiverId = receiverId,
                requestReply = REQUEST_REPLY_REQUEST,
                requestType = 0,
                innerSequence = innerSequence,
                tokenRequest = tokenRequest,
                token = token,
            ),
        )
        encodeCredentialPassCode(username).copyInto(result, destinationOffset = 0x40)
        encodeCredentialPassCode(password).copyInto(result, destinationOffset = 0x50)
        encodeAscii(clientName, CREDENTIAL_SIZE, "clientName")
            .copyInto(result, destinationOffset = 0x60)
        return result
    }

    fun decodeLoginResponse(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): Ic705LoginResponse {
        val header = decodeAuthPacketHeader(
            data = data,
            expectedPacketSize = LOGIN_RESPONSE_PACKET_SIZE,
            expectedReceiverId = expectedReceiverId,
        )
        return Ic705LoginResponse(
            header = header,
            authStartId = Ic705WireByteOrder.readUInt16Be(data, 0x20),
            errorCode = Ic705WireByteOrder.readInt32Le(data, 0x30),
            connectionName = decodeAscii(data, 0x40, CREDENTIAL_SIZE),
        )
    }

    fun encodeTokenConfirm(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        innerSequence: Int,
        tokenRequest: Int,
        token: Int,
    ): ByteArray = encodeTokenRequest(
        sequence = sequence,
        senderId = senderId,
        receiverId = receiverId,
        requestType = TOKEN_REQUEST_CONFIRM,
        innerSequence = innerSequence,
        tokenRequest = tokenRequest,
        token = token,
    )

    fun encodeTokenRenewal(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        innerSequence: Int,
        tokenRequest: Int,
        token: Int,
    ): ByteArray = encodeTokenRequest(
        sequence = sequence,
        senderId = senderId,
        receiverId = receiverId,
        requestType = TOKEN_REQUEST_RENEWAL,
        innerSequence = innerSequence,
        tokenRequest = tokenRequest,
        token = token,
    )

    fun encodeTokenDelete(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        innerSequence: Int,
        tokenRequest: Int,
        token: Int,
    ): ByteArray = encodeTokenRequest(
        sequence = sequence,
        senderId = senderId,
        receiverId = receiverId,
        requestType = TOKEN_REQUEST_DELETE,
        innerSequence = innerSequence,
        tokenRequest = tokenRequest,
        token = token,
    )

    fun decodeTokenPacket(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): Ic705TokenPacket = Ic705TokenPacket(
        header = decodeAuthPacketHeader(
            data = data,
            expectedPacketSize = TOKEN_PACKET_SIZE,
            expectedReceiverId = expectedReceiverId,
        ),
        responseCode = Ic705WireByteOrder.readInt32Be(data, 0x30),
    )

    fun decodeStatusPacket(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): Ic705StatusPacket {
        val header = decodeAuthPacketHeader(
            data = data,
            expectedPacketSize = STATUS_PACKET_SIZE,
            expectedReceiverId = expectedReceiverId,
        )
        return Ic705StatusPacket(
            header = header,
            errorCode = Ic705WireByteOrder.readInt32Le(data, 0x30),
            disconnectFlag = data[0x40].toInt() and 0xff,
            civPort = Ic705WireByteOrder.readUInt16Be(data, 0x42),
            audioPort = Ic705WireByteOrder.readUInt16Be(data, 0x46),
        )
    }

    private fun encodeTokenRequest(
        sequence: Int,
        senderId: Int,
        receiverId: Int,
        requestType: Int,
        innerSequence: Int,
        tokenRequest: Int,
        token: Int,
    ): ByteArray {
        val result = ByteArray(TOKEN_PACKET_SIZE)
        writeAuthPacketHeader(
            destination = result,
            packetSize = TOKEN_PACKET_SIZE,
            header = Ic705AuthPacketHeader(
                type = 0,
                sequence = sequence,
                senderId = senderId,
                receiverId = receiverId,
                requestReply = REQUEST_REPLY_REQUEST,
                requestType = requestType,
                innerSequence = innerSequence,
                tokenRequest = tokenRequest,
                token = token,
            ),
        )
        // wfview includes this marker in confirm, renewal, and delete requests.
        // Some radios authenticate a zero-filled token packet but do not proceed
        // to stream endpoint allocation afterward.
        Ic705WireByteOrder.writeUInt16Be(
            result,
            TOKEN_RESET_CAPABILITY_OFFSET,
            TOKEN_RESET_CAPABILITY,
        )
        return result
    }

    private fun encodeAscii(value: String, maxLength: Int, fieldName: String): ByteArray {
        require(value.all { it.code <= 0x7f }) { "$fieldName must contain US-ASCII only" }
        val source = value.toByteArray(StandardCharsets.US_ASCII)
        require(source.size <= maxLength) { "$fieldName must be at most $maxLength bytes" }
        return source
    }

    private fun decodeAscii(data: ByteArray, offset: Int, length: Int): String {
        val firstNull = (offset until offset + length).firstOrNull { data[it] == 0.toByte() }
        val end = firstNull ?: (offset + length)
        return String(data, offset, end - offset, StandardCharsets.US_ASCII).trimEnd(' ')
    }

    private val PASS_CODE_PRINTABLE_SEQUENCE = byteArrayOf(
        0x47, 0x5d, 0x4c, 0x42, 0x66, 0x20, 0x23, 0x46, 0x4e, 0x57,
        0x45, 0x3d, 0x67, 0x76, 0x60, 0x41, 0x62, 0x39, 0x59, 0x2d,
        0x68, 0x7e, 0x7c, 0x65, 0x7d, 0x49, 0x29, 0x72, 0x73, 0x78,
        0x21, 0x6e, 0x5a, 0x5e, 0x4a, 0x3e, 0x71, 0x2c, 0x2a, 0x54,
        0x3c, 0x3a, 0x63, 0x4f, 0x43, 0x75, 0x27, 0x79, 0x5b, 0x35,
        0x70, 0x48, 0x6b, 0x56, 0x6f, 0x34, 0x32, 0x6c, 0x30, 0x61,
        0x6d, 0x7b, 0x2f, 0x4b, 0x64, 0x38, 0x2b, 0x2e, 0x50, 0x40,
        0x3f, 0x55, 0x33, 0x37, 0x25, 0x77, 0x24, 0x26, 0x74, 0x6a,
        0x28, 0x53, 0x4d, 0x69, 0x22, 0x5c, 0x44, 0x31, 0x36, 0x58,
        0x3b, 0x7a, 0x51, 0x5f, 0x52,
    )
}

/**
 * Writes the mixed-endian authenticated prefix used by 0x40/0x50/0x60/0x80/0x90 packets.
 * The destination is expected to be zero-filled so reserved fields remain zero.
 */
internal fun writeAuthPacketHeader(
    destination: ByteArray,
    packetSize: Int,
    header: Ic705AuthPacketHeader,
) {
    require(destination.size == packetSize) {
        "Destination size ${destination.size} does not match packet size $packetSize"
    }
    require(packetSize >= 0x20) { "Authenticated packet must be at least 32 bytes" }
    requireUInt16("payloadSize", packetSize - Ic705ControlPacketCodec.PACKET_SIZE)
    writeBaseHeader(
        destination = destination,
        packetSize = packetSize,
        type = header.type,
        sequence = header.sequence,
        senderId = header.senderId,
        receiverId = header.receiverId,
    )
    Ic705WireByteOrder.writeUInt16Be(
        destination,
        0x12,
        packetSize - Ic705ControlPacketCodec.PACKET_SIZE,
    )
    destination[0x14] = header.requestReply.toByte()
    destination[0x15] = header.requestType.toByte()
    Ic705WireByteOrder.writeUInt16Be(destination, 0x16, header.innerSequence)
    Ic705WireByteOrder.writeUInt16Be(destination, 0x1a, header.tokenRequest)
    Ic705WireByteOrder.writeInt32Be(destination, 0x1c, header.token)
}

internal fun decodeAuthPacketHeader(
    data: ByteArray,
    expectedPacketSize: Int,
    expectedReceiverId: Int? = null,
): Ic705AuthPacketHeader {
    require(expectedPacketSize >= 0x20) { "Authenticated packet must be at least 32 bytes" }
    validatePacketEnvelope(data, expectedPacketSize, expectedReceiverId)
    val payloadSize = Ic705WireByteOrder.readUInt16Be(data, 0x12)
    val expectedPayloadSize = expectedPacketSize - Ic705ControlPacketCodec.PACKET_SIZE
    protocolRequire(payloadSize == expectedPayloadSize) {
        "Authenticated packet payload declares $payloadSize bytes, expected $expectedPayloadSize"
    }
    return Ic705AuthPacketHeader(
        type = Ic705WireByteOrder.readUInt16Le(data, 0x04),
        sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
        senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
        receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
        requestReply = data[0x14].toInt() and 0xff,
        requestType = data[0x15].toInt() and 0xff,
        innerSequence = Ic705WireByteOrder.readUInt16Be(data, 0x16),
        tokenRequest = Ic705WireByteOrder.readUInt16Be(data, 0x1a),
        token = Ic705WireByteOrder.readInt32Be(data, 0x1c),
    )
}

private fun writeBaseHeader(
    destination: ByteArray,
    packetSize: Int,
    type: Int,
    sequence: Int,
    senderId: Int,
    receiverId: Int,
) {
    require(destination.size == packetSize)
    requireUInt16("type", type)
    requireUInt16("sequence", sequence)
    Ic705WireByteOrder.writeInt32Le(destination, 0x00, packetSize)
    Ic705WireByteOrder.writeUInt16Le(destination, 0x04, type)
    Ic705WireByteOrder.writeUInt16Le(destination, 0x06, sequence)
    Ic705WireByteOrder.writeInt32Le(destination, 0x08, senderId)
    Ic705WireByteOrder.writeInt32Le(destination, 0x0c, receiverId)
}

private fun validatePacketEnvelope(
    data: ByteArray,
    expectedPacketSize: Int,
    expectedReceiverId: Int?,
) {
    protocolRequire(data.size == expectedPacketSize) {
        "Packet must be $expectedPacketSize bytes, got ${data.size}"
    }
    val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
    protocolRequire(declaredLength == expectedPacketSize) {
        "Packet declares length $declaredLength, expected $expectedPacketSize"
    }
    validateReceiverId(Ic705WireByteOrder.readInt32Le(data, 0x0c), expectedReceiverId)
}
