package org.aprsdroid.app.ic705.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705UdpChannelTest {
    @Test
    fun sendsAndReceivesThroughOneConnectedEndpoint() {
        val loopback = InetAddress.getLoopbackAddress()
        val peer = DatagramSocket(InetSocketAddress(loopback, 0))
        peer.soTimeout = 2_000
        val received = AtomicReference<Ic705ReceivedDatagram>()
        val receivedLatch = CountDownLatch(1)
        val channel = Ic705UdpChannel(
            role = Ic705ChannelRole.AUDIO,
            localAddress = InetSocketAddress(loopback, 0),
            onDatagram = {
                received.set(it)
                receivedLatch.countDown()
            },
        )

        try {
            channel.setRemoteEndpoint(peer.localSocketAddress as InetSocketAddress)
            channel.open()
            assertTrue(channel.isOpen)

            val outbound = byteArrayOf(0x01, 0x02, 0x03)
            channel.send(outbound)
            val peerBuffer = ByteArray(32)
            val peerPacket = DatagramPacket(peerBuffer, peerBuffer.size)
            peer.receive(peerPacket)
            assertArrayEquals(outbound, peerPacket.data.copyOf(peerPacket.length))

            val inbound = byteArrayOf(0x11, 0x12)
            peer.send(
                DatagramPacket(
                    inbound,
                    inbound.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )
            assertTrue(receivedLatch.await(2, TimeUnit.SECONDS))
            assertArrayEquals(inbound, received.get().data)
            assertEquals(peer.localPort, received.get().source.port)
        } finally {
            channel.close()
            peer.close()
        }

        assertFalse(channel.isOpen)
    }

    @Test
    fun unconnectedDiscoveryAcceptsUnicastReplyBeforeLockingItsSource() {
        val loopback = InetAddress.getLoopbackAddress()
        val discoveryTarget = DatagramSocket(InetSocketAddress(loopback, 0)).apply {
            soTimeout = 2_000
        }
        val replySource = DatagramSocket(InetSocketAddress(loopback, 0)).apply {
            soTimeout = 2_000
        }
        val received = mutableListOf<Ic705ReceivedDatagram>()
        val firstReplyReceived = CountDownLatch(1)
        val expectedReplies = CountDownLatch(2)
        val channel = Ic705UdpChannel(
            role = Ic705ChannelRole.CONTROL,
            localAddress = InetSocketAddress(loopback, 0),
            onDatagram = {
                synchronized(received) { received += it }
                firstReplyReceived.countDown()
                expectedReplies.countDown()
            },
        )

        try {
            channel.setRemoteEndpoint(
                discoveryTarget.localSocketAddress as InetSocketAddress,
                lockSource = false,
            )
            channel.open()

            val discovery = byteArrayOf(0x21, 0x22)
            channel.send(discovery)
            val discoveryBuffer = ByteArray(32)
            val discoveryPacket = DatagramPacket(discoveryBuffer, discoveryBuffer.size)
            discoveryTarget.receive(discoveryPacket)
            assertArrayEquals(discovery, discoveryPacket.data.copyOf(discoveryPacket.length))

            val unicastReply = byteArrayOf(0x31, 0x32)
            replySource.send(
                DatagramPacket(
                    unicastReply,
                    unicastReply.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )

            assertTrue(firstReplyReceived.await(2, TimeUnit.SECONDS))
            val discoveredSource = synchronized(received) { received.single().source }
            assertEquals(replySource.localPort, discoveredSource.port)

            channel.setRemoteEndpoint(discoveredSource, lockSource = true)
            assertEquals(discoveredSource, channel.remoteEndpoint)

            val wrongSource = byteArrayOf(0x41)
            discoveryTarget.send(
                DatagramPacket(
                    wrongSource,
                    wrongSource.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )
            val lockedReply = byteArrayOf(0x51)
            replySource.send(
                DatagramPacket(
                    lockedReply,
                    lockedReply.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )
            assertTrue(expectedReplies.await(2, TimeUnit.SECONDS))

            val receivedPayloads = synchronized(received) { received.map { it.data } }
            assertEquals(2, receivedPayloads.size)
            assertArrayEquals(unicastReply, receivedPayloads[0])
            assertArrayEquals(lockedReply, receivedPayloads[1])

            val lockedOutbound = byteArrayOf(0x61, 0x62)
            channel.send(lockedOutbound)
            val lockedBuffer = ByteArray(32)
            val lockedPacket = DatagramPacket(lockedBuffer, lockedBuffer.size)
            replySource.receive(lockedPacket)
            assertArrayEquals(lockedOutbound, lockedPacket.data.copyOf(lockedPacket.length))
        } finally {
            channel.close()
            discoveryTarget.close()
            replySource.close()
        }
    }

    @Test
    fun dropsInboundDatagramUntilRemoteEndpointIsConfigured() {
        val loopback = InetAddress.getLoopbackAddress()
        val secondReceiveStarted = CountDownLatch(1)
        val sender = DatagramSocket(InetSocketAddress(loopback, 0))
        val accepted = AtomicReference<Ic705ReceivedDatagram>()
        val acceptedLatch = CountDownLatch(1)
        val channel = Ic705UdpChannel(
            role = Ic705ChannelRole.CIV,
            localAddress = InetSocketAddress(loopback, 0),
            socketFactory = Ic705DatagramSocketFactory { localAddress ->
                ReceiveObservingDatagramSocket(localAddress, secondReceiveStarted)
            },
            onDatagram = {
                accepted.set(it)
                acceptedLatch.countDown()
            },
        )

        try {
            channel.open()
            val ignored = byteArrayOf(0x71)
            sender.send(
                DatagramPacket(
                    ignored,
                    ignored.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )
            assertTrue(secondReceiveStarted.await(2, TimeUnit.SECONDS))
            assertEquals(1L, acceptedLatch.count)

            val senderEndpoint = sender.localSocketAddress as InetSocketAddress
            channel.setRemoteEndpoint(senderEndpoint, lockSource = false)
            val delivered = byteArrayOf(0x72)
            sender.send(
                DatagramPacket(
                    delivered,
                    delivered.size,
                    InetSocketAddress(loopback, channel.localPort),
                ),
            )
            assertTrue(acceptedLatch.await(2, TimeUnit.SECONDS))
            assertArrayEquals(delivered, accepted.get().data)
        } finally {
            channel.close()
            sender.close()
        }
    }

    @Test
    fun closeIsIdempotentAndUnblocksReceive() {
        val channel = Ic705UdpChannel(
            role = Ic705ChannelRole.CONTROL,
            localAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            onDatagram = {},
        )
        channel.open()

        channel.close()
        channel.close()

        assertFalse(channel.isOpen)
    }

    private class ReceiveObservingDatagramSocket(
        localAddress: InetSocketAddress,
        private val secondReceiveStarted: CountDownLatch,
    ) : DatagramSocket(null as SocketAddress?) {
        private val receiveCount = AtomicInteger()

        init {
            bind(localAddress)
        }

        override fun receive(packet: DatagramPacket) {
            if (receiveCount.incrementAndGet() == 2) secondReceiveStarted.countDown()
            super.receive(packet)
        }
    }
}
