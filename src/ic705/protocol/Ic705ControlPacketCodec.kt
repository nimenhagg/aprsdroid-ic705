package org.aprsdroid.app.ic705.protocol

data class Ic705ControlPacket(
    val type: Int,
    val sequence: Int,
    val senderId: Int,
    val receiverId: Int,
) {
    init {
        requireUInt16("type", type)
        requireUInt16("sequence", sequence)
    }

    fun isAddressedTo(localId: Int): Boolean = receiverId == localId
}

/** Codec for the common 16-byte Icom LAN control header/packet. */
object Ic705ControlPacketCodec {
    const val PACKET_SIZE = 0x10

    const val TYPE_NULL = 0x00
    const val TYPE_RETRANSMIT = 0x01
    const val TYPE_ARE_YOU_THERE = 0x03
    const val TYPE_I_AM_HERE = 0x04
    const val TYPE_DISCONNECT = 0x05
    const val TYPE_READY = 0x06
    const val TYPE_PING = 0x07

    fun encode(packet: Ic705ControlPacket): ByteArray {
        val result = ByteArray(PACKET_SIZE)
        Ic705WireByteOrder.writeInt32Le(result, 0x00, PACKET_SIZE)
        Ic705WireByteOrder.writeUInt16Le(result, 0x04, packet.type)
        Ic705WireByteOrder.writeUInt16Le(result, 0x06, packet.sequence)
        Ic705WireByteOrder.writeInt32Le(result, 0x08, packet.senderId)
        Ic705WireByteOrder.writeInt32Le(result, 0x0c, packet.receiverId)
        return result
    }

    fun decode(data: ByteArray, expectedReceiverId: Int? = null): Ic705ControlPacket {
        protocolRequire(data.size == PACKET_SIZE) {
            "Control packet must be $PACKET_SIZE bytes, got ${data.size}"
        }
        val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
        protocolRequire(declaredLength == PACKET_SIZE) {
            "Control packet declares length $declaredLength, expected $PACKET_SIZE"
        }

        val packet = Ic705ControlPacket(
            type = Ic705WireByteOrder.readUInt16Le(data, 0x04),
            sequence = Ic705WireByteOrder.readUInt16Le(data, 0x06),
            senderId = Ic705WireByteOrder.readInt32Le(data, 0x08),
            receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c),
        )
        validateReceiverId(packet.receiverId, expectedReceiverId)
        return packet
    }

    /**
     * Decodes either a one-sequence 16-byte retransmit request or the variable
     * form whose requested sequence numbers follow the common header.
     */
    fun decodeRetransmitRequest(
        data: ByteArray,
        expectedReceiverId: Int? = null,
    ): List<Int> {
        protocolRequire(data.size >= PACKET_SIZE) {
            "Retransmit request must be at least $PACKET_SIZE bytes, got ${data.size}"
        }
        val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0x00)
        protocolRequire(declaredLength == data.size) {
            "Retransmit request declares length $declaredLength, actual length is ${data.size}"
        }
        val type = Ic705WireByteOrder.readUInt16Le(data, 0x04)
        protocolRequire(type == TYPE_RETRANSMIT) {
            "Retransmit request type must be $TYPE_RETRANSMIT, got $type"
        }
        validateReceiverId(Ic705WireByteOrder.readInt32Le(data, 0x0c), expectedReceiverId)

        if (data.size == PACKET_SIZE) {
            return listOf(Ic705WireByteOrder.readUInt16Le(data, 0x06))
        }
        val sequenceBytes = data.size - PACKET_SIZE
        protocolRequire(sequenceBytes % 2 == 0) {
            "Retransmit sequence list must contain complete 16-bit values"
        }
        return buildList(sequenceBytes / 2) {
            var offset = PACKET_SIZE
            while (offset < data.size) {
                add(Ic705WireByteOrder.readUInt16Le(data, offset))
                offset += 2
            }
        }
    }
}
