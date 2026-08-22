package org.aprsdroid.app.ic705.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Ic705HandshakeCodecTest {
    private val pingRequestGolden = hexBytes(
        "15 00 00 00 07 00 34 12 44 33 22 11 88 77 66 55 " +
            "00 ef cd ab 89",
    )
    private val pingReplyGolden = hexBytes(
        "15 00 00 00 07 00 34 12 88 77 66 55 44 33 22 11 " +
            "01 ef cd ab 89",
    )

    @Test
    fun pingRequestAndReplyMatchFt8cnWireLayout() {
        val request = Ic705PingPacket(
            sequence = 0x1234,
            senderId = 0x11223344,
            receiverId = 0x55667788,
            isReply = false,
            timestampBits = 0x89abcdef.toInt(),
        )
        val reply = request.copy(
            senderId = request.receiverId,
            receiverId = request.senderId,
            isReply = true,
        )

        assertArrayEquals(pingRequestGolden, Ic705HandshakeCodec.encodePing(request))
        assertArrayEquals(pingReplyGolden, Ic705HandshakeCodec.encodePing(reply))
        assertEquals(
            request,
            Ic705HandshakeCodec.decodePing(pingRequestGolden, expectedReceiverId = 0x55667788),
        )
        assertEquals(
            reply,
            Ic705HandshakeCodec.decodePing(pingReplyGolden, expectedReceiverId = 0x11223344),
        )
    }

    @Test
    fun pingRequestAcceptsZeroLengthFieldUsedByRealIc705() {
        val radioRequest = pingRequestGolden.copyOf().apply {
            fill(0, fromIndex = 0, toIndex = 4)
        }

        val decoded = Ic705HandshakeCodec.decodePing(
            radioRequest,
            expectedReceiverId = 0x55667788,
        )

        assertFalse(decoded.isReply)
        assertEquals(0x1234, decoded.sequence)
        assertEquals(0x89abcdef.toInt(), decoded.timestampBits)
        val reply = Ic705HandshakeCodec.encodePing(
            decoded.copy(
                senderId = decoded.receiverId,
                receiverId = decoded.senderId,
                isReply = true,
            ),
        )
        assertEquals(Ic705HandshakeCodec.PING_PACKET_SIZE, Ic705WireByteOrder.readInt32Le(reply, 0))
        assertEquals(1, reply[0x10].toInt() and 0xff)
        assertArrayEquals(radioRequest.copyOfRange(0x11, 0x15), reply.copyOfRange(0x11, 0x15))
    }

    @Test
    fun pingRejectsMalformedLengthAndWrongReceiver() {
        val malformed = pingRequestGolden.copyOf()
        malformed[0] = 0x14
        expectHandshakeProtocolFailure { Ic705HandshakeCodec.decodePing(malformed) }
        expectHandshakeProtocolFailure {
            Ic705HandshakeCodec.decodePing(pingRequestGolden, expectedReceiverId = 0x01020304)
        }
    }

    @Test
    fun civOpenAndCloseMatchFt8cnMixedEndianWireLayout() {
        val openGolden = hexBytes(
            "16 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
                "c0 01 00 9a bc 04",
        )
        val closeGolden = hexBytes(
            "16 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
                "c0 01 00 9a bc 00",
        )
        val open = Ic705CivOpenClosePacket(
            sequence = 0x1234,
            senderId = 0x11223344,
            receiverId = 0x55667788,
            civSequence = 0x9abc,
            action = Ic705CivChannelAction.OPEN,
        )

        assertArrayEquals(openGolden, Ic705HandshakeCodec.encodeCivOpenClose(open))
        assertArrayEquals(
            closeGolden,
            Ic705HandshakeCodec.encodeCivOpenClose(open.copy(action = Ic705CivChannelAction.CLOSE)),
        )
        assertEquals(
            open,
            Ic705HandshakeCodec.decodeCivOpenClose(openGolden, 0x55667788),
        )
    }

    @Test
    fun credentialPassCodeMatchesFt8cnGoldenVectors() {
        assertArrayEquals(
            hexBytes("56 6b 54 56 00 00 00 00 00 00 00 00 00 00 00 00"),
            Ic705HandshakeCodec.encodeCredentialPassCode("USER"),
        )
        assertArrayEquals(
            hexBytes("5b 4a 56 6f 00 00 00 00 00 00 00 00 00 00 00 00"),
            Ic705HandshakeCodec.encodeCredentialPassCode("PASS"),
        )
    }

    @Test
    fun credentialPassCodeRejectsNonAsciiAndOverlengthInput() {
        expectArgumentFailure { Ic705HandshakeCodec.encodeCredentialPassCode("密码") }
        expectArgumentFailure {
            Ic705HandshakeCodec.encodeCredentialPassCode("12345678901234567")
        }
    }

    @Test
    fun loginRequestMatchesFt8cnGoldenVector() {
        val golden = hexBytes(
            "80 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
                "00 00 00 70 01 00 9a bc 00 00 13 57 24 68 ac e0 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "56 6b 54 56 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "5b 4a 56 6f 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "41 50 52 53 64 72 6f 69 64 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
        )

        assertArrayEquals(
            golden,
            Ic705HandshakeCodec.encodeLoginRequest(
                sequence = 0x1234,
                senderId = 0x11223344,
                receiverId = 0x55667788,
                innerSequence = 0x9abc,
                tokenRequest = 0x1357,
                token = 0x2468ace0,
                username = "USER",
                password = "PASS",
                clientName = "APRSdroid",
            ),
        )
    }

    @Test
    fun loginResponseParsesFieldsAndValidatesReceiver() {
        val golden = hexBytes(
            "60 00 00 00 01 00 34 12 88 77 66 55 44 33 22 11 " +
                "00 00 00 50 02 00 9a bc 00 00 13 57 24 68 ac e0 " +
                "be ef 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
                "49 43 2d 37 30 35 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
        )

        val response = Ic705HandshakeCodec.decodeLoginResponse(golden, 0x11223344)
        assertEquals(0x1234, response.header.sequence)
        assertEquals(0x55667788, response.header.senderId)
        assertEquals(0x11223344, response.header.receiverId)
        assertEquals(0x9abc, response.header.innerSequence)
        assertEquals(0x1357, response.header.tokenRequest)
        assertEquals(0x2468ace0, response.header.token)
        assertEquals(0xbeef, response.authStartId)
        assertEquals(0, response.errorCode)
        assertEquals("IC-705", response.connectionName)
        assertTrue(response.isAuthenticated)

        expectHandshakeProtocolFailure {
            Ic705HandshakeCodec.decodeLoginResponse(golden, 0x01020304)
        }
    }

    @Test
    fun tokenConfirmAndRenewalMatchWfviewGoldenVectors() {
        val confirmGolden = hexBytes(
            "40 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
                "00 00 00 30 01 02 9a bc 00 00 13 57 24 68 ac e0 " +
                "00 00 00 00 07 98 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
        )
        val renewalGolden = hexBytes(
            "40 00 00 00 00 00 34 12 44 33 22 11 88 77 66 55 " +
                "00 00 00 30 01 05 9a bc 00 00 13 57 24 68 ac e0 " +
                "00 00 00 00 07 98 00 00 00 00 00 00 00 00 00 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
        )

        assertArrayEquals(confirmGolden, encodeTokenConfirm())
        assertArrayEquals(renewalGolden, encodeTokenRenewal())
    }

    @Test
    fun renewalResponseIsParsedWithoutInterpretingRejectedResponseAsSuccess() {
        val successful = tokenRenewalResponse(responseHex = "00 00 00 00")
        val rejected = tokenRenewalResponse(responseHex = "ff ff ff ff")

        val successPacket = Ic705HandshakeCodec.decodeTokenPacket(successful, 0x11223344)
        assertTrue(successPacket.isSuccessfulRenewal)
        assertEquals(0, successPacket.responseCode)

        val rejectedPacket = Ic705HandshakeCodec.decodeTokenPacket(rejected, 0x11223344)
        assertFalse(rejectedPacket.isSuccessfulRenewal)
        assertEquals(-1, rejectedPacket.responseCode)
    }

    @Test
    fun statusParsesBigEndianPortsAndConnectionFlags() {
        val golden = validStatusPacket()

        val status = Ic705HandshakeCodec.decodeStatusPacket(golden, 0x11223344)
        assertTrue(status.isAuthenticated)
        assertTrue(status.isConnected)
        assertEquals(0x1234, status.civPort)
        assertEquals(0x5678, status.audioPort)
    }

    @Test
    fun statusErrorCodeIsLittleEndian() {
        val packet = validStatusPacket().apply {
            this[0x30] = 0x78
            this[0x31] = 0x56
            this[0x32] = 0x34
            this[0x33] = 0x12
        }

        val status = Ic705HandshakeCodec.decodeStatusPacket(packet, 0x11223344)

        assertEquals(0x12345678, status.errorCode)
        assertFalse(status.isAuthenticated)
    }

    @Test
    fun successfulStatusCanExplicitlyReportZeroUnallocatedPorts() {
        val packet = validStatusPacket().apply {
            fill(0, fromIndex = 0x42, toIndex = 0x48)
        }

        val status = Ic705HandshakeCodec.decodeStatusPacket(packet, 0x11223344)

        assertEquals(0, status.errorCode)
        assertTrue(status.isAuthenticated)
        assertTrue(status.isConnected)
        assertEquals(0, status.civPort)
        assertEquals(0, status.audioPort)
    }

    @Test
    fun rejectedStatusPreservesUnsignedAllOnesAndDisconnectFlag() {
        val packet = validStatusPacket().apply {
            fill(0xff.toByte(), fromIndex = 0x30, toIndex = 0x34)
            this[0x40] = 1
            fill(0, fromIndex = 0x42, toIndex = 0x48)
        }

        val status = Ic705HandshakeCodec.decodeStatusPacket(packet, 0x11223344)

        assertEquals(-1, status.errorCode)
        assertFalse(status.isAuthenticated)
        assertFalse(status.isConnected)
        assertEquals(0, status.civPort)
        assertEquals(0, status.audioPort)
    }

    @Test
    fun authenticatedPacketsRejectBadPayloadLength() {
        val malformed = tokenRenewalResponse(responseHex = "00 00 00 00")
        malformed[0x13] = 0x2f
        expectHandshakeProtocolFailure { Ic705HandshakeCodec.decodeTokenPacket(malformed) }
    }

    private fun encodeTokenConfirm(): ByteArray = Ic705HandshakeCodec.encodeTokenConfirm(
        sequence = 0x1234,
        senderId = 0x11223344,
        receiverId = 0x55667788,
        innerSequence = 0x9abc,
        tokenRequest = 0x1357,
        token = 0x2468ace0,
    )

    private fun encodeTokenRenewal(): ByteArray = Ic705HandshakeCodec.encodeTokenRenewal(
        sequence = 0x1234,
        senderId = 0x11223344,
        receiverId = 0x55667788,
        innerSequence = 0x9abc,
        tokenRequest = 0x1357,
        token = 0x2468ace0,
    )

    private fun tokenRenewalResponse(responseHex: String): ByteArray = hexBytes(
        "40 00 00 00 01 00 34 12 88 77 66 55 44 33 22 11 " +
            "00 00 00 30 02 05 9a bc 00 00 13 57 24 68 ac e0 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "$responseHex 00 00 00 00 00 00 00 00 00 00 00 00",
    )

    private fun validStatusPacket(): ByteArray = hexBytes(
        "50 00 00 00 01 00 34 12 88 77 66 55 44 33 22 11 " +
            "00 00 00 40 02 03 9a bc 00 00 13 57 24 68 ac e0 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 12 34 00 00 56 78 00 00 00 00 00 00 00 00",
    )
}

private fun hexBytes(value: String): ByteArray {
    val compact = value.filterNot(Char::isWhitespace)
    require(compact.length % 2 == 0) { "Hex text must contain complete bytes" }
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private inline fun expectHandshakeProtocolFailure(block: () -> Unit) {
    try {
        block()
        fail("Expected Ic705ProtocolException")
    } catch (_: Ic705ProtocolException) {
        // Expected.
    }
}

private inline fun expectArgumentFailure(block: () -> Unit) {
    try {
        block()
        fail("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
        // Expected.
    }
}
