package org.aprsdroid.app.ic705.transport

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

enum class Ic705ChannelRole {
    CONTROL,
    CIV,
    AUDIO,
}

data class Ic705ReceivedDatagram(
    val data: ByteArray,
    val source: InetSocketAddress,
)

/**
 * Creates and binds a socket. An Android implementation can bind the socket to
 * a chosen `Network` before returning it; the transport itself stays Android-free.
 */
fun interface Ic705DatagramSocketFactory {
    @Throws(SocketException::class)
    fun create(localAddress: InetSocketAddress): DatagramSocket
}

interface Ic705DatagramChannel : Closeable {
    val role: Ic705ChannelRole
    val isOpen: Boolean
    val boundLocalAddress: InetAddress?
    val localPort: Int
    val remoteEndpoint: InetSocketAddress?

    fun open()
    /**
     * Sets the send target. With [lockSource] false the socket remains unconnected,
     * allowing a broadcast discovery target to receive the radio's unicast reply.
     */
    fun setRemoteEndpoint(endpoint: InetSocketAddress?, lockSource: Boolean = true)

    @Throws(IOException::class)
    fun send(data: ByteArray)
}

fun interface Ic705DatagramChannelFactory {
    fun create(
        role: Ic705ChannelRole,
        localAddress: InetSocketAddress,
        onDatagram: (Ic705ReceivedDatagram) -> Unit,
        onError: (IOException) -> Unit,
    ): Ic705DatagramChannel
}

private val defaultSocketFactory = Ic705DatagramSocketFactory { localAddress ->
    DatagramSocket(null).apply {
        broadcast = true
        bind(localAddress)
    }
}

/**
 * One UDP channel used by the Icom LAN control, CI-V, or audio stream.
 *
 * Packet encoding, sequence numbers, retries, and reconnect policy intentionally
 * belong to the session layer. This class only owns socket lifetime and source
 * endpoint filtering. The socket intentionally remains unconnected: on Windows
 * and Android/JDK implementations, connect/disconnect can contend with a blocking
 * receive. Logical source locking is enforced in [receiveLoop] instead.
 */
class Ic705UdpChannel(
    override val role: Ic705ChannelRole,
    private val localAddress: InetSocketAddress = InetSocketAddress(0),
    private val socketFactory: Ic705DatagramSocketFactory = defaultSocketFactory,
    private val onDatagram: (Ic705ReceivedDatagram) -> Unit,
    private val onError: (IOException) -> Unit = {},
) : Ic705DatagramChannel {
    private val lock = Any()

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var receiveThread: Thread? = null

    @Volatile
    private var configuredRemoteEndpoint: InetSocketAddress? = null

    @Volatile
    private var remoteSourceLocked = false

    override val isOpen: Boolean
        get() = socket?.isClosed == false

    override val localPort: Int
        get() = socket?.localPort ?: 0

    override val boundLocalAddress: InetAddress?
        get() = socket?.localAddress

    override val remoteEndpoint: InetSocketAddress?
        get() = configuredRemoteEndpoint

    override fun open() {
        synchronized(lock) {
            check(socket == null) { "$role UDP channel is already open" }
            val createdSocket = socketFactory.create(localAddress)
            try {
                check(createdSocket.isBound) { "Socket factory must return a bound socket" }
                socket = createdSocket
                receiveThread = Thread(
                    { receiveLoop(createdSocket) },
                    "IC-705 ${role.name.lowercase()} UDP receive",
                ).apply {
                    isDaemon = true
                    start()
                }
            } catch (error: Exception) {
                createdSocket.close()
                throw error
            }
        }
    }

    /** Changes the send target and, when requested, the accepted source endpoint. */
    override fun setRemoteEndpoint(endpoint: InetSocketAddress?, lockSource: Boolean) {
        synchronized(lock) {
            configuredRemoteEndpoint = endpoint
            remoteSourceLocked = endpoint != null && lockSource
        }
    }

    @Throws(IOException::class)
    override fun send(data: ByteArray) {
        val currentSocket: DatagramSocket
        synchronized(lock) {
            currentSocket = checkNotNull(socket) { "$role UDP channel is not open" }
            check(configuredRemoteEndpoint != null) { "$role remote endpoint is not configured" }
        }
        val immutablePayload = data.copyOf()
        val target = checkNotNull(configuredRemoteEndpoint)
        val packet = DatagramPacket(immutablePayload, immutablePayload.size, target)
        currentSocket.send(packet)
    }

    override fun close() {
        val threadToJoin: Thread?
        synchronized(lock) {
            val currentSocket = socket
            socket = null
            currentSocket?.close()
            threadToJoin = receiveThread
            receiveThread = null
        }
        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            threadToJoin.join(RECEIVE_THREAD_JOIN_MILLIS)
        }
    }

    private fun receiveLoop(ownedSocket: DatagramSocket) {
        val buffer = ByteArray(MAX_UDP_DATAGRAM_SIZE)
        try {
            while (isCurrentSocket(ownedSocket)) {
                val packet = DatagramPacket(buffer, buffer.size)
                ownedSocket.receive(packet)
                val source = packet.socketAddress as? InetSocketAddress ?: continue
                // CI-V and audio sockets are opened before the control channel has
                // negotiated their radio endpoints. Ignore traffic until then.
                val expectedSource = configuredRemoteEndpoint ?: continue
                if (remoteSourceLocked && source != expectedSource) continue
                try {
                    onDatagram(
                        Ic705ReceivedDatagram(
                            data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
                            source = source,
                        ),
                    )
                } catch (error: RuntimeException) {
                    onError(IOException("$role datagram callback failed", error))
                }
            }
        } catch (error: IOException) {
            if (isCurrentSocket(ownedSocket)) onError(error)
        } finally {
            ownedSocket.close()
            synchronized(lock) {
                if (socket === ownedSocket) socket = null
                if (receiveThread === Thread.currentThread()) receiveThread = null
            }
        }
    }

    private fun isCurrentSocket(candidate: DatagramSocket): Boolean =
        socket === candidate && !candidate.isClosed

    private companion object {
        const val MAX_UDP_DATAGRAM_SIZE = 65_535
        const val RECEIVE_THREAD_JOIN_MILLIS = 2_000L
    }
}
