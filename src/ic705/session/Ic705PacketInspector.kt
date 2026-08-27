package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705ConnectionInfoCodec
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705HandshakeCodec
import org.aprsdroid.app.ic705.protocol.Ic705ProtocolException
import org.aprsdroid.app.ic705.protocol.Ic705WireByteOrder

internal fun ic705PacketDiagnostic(data: ByteArray, localId: Int): Ic705PacketDiagnostic {
    val declaredLength = if (data.size >= 4) Ic705WireByteOrder.readInt32Le(data, 0) else null
    val commonType = if (data.size >= 6) Ic705WireByteOrder.readUInt16Le(data, 4) else null
    val receiverKind = if (data.size < Ic705ControlPacketCodec.PACKET_SIZE) {
        Ic705PacketReceiverKind.ABSENT
    } else {
        when (Ic705WireByteOrder.readInt32Le(data, 0x0c)) {
            localId -> Ic705PacketReceiverKind.LOCAL
            0 -> Ic705PacketReceiverKind.ZERO
            else -> Ic705PacketReceiverKind.OTHER
        }
    }
    val rejection = when {
        data.size < Ic705ControlPacketCodec.PACKET_SIZE -> {
            Ic705PacketRejectionKind.HEADER_TOO_SHORT
        }
        declaredLength != data.size -> Ic705PacketRejectionKind.DECLARED_LENGTH_MISMATCH
        receiverKind == Ic705PacketReceiverKind.ZERO -> Ic705PacketRejectionKind.RECEIVER_ZERO
        receiverKind == Ic705PacketReceiverKind.OTHER -> Ic705PacketRejectionKind.RECEIVER_OTHER
        else -> Ic705PacketRejectionKind.PACKET_CODEC
    }
    val hasAuthenticatedHeader = data.size in IC705_AUTHENTICATED_PACKET_SIZES
    return Ic705PacketDiagnostic(
        length = data.size,
        declaredLength = declaredLength,
        commonType = commonType,
        receiverKind = receiverKind,
        payloadLength = if (hasAuthenticatedHeader) {
            Ic705WireByteOrder.readUInt16Be(data, 0x12)
        } else {
            null
        },
        requestReply = if (hasAuthenticatedHeader) data[0x14].toInt() and 0xff else null,
        requestType = if (hasAuthenticatedHeader) data[0x15].toInt() and 0xff else null,
        rejection = rejection,
    )
}

@Throws(Ic705ProtocolException::class)
internal fun validateIc705CommonEnvelope(data: ByteArray, expectedReceiverId: Int) {
    if (data.size < Ic705ControlPacketCodec.PACKET_SIZE) {
        throw Ic705ProtocolException("Datagram is shorter than the common Icom header")
    }
    val declaredLength = Ic705WireByteOrder.readInt32Le(data, 0)
    if (declaredLength != data.size) {
        throw Ic705ProtocolException("Datagram length does not match its common header")
    }
    val receiverId = Ic705WireByteOrder.readInt32Le(data, 0x0c)
    if (receiverId != expectedReceiverId) {
        throw Ic705ProtocolException("Datagram receiver ID does not match this channel")
    }
}

internal fun isIc705VariableRetransmit(data: ByteArray): Boolean =
    data.size > Ic705ControlPacketCodec.PACKET_SIZE &&
        data.size % 2 == 0 &&
        Ic705WireByteOrder.readUInt16Le(data, 0x04) == Ic705ControlPacketCodec.TYPE_RETRANSMIT

internal fun looksLikeIc705Audio(data: ByteArray): Boolean = runCatching {
    data.size > Ic705AudioPacketCodec.HEADER_SIZE &&
        Ic705WireByteOrder.readInt32Le(data, 0) == data.size &&
        Ic705WireByteOrder.readUInt16Le(data, 0x04) != Ic705ControlPacketCodec.TYPE_RETRANSMIT &&
        Ic705WireByteOrder.readUInt16Be(data, 0x16) == data.size - Ic705AudioPacketCodec.HEADER_SIZE
}.getOrDefault(false)

internal val IC705_AUTHENTICATED_PACKET_SIZES = setOf(
    Ic705HandshakeCodec.TOKEN_PACKET_SIZE,
    Ic705HandshakeCodec.STATUS_PACKET_SIZE,
    Ic705HandshakeCodec.LOGIN_RESPONSE_PACKET_SIZE,
    Ic705ConnectionInfoCodec.PACKET_SIZE,
)
