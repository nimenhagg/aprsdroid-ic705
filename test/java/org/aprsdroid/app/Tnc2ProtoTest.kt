package org.aprsdroid.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Tnc2ProtoTest {
    @Test
    fun readPacketReturnsLineBeforeEof() {
        val proto = Tnc2Proto(
            ByteArrayInputStream("N0CALL>APRS:test\n".toByteArray()),
            ByteArrayOutputStream(),
        )

        assertEquals("N0CALL>APRS:test", proto.readPacket())
    }

    @Test
    fun readPacketThrowsOnEofInsteadOfReturningEmptyLine() {
        val proto = Tnc2Proto(
            ByteArrayInputStream(ByteArray(0)),
            ByteArrayOutputStream(),
        )

        try {
            proto.readPacket()
            fail("Expected EOFException")
        } catch (_: EOFException) {
            // EOF must reach the backend reconnect path as an I/O failure.
        }
    }
}
