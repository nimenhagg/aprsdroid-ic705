package org.aprsdroid.app.radio

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import org.aprsdroid.app.diagnostic.AppLog

/**
 * Interface representing a raw bidirectional stream between Android and a radio.
 * Decoupled from Android USB APIs to allow host-JVM testing.
 */
interface RadioByteStream : AutoCloseable {
    val inputStream: InputStream
    val outputStream: OutputStream
}

/**
 * Local TCP loopback bridge for Hamlib CAT transport.
 *
 * Hamlib Android is built --without-libusb, so direct USB communication is
 * bridged through a local loopback TCP port:
 *
 * Hamlib backend -> 127.0.0.1:<ephemeral-port> -> LocalLoopbackCatBridge -> USB Serial / Radio
 *
 * Security & Lifecycle Invariants (ROADMAP Section 16.4):
 * 1. Binds exclusively to 127.0.0.1 (Loopback address), never 0.0.0.0 or external interfaces.
 * 2. Uses an ephemeral port allocated by the OS (port 0) with clear lifecycle ownership.
 * 3. Graceful bidirectional pump with clean teardown to prevent zombie threads/sockets.
 * 4. Transparent byte transfer without parsing CAT/CI-V commands (Hamlib owns CAT semantics).
 */
class LocalLoopbackCatBridge(
    private val radioStream: RadioByteStream,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) : AutoCloseable {

    companion object {
        const val DEFAULT_BUFFER_SIZE = 1024
        private const val TAG = "RADIO.CAT_BRIDGE"
    }

    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort
    val endpoint: String = "127.0.0.1:$port"

    private val closed = AtomicBoolean(false)
    private var clientSocket: Socket? = null
    private var acceptThread: Thread? = null
    private var clientToRadioThread: Thread? = null
    private var radioToClientThread: Thread? = null

    val isClosed: Boolean
        get() = closed.get()

    fun start() {
        check(!closed.get()) { "Bridge is closed" }
        acceptThread = Thread({
            try {
                AppLog.i(TAG, "bridge_listening", mapOf("endpoint" to endpoint))
                val socket = serverSocket.accept()
                synchronized(this) {
                    if (closed.get()) {
                        socket.close()
                        return@Thread
                    }
                    clientSocket = socket
                }
                AppLog.i(TAG, "client_connected", mapOf("client" to socket.remoteSocketAddress.toString()))
                startPumping(socket)
            } catch (e: Exception) {
                if (!closed.get()) {
                    AppLog.w(TAG, "bridge_accept_failed", error = e)
                }
            }
        }, "CatBridge-Accept-$port").apply {
            isDaemon = true
            start()
        }
    }

    private fun startPumping(socket: Socket) {
        val socketIn = socket.getInputStream()
        val socketOut = socket.getOutputStream()
        val radioIn = radioStream.inputStream
        val radioOut = radioStream.outputStream

        clientToRadioThread = Thread({
            pump(socketIn, radioOut, "client_to_radio")
        }, "CatBridge-C2R-$port").apply {
            isDaemon = true
            start()
        }

        radioToClientThread = Thread({
            pump(radioIn, socketOut, "radio_to_client")
        }, "CatBridge-R2C-$port").apply {
            isDaemon = true
            start()
        }
    }

    private fun pump(input: InputStream, output: OutputStream, direction: String) {
        val buffer = ByteArray(bufferSize)
        try {
            while (!closed.get()) {
                val read = input.read(buffer)
                if (read < 0) {
                    AppLog.d(TAG, "stream_eof", mapOf("direction" to direction))
                    break
                }
                if (read > 0) {
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            if (!closed.get() && e !is SocketException) {
                AppLog.d(TAG, "pump_interrupted", mapOf("direction" to direction, "error" to (e.message ?: "")))
            }
        } finally {
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        AppLog.i(TAG, "bridge_closing", mapOf("endpoint" to endpoint))

        runCatching { serverSocket.close() }
        synchronized(this) {
            clientSocket?.let { runCatching { it.close() } }
            clientSocket = null
        }
        runCatching { radioStream.close() }

        clientToRadioThread?.interrupt()
        radioToClientThread?.interrupt()
        acceptThread?.interrupt()
    }
}
