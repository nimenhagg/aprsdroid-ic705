package org.aprsdroid.app.ic705.protocol

/** Raised when a UDP datagram does not match the IC-705 wire format. */
class Ic705ProtocolException(message: String) : IllegalArgumentException(message)

/**
 * Explicit byte-order primitives for the mixed-endian IC-705 LAN protocol.
 *
 * FT8CN's helpers named `*BigEndian` actually encode little-endian values. Keeping the
 * byte order in every method name avoids carrying that ambiguity into this implementation.
 */
object Ic705WireByteOrder {
    fun readUInt16Le(data: ByteArray, offset: Int): Int {
        requireAvailable(data, offset, 2)
        return (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8)
    }

    fun readUInt16Be(data: ByteArray, offset: Int): Int {
        requireAvailable(data, offset, 2)
        return ((data[offset].toInt() and 0xff) shl 8) or
            (data[offset + 1].toInt() and 0xff)
    }

    fun readInt32Le(data: ByteArray, offset: Int): Int {
        requireAvailable(data, offset, 4)
        return (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)
    }

    fun readInt32Be(data: ByteArray, offset: Int): Int {
        requireAvailable(data, offset, 4)
        return ((data[offset].toInt() and 0xff) shl 24) or
            ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or
            (data[offset + 3].toInt() and 0xff)
    }

    fun writeUInt16Le(data: ByteArray, offset: Int, value: Int) {
        requireUInt16("value", value)
        requireAvailable(data, offset, 2)
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
    }

    fun writeUInt16Be(data: ByteArray, offset: Int, value: Int) {
        requireUInt16("value", value)
        requireAvailable(data, offset, 2)
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    fun writeInt32Le(data: ByteArray, offset: Int, value: Int) {
        requireAvailable(data, offset, 4)
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
        data[offset + 2] = (value ushr 16).toByte()
        data[offset + 3] = (value ushr 24).toByte()
    }

    fun writeInt32Be(data: ByteArray, offset: Int, value: Int) {
        requireAvailable(data, offset, 4)
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    private fun requireAvailable(data: ByteArray, offset: Int, width: Int) {
        require(offset >= 0 && offset <= data.size - width) {
            "Need $width bytes at offset $offset, but array size is ${data.size}"
        }
    }
}

internal fun requireUInt8(field: String, value: Int) {
    require(value in 0..0xff) { "$field must fit in an unsigned byte: $value" }
}

internal fun requireUInt16(field: String, value: Int) {
    require(value in 0..0xffff) { "$field must fit in an unsigned 16-bit field: $value" }
}

internal fun protocolRequire(condition: Boolean, message: () -> String) {
    if (!condition) throw Ic705ProtocolException(message())
}

internal fun validateReceiverId(actual: Int, expected: Int?) {
    if (expected != null) {
        protocolRequire(actual == expected) {
            "Datagram receiver ID $actual does not match local ID $expected"
        }
    }
}
