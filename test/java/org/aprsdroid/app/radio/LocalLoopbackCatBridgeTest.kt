package org.aprsdroid.app.radio

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLoopbackCatBridgeTest {

    @Test
    fun bridgeBindsOnlyToLoopbackAddress() {
        val radioStream = FakeRadioByteStream()
        LocalLoopbackCatBridge(radioStream).use { bridge ->
            assertTrue(bridge.port > 0)
            assertTrue(bridge.endpoint.startsWith("127.0.0.1:"))
        }
    }

    @Test
    fun bidirectionalDataPumping() {
        val radioStream = FakeRadioByteStream()
        LocalLoopbackCatBridge(radioStream).use { bridge ->
            bridge.start()

            // Connect a client socket (simulating Hamlib network transport)
            Socket("127.0.0.1", bridge.port).use { clientSocket ->
                val clientOut = clientSocket.getOutputStream()
                val clientIn = clientSocket.getInputStream()

                // Client -> Radio (CAT command)
                val catCommand = byteArrayOf(0xfe.toByte(), 0xfe.toByte(), 0xa4.toByte(), 0xe0.toByte(), 0x1c.toByte(), 0x00.toByte(), 0xfd.toByte())
                clientOut.write(catCommand)
                clientOut.flush()

                val receivedByRadio = ByteArray(catCommand.size)
                var totalRead = 0
                while (totalRead < catCommand.size) {
                    val read = radioStream.radioSideIn.read(receivedByRadio, totalRead, catCommand.size - totalRead)
                    assertTrue("Premature EOF", read > 0)
                    totalRead += read
                }
                assertArrayEquals(catCommand, receivedByRadio)

                // Radio -> Client (CAT reply/status)
                val catReply = byteArrayOf(0xfe.toByte(), 0xfe.toByte(), 0xe0.toByte(), 0xa4.toByte(), 0xfb.toByte(), 0xfd.toByte())
                radioStream.radioSideOut.write(catReply)
                radioStream.radioSideOut.flush()

                val receivedByClient = ByteArray(catReply.size)
                totalRead = 0
                while (totalRead < catReply.size) {
                    val read = clientIn.read(receivedByClient, totalRead, catReply.size - totalRead)
                    assertTrue("Premature EOF", read > 0)
                    totalRead += read
                }
                assertArrayEquals(catReply, receivedByClient)
            }
        }
    }

    @Test
    fun gracefulTeardownOnClose() {
        val radioStream = FakeRadioByteStream()
        val bridge = LocalLoopbackCatBridge(radioStream)
        bridge.start()

        val socket = Socket("127.0.0.1", bridge.port)
        bridge.close()

        assertTrue(bridge.isClosed)
        assertTrue(radioStream.closed)
        socket.close()
    }

    @Test
    fun radioEofClosesBridge() {
        val radioStream = FakeRadioByteStream()
        val bridge = LocalLoopbackCatBridge(radioStream)
        bridge.start()

        val socket = Socket("127.0.0.1", bridge.port)
        // Closing radio-side output causes bridge radio-to-client pump to see EOF
        radioStream.radioSideOut.close()

        // Give the pump thread a brief moment to observe EOF and trigger bridge close
        val deadline = System.currentTimeMillis() + 1000
        while (!bridge.isClosed && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertTrue(bridge.isClosed)
        socket.close()
    }

    /**
     * Test fake providing pipe-based bidirectional streams to simulate a radio serial device.
     */
    private class FakeRadioByteStream : RadioByteStream {
        // Data written by bridge goes to bridgeSideOut, readable by radioSideIn
        private val bridgeSideOut = PipedOutputStream()
        val radioSideIn = PipedInputStream(bridgeSideOut)

        // Data written by radio goes to radioSideOut, readable by bridgeSideIn
        val radioSideOut = PipedOutputStream()
        private val bridgeSideIn = PipedInputStream(radioSideOut)

        override val inputStream: InputStream = bridgeSideIn
        override val outputStream: OutputStream = bridgeSideOut

        var closed = false
            private set

        override fun close() {
            closed = true
            runCatching { bridgeSideOut.close() }
            runCatching { radioSideIn.close() }
            runCatching { radioSideOut.close() }
            runCatching { bridgeSideIn.close() }
        }
    }
}
