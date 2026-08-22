package org.aprsdroid.app.ic705.protocol

data class Ic705CivDatagram(
    val type: Int = 0,
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
    val civSequence: Int,
    val civFrame: ByteArray,
) {
    init {
        requireUInt16("type", type)
        requireUInt16("sequence", sequence)
        requireUInt16("civSequence", civSequence)
        require(civFrame.isNotEmpty()) { "CI-V frame must not be empty" }
        requireUInt16("civFrame length", civFrame.size)
    }
}

/** Codec for the Icom LAN CI-V envelope (0x15-byte header plus a CI-V frame). */
object Ic705CivDatagramCodec {
    const val HEADER_SIZE = 0x15
    const val CIV_MARKER = 0xc1

    fun encode(packet: Ic705CivDatagram): ByteArray {
        val result = ByteArray(HEADER_SIZE + packet.civFrame.size)
        Ic705WireByteOrder.writeInt32Le(result, 0x00, result.size)
        Ic705WireByteOrder.writeUInt16Le(result, 0x04, packet.type)
        Ic705WireByteOrder.writeUInt16Le(result, 0x06, packet.sequence)
        Ic705WireByteOrder.writeInt32Le(result, 0x08, packet.senderId)
        Ic705WireByteOrder.writeInt32Le(result, 0x0c, packet.receiverId)
        result[0x10] = CIV_MARKER.toByte()
        Ic705WireByteOrder.writeUInt16Le(result, 0x11, packet.civFrame.size)
        Ic705WireByteOrder.writeUInt16Be(result, 0x13, packet.civSequence)
        packet.civFrame.copyInto(result, destinationOffset = HEADER_SIZE)
        return result
    }

    fun decode(data: ByteArray, expectedReceiverId: Int? = null): Ic705CivDatagram {
        protocolRequire(data.size > HEADER_SIZE) {
            "CI-V datagram must contain a frame after its $HEADER_SIZE-byte header"
        }
        val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
        protocolRequire(declaredLength == data.size) {
            "CI-V datagram declares length $declaredLength, actual length is ${data.size}"
        }
        val type = Ic705WireByteOrder.readUInt16Le(data, 0x04)
        protocolRequire(type != Ic705ControlPacketCodec.TYPE_RETRANSMIT) {
            "Retransmit request is not a CI-V data envelope"
        }
        protocolRequire((data[0x10].toInt() and 0xff) == CIV_MARKER) {
            "CI-V datagram marker must be 0xc1"
        }
        val frameLength = Ic705WireByteOrder.readUInt16Le(data, 0x11)
        protocolRequire(frameLength == data.size - HEADER_SIZE) {
            "CI-V frame declares $frameLength bytes, actual frame is ${data.size - HEADER_SIZE}"
        }

        val packet = Ic705CivDatagram(
            type = type,
            sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
            senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
            receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
            civSequence = Ic705WireByteOrder.readUInt16Be(data, 0x13),
            civFrame = data.copyOfRange(HEADER_SIZE, data.size),
        )
        validateReceiverId(packet.receiverId, expectedReceiverId)
        return packet
    }
}

/** Pure CI-V command builders. This object performs no radio or socket I/O. */
object Ic705CivCommands {
    const val DEFAULT_RADIO_ADDRESS = 0xa4
    const val DEFAULT_CONTROLLER_ADDRESS = 0xe0

    private const val PREAMBLE = 0xfe
    private const val TERMINATOR = 0xfd
    private const val COMMAND_TRANSCEIVER_STATUS = 0x1c
    private const val SUBCOMMAND_PTT = 0x00

    fun buildPttFrame(
        pttOn: Boolean,
        radioAddress: Int = DEFAULT_RADIO_ADDRESS,
        controllerAddress: Int = DEFAULT_CONTROLLER_ADDRESS,
    ): ByteArray {
        requireUInt8("radioAddress", radioAddress)
        requireUInt8("controllerAddress", controllerAddress)
        return byteArrayOf(
            PREAMBLE.toByte(),
            PREAMBLE.toByte(),
            radioAddress.toByte(),
            controllerAddress.toByte(),
            COMMAND_TRANSCEIVER_STATUS.toByte(),
            SUBCOMMAND_PTT.toByte(),
            if (pttOn) 0x01 else 0x00,
            TERMINATOR.toByte(),
        )
    }
}
