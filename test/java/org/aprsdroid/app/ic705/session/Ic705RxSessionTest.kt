package org.aprsdroid.app.ic705.session

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.aprsdroid.app.audio.PcmEncoding
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705ConnectionInfoCodec
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacket
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705HandshakeCodec
import org.aprsdroid.app.ic705.protocol.Ic705PingPacket
import org.aprsdroid.app.ic705.protocol.Ic705WireByteOrder
import org.aprsdroid.app.ic705.transport.Ic705ChannelRole
import org.aprsdroid.app.ic705.transport.Ic705DatagramChannel
import org.aprsdroid.app.ic705.transport.Ic705DatagramChannelFactory
import org.aprsdroid.app.ic705.transport.Ic705ReceivedDatagram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.ic705.protocol.Ic705CivDatagram
import org.aprsdroid.app.ic705.protocol.Ic705CivDatagramCodec
import sivantoledo.ax25.Packet
import org.junit.Test

class Ic705RxSessionTest {
    @Test
    fun transmitSendsPttOnAndStreamsAudioConcurrently() {
        val harness = SessionHarness()
        try {
            harness.advanceToReceiving()
            val civChannel = harness.factory.channel(Ic705ChannelRole.CIV)
            val audioChannel = harness.factory.channel(Ic705ChannelRole.AUDIO)

            val testAx25 = Packet(
                "APRS",
                "N0CALL",
                arrayOf("WIDE1-1"),
                Packet.AX25_CONTROL_APRS,
                Packet.AX25_PROTOCOL_NO_LAYER_3,
                "!4903.50N/07201.75W-Test APRS frame".toByteArray(Charsets.ISO_8859_1),
            )
            val packet = Parser.parseAX25(testAx25.bytesWithoutCRC())
            val started = harness.session.transmit(packet)
            assertTrue(started)
            assertTrue(harness.session.isTransmitting)

            // Verify CI-V frame was sent (PTT ON)
            val civSent = civChannel.sentPackets.filter {
                it.size > Ic705CivDatagramCodec.HEADER_SIZE &&
                    (it[0x10].toInt() and 0xff) == Ic705CivDatagramCodec.CIV_MARKER
            }
            assertTrue(civSent.isNotEmpty())

            // Wait a moment for audio streaming
            Thread.sleep(150)

            val audioSent = audioChannel.sentPackets.filter { looksLikeAudioPayload(it) }
            assertTrue(audioSent.isNotEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun defaultControlTimingMatchesSuccessfulRsBa1Cadence() {
        val timing = Ic705RxSessionTiming()
        assertEquals(100L, timing.pingPeriodMillis)
        assertEquals(100L, timing.idleCheckPeriodMillis)
        assertEquals(100L, timing.idleAfterMillis)
        assertEquals(3_000L, timing.connectionInfoSettleMillis)
        assertFalse(shouldSendIc705TrackedIdle(99L, 100L))
        assertTrue(shouldSendIc705TrackedIdle(100L, 100L))
    }

    @Test
    fun clientIdsMatchWfviewEndpointLayoutWithPortableFallback() {
        assertEquals(
            0x0117A029,
            ic705ClientIdForEndpoint(
                InetAddress.getByName("192.168.1.23"),
                41_001,
            ),
        )
        assertEquals(
            0x0001A029,
            ic705ClientIdForEndpoint(null, 41_001),
        )
        assertEquals(
            0x0001A029,
            ic705ClientIdForEndpoint(
                InetAddress.getByName("0.0.0.0"),
                41_001,
            ),
        )
    }

    @Test
    fun rigplaneDiagnosticProfileUsesItsExactInitialControlEnvelope() {
        val harness = SessionHarness(wireProfile = Ic705RxWireProfile.RIGPLANE_DIAGNOSTIC)
        try {
            harness.session.start()
            harness.drain()
            val control = harness.factory.channel(Ic705ChannelRole.CONTROL)
            control.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_I_AM_HERE,
                        sequence = 0,
                        senderId = RADIO_ID,
                        receiverId = RIGPLANE_CONTROL_LOCAL_ID,
                    ),
                ),
            )
            harness.drain()
            control.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_I_AM_HERE,
                        sequence = 0,
                        senderId = RADIO_ID,
                        receiverId = RIGPLANE_CONTROL_LOCAL_ID,
                    ),
                ),
            )
            harness.drain()
            val ready = control.sentPackets.single {
                it.size == Ic705ControlPacketCodec.PACKET_SIZE &&
                    Ic705WireByteOrder.readUInt16Le(it, 0x04) == Ic705ControlPacketCodec.TYPE_READY
            }
            assertEquals(0, Ic705WireByteOrder.readUInt16Le(ready, 0x06))
            control.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_READY,
                        sequence = 1,
                        senderId = RADIO_ID,
                        receiverId = RIGPLANE_CONTROL_LOCAL_ID,
                    ),
                ),
            )
            harness.drain()

            val login = control.sentPackets.single {
                it.size == Ic705HandshakeCodec.LOGIN_REQUEST_PACKET_SIZE
            }
            assertEquals(0, Ic705WireByteOrder.readUInt16Le(login, 0x06))
            assertEquals(RIGPLANE_CONTROL_LOCAL_ID, Ic705WireByteOrder.readInt32Le(login, 0x08))
            assertEquals(0, Ic705WireByteOrder.readUInt16Be(login, 0x16))
            assertEquals(0, Ic705WireByteOrder.readUInt16Be(login, 0x1a))

            control.emit(
                loginAccepted(
                    senderId = RADIO_ID,
                    receiverId = RIGPLANE_CONTROL_LOCAL_ID,
                    tokenRequest = 0,
                    token = RADIO_TOKEN,
                ),
            )
            harness.drain()
            val tokenConfirm = control.sentPackets.single {
                it.size == Ic705HandshakeCodec.TOKEN_PACKET_SIZE &&
                    (it[0x15].toInt() and 0xff) == Ic705HandshakeCodec.TOKEN_REQUEST_CONFIRM
            }
            assertEquals(1, Ic705WireByteOrder.readUInt16Le(tokenConfirm, 0x06))
            assertEquals(0, Ic705WireByteOrder.readUInt16Be(tokenConfirm, 0x16))
        } finally {
            harness.close()
        }
    }

    @Test
    fun settledConnectionInfoAdvertisesRequiredWireCapabilitiesAndNeverWritesAudioOrPtt() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()

            assertEquals(50_001, harness.factory.requestedLocalAddress(Ic705ChannelRole.CONTROL).port)
            assertEquals(
                RADIO_CIV_PORT,
                harness.factory.requestedLocalAddress(Ic705ChannelRole.CIV).port,
            )
            assertEquals(
                RADIO_AUDIO_PORT,
                harness.factory.requestedLocalAddress(Ic705ChannelRole.AUDIO).port,
            )

            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ),
            )
            harness.drain()
            assertTrue(control.sentPackets.none { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE })
            harness.awaitConnectionInfoCount(1)

            val request = control.sentPackets.single {
                it.size == Ic705ConnectionInfoCodec.PACKET_SIZE
            }
            assertEquals(Ic705HandshakeCodec.REQUEST_REPLY_REQUEST, request[0x14].toInt() and 0xff)
            assertEquals(CONNECTION_REQUEST_TYPE, request[0x15].toInt() and 0xff)
            assertEquals(1, request[0x70].toInt() and 0xff)
            assertEquals(1, request[0x71].toInt() and 0xff)
            assertFalse(harness.factory.allSentPackets().any(::looksLikeAudioPayload))
            assertTrue(harness.factory.channel(Ic705ChannelRole.CIV).sentPackets.isEmpty())
            assertTrue(harness.factory.channel(Ic705ChannelRole.AUDIO).sentPackets.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun busyConnectionAnnouncementDoesNotAuthorizeOrAdvanceNegotiation() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = true,
                ),
            )
            harness.drain()

            assertEquals(Ic705RxSessionEngine.Phase.NEGOTIATING, harness.session.state.phase)
            assertFalse(harness.session.state.connectionRequestAuthorized)
            assertFalse(harness.session.state.connectionInfoReceived)
            assertTrue(control.sentPackets.none { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE })

            control.emit(
                tokenRenewalAuthorization(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    tokenRequest = RADIO_TOKEN_REQUEST,
                    token = RADIO_TOKEN,
                ),
            )
            harness.drain()
            assertTrue(control.sentPackets.none { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE })

            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ),
            )
            harness.awaitConnectionInfoCount(1)

            val request = control.sentPackets.single {
                it.size == Ic705ConnectionInfoCodec.PACKET_SIZE
            }
            assertEquals(1, request[0x71].toInt() and 0xff)
        } finally {
            harness.close()
        }
    }

    @Test
    fun ownBusyAnnouncementAfterConninfoOpensDefaultStreamsWithoutStatusPacket() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ),
            )
            harness.awaitConnectionInfoCount(1)

            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = true,
                    busyClientName = "APRSdroid",
                ),
            )
            harness.drain()

            assertEquals(Ic705RxSessionEngine.Phase.OPENING_STREAMS, harness.session.state.phase)
            assertEquals(
                InetSocketAddress(RADIO_ADDRESS.address, RADIO_CIV_PORT),
                harness.factory.channel(Ic705ChannelRole.CIV).remoteEndpoint,
            )
            assertEquals(
                InetSocketAddress(RADIO_ADDRESS.address, RADIO_AUDIO_PORT),
                harness.factory.channel(Ic705ChannelRole.AUDIO).remoteEndpoint,
            )
            assertTrue(harness.factory.channel(Ic705ChannelRole.CIV).sentPackets.isNotEmpty())
            assertTrue(harness.factory.channel(Ic705ChannelRole.AUDIO).sentPackets.isNotEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun realRadioZeroLengthPingIsAcceptedAndRepliedTo() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            val sentBefore = control.sentPackets.size
            val radioPing = Ic705HandshakeCodec.encodePing(
                Ic705PingPacket(
                    sequence = 7,
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    isReply = false,
                    timestampBits = 0x12345678,
                ),
            ).apply {
                fill(0, fromIndex = 0, toIndex = 4)
            }

            control.emit(radioPing)
            harness.drain()

            assertTrue(harness.issues.isEmpty())
            val reply = control.sentPackets.drop(sentBefore).single {
                it.size == Ic705HandshakeCodec.PING_PACKET_SIZE
            }
            assertEquals(Ic705HandshakeCodec.PING_PACKET_SIZE, Ic705WireByteOrder.readInt32Le(reply, 0))
            assertEquals(7, Ic705WireByteOrder.readUInt16Le(reply, 6))
            assertEquals(1, reply[0x10].toInt() and 0xff)
            assertTrue(reply.copyOfRange(0x11, 0x15).contentEquals(radioPing.copyOfRange(0x11, 0x15)))
        } finally {
            harness.close()
        }
    }

    @Test
    fun realRadioConnectionAnnouncementMarkerIsAccepted() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ).apply {
                    // Observed on the real IC-705: the 0x90 announcement uses 0x03/0x00
                    // here, rather than the generic request/response markers.
                    this[0x14] = 0x03
                    this[0x15] = 0x00
                    Ic705WireByteOrder.writeUInt16Le(this, 0x04, 0)
                },
            )
            harness.drain()

            assertTrue(harness.issues.isEmpty())
            assertTrue(harness.session.state.connectionRequestAuthorized)
            assertTrue(harness.session.state.connectionInfoReceived)
            assertEquals(
                0,
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE },
            )
            harness.awaitConnectionInfoCount(1)
            assertEquals(
                1,
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE },
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun duplicateRealRadioConnectionAnnouncementsAreCoalescedBeforeStatus() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            val announcement = connectionAnnouncement(
                senderId = RADIO_ID,
                receiverId = CONTROL_LOCAL_ID,
                busy = false,
            ).apply {
                this[0x14] = 0x03
                this[0x15] = 0x00
                Ic705WireByteOrder.writeUInt16Le(this, 0x04, 0)
            }

            control.emit(announcement)
            control.emit(announcement)
            harness.drain()
            assertEquals(
                0,
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE },
            )
            harness.awaitConnectionInfoCount(1)

            assertTrue(harness.issues.isEmpty())
            assertEquals(
                1,
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE },
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun explicitZeroPortStatusRemainsNegotiatingWithoutMalformedPacketIssue() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceThroughTokenConfirmation()
            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ),
            )
            harness.awaitConnectionInfoCount(1)

            control.emit(
                connectedStatus(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    civPort = 0,
                    audioPort = 0,
                ),
            )
            harness.drain()

            assertEquals(Ic705RxSessionEngine.Phase.NEGOTIATING, harness.session.state.phase)
            assertEquals("radio session not ready", harness.session.state.failureReason)
            assertTrue(harness.issues.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun rejectedRenewalAfterFirstAudioClosesEveryChannelAndFailsWithoutReconnect() {
        val harness = SessionHarness()
        try {
            val control = harness.advanceToReceiving()
            val openedChannels = harness.factory.allChannels()
            assertEquals(Ic705RxSessionEngine.Phase.RECEIVING, harness.session.state.phase)
            assertEquals(3, openedChannels.size)
            assertTrue(openedChannels.all { it.isOpen })

            control.emit(
                tokenRenewalAuthorization(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    tokenRequest = RADIO_TOKEN_REQUEST,
                    token = RADIO_TOKEN,
                ),
            )
            harness.drain()

            assertEquals(Ic705RxSessionEngine.Phase.FAILED, harness.session.state.phase)
            assertTrue(harness.session.state.failureReason?.contains("token") == true)
            assertTrue(openedChannels.none { it.isOpen })
        } finally {
            harness.close()
        }
    }

    @Test
    fun closeCallbackWaitsForBlockedPcmWriteAndQueuedAudioDoesNotRunAfterClose() {
        val sink = BlockingPcmSink()
        val harness = SessionHarness(audioSink = sink)
        val closed = CountDownLatch(1)
        try {
            val audio = harness.advanceToStreamsReady()
            audio.emit(audioDatagram(audioSequence = 1, pcmSample = 0x1234))
            assertTrue(sink.writeStarted.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            // This packet must remain behind the blocked write until close advances
            // the session generation and clears the old audio work queue.
            audio.emit(audioDatagram(audioSequence = 2, pcmSample = 0x5678))
            harness.session.close { closed.countDown() }

            assertFalse(closed.await(100, TimeUnit.MILLISECONDS))
            assertEquals(1, sink.writeCount.get())

            sink.releaseWrite.countDown()
            assertTrue(closed.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(harness.factory.awaitAllClosed())
            assertEquals(1, sink.writeCount.get())
        } finally {
            sink.releaseWrite.countDown()
            harness.close()
        }
    }

    private class SessionHarness(
        audioSink: PcmSink = NoOpPcmSink,
        wireProfile: Ic705RxWireProfile = Ic705RxWireProfile.WFVIEW,
    ) : AutoCloseable {
        val factory = FakeChannelFactory()
        val issues = CopyOnWriteArrayList<Ic705RxSessionIssue>()
        private val controlExecutor = ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }
        private val audioExecutor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(4),
        )
        private val randomValues = intArrayOf(
            CLIENT_TOKEN_REQUEST,
            CONTROL_LOCAL_ID,
            CIV_LOCAL_ID,
            AUDIO_LOCAL_ID,
        )
        private var randomIndex = 0

        val session = Ic705RxSession(
            config = Ic705RxSessionConfig(
                radioAddress = RADIO_ADDRESS.address,
                controlPort = RADIO_ADDRESS.port,
                username = "N0CALL",
                password = "test-password",
                autoReconnect = false,
                timing = quietTiming(),
            ),
            audioSink = audioSink,
            callbacks = Ic705RxSessionCallbacks(onIssue = issues::add),
            channelFactory = factory,
            controlExecutor = controlExecutor,
            audioExecutor = audioExecutor,
            randomInt = { randomValues[randomIndex++] },
            monotonicMillis = { 1_000L },
            wireProfile = wireProfile,
        )

        fun advanceThroughTokenConfirmation(): FakeChannel {
            session.start()
            drain()
            val control = factory.channel(Ic705ChannelRole.CONTROL)
            assertTrue(
                control.sentPackets.any {
                    it.size == Ic705ControlPacketCodec.PACKET_SIZE &&
                        Ic705WireByteOrder.readUInt16Le(it, 0x04) ==
                        Ic705ControlPacketCodec.TYPE_ARE_YOU_THERE
                },
            )
            control.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_I_AM_HERE,
                        sequence = 0,
                        senderId = RADIO_ID,
                        receiverId = CONTROL_LOCAL_ID,
                    ),
                ),
            )
            drain()
            control.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_READY,
                        sequence = 1,
                        senderId = RADIO_ID,
                        receiverId = CONTROL_LOCAL_ID,
                    ),
                ),
            )
            drain()
            assertTrue(
                control.sentPackets.any {
                    it.size == Ic705HandshakeCodec.LOGIN_REQUEST_PACKET_SIZE
                },
            )
            control.emit(
                loginAccepted(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    tokenRequest = CLIENT_TOKEN_REQUEST,
                    token = RADIO_TOKEN,
                ),
            )
            drain()
            assertTrue(
                control.sentPackets.any {
                    it.size == Ic705HandshakeCodec.TOKEN_PACKET_SIZE &&
                        (it[0x15].toInt() and 0xff) ==
                        Ic705HandshakeCodec.TOKEN_REQUEST_CONFIRM
                },
            )
            return control
        }

        fun advanceToStreamsReady(): FakeChannel {
            val control = advanceThroughTokenConfirmation()
            control.emit(
                tokenRenewalAuthorization(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    tokenRequest = RADIO_TOKEN_REQUEST,
                    token = RADIO_TOKEN,
                ),
            )
            drain()
            control.emit(
                connectionAnnouncement(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    busy = false,
                ),
            )
            awaitConnectionInfoCount(1)
            control.emit(
                connectedStatus(
                    senderId = RADIO_ID,
                    receiverId = CONTROL_LOCAL_ID,
                    civPort = RADIO_CIV_PORT,
                    audioPort = RADIO_AUDIO_PORT,
                ),
            )
            drain()

            readyStream(
                channel = factory.channel(Ic705ChannelRole.CIV),
                localId = CIV_LOCAL_ID,
                radioId = CIV_RADIO_ID,
            )
            readyStream(
                channel = factory.channel(Ic705ChannelRole.AUDIO),
                localId = AUDIO_LOCAL_ID,
                radioId = AUDIO_RADIO_ID,
            )
            assertEquals(Ic705RxSessionEngine.Phase.STREAMS_READY, session.state.phase)
            return factory.channel(Ic705ChannelRole.AUDIO)
        }

        fun advanceToReceiving(): FakeChannel {
            val audio = advanceToStreamsReady()
            audio.emit(audioDatagram(audioSequence = 1, pcmSample = 0x1234))
            drainAudio()
            drain()
            assertEquals(Ic705RxSessionEngine.Phase.RECEIVING, session.state.phase)
            return factory.channel(Ic705ChannelRole.CONTROL)
        }

        private fun readyStream(channel: FakeChannel, localId: Int, radioId: Int) {
            channel.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_I_AM_HERE,
                        sequence = 0,
                        senderId = radioId,
                        receiverId = localId,
                    ),
                ),
            )
            drain()
            channel.emit(
                Ic705ControlPacketCodec.encode(
                    Ic705ControlPacket(
                        type = Ic705ControlPacketCodec.TYPE_READY,
                        sequence = 1,
                        senderId = radioId,
                        receiverId = localId,
                    ),
                ),
            )
            drain()
        }

        fun drain() {
            controlExecutor.submit {}.get(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        fun awaitConnectionInfoCount(expectedCount: Int) {
            val control = factory.channel(Ic705ChannelRole.CONTROL)
            val deadline = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(BARRIER_TIMEOUT_SECONDS)
            while (
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE } !=
                expectedCount &&
                System.nanoTime() < deadline
            ) {
                Thread.sleep(5)
                drain()
            }
            assertEquals(
                expectedCount,
                control.sentPackets.count { it.size == Ic705ConnectionInfoCodec.PACKET_SIZE },
            )
        }

        private fun drainAudio() {
            audioExecutor.submit {}.get(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        override fun close() {
            session.close()
            controlExecutor.shutdown()
            if (!controlExecutor.awaitTermination(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                controlExecutor.shutdownNow()
            }
            audioExecutor.shutdownNow()
        }
    }

    private class FakeChannelFactory : Ic705DatagramChannelFactory {
        private val channels = ConcurrentHashMap<Ic705ChannelRole, FakeChannel>()
        private val requestedLocalAddresses =
            ConcurrentHashMap<Ic705ChannelRole, InetSocketAddress>()

        override fun create(
            role: Ic705ChannelRole,
            localAddress: InetSocketAddress,
            onDatagram: (Ic705ReceivedDatagram) -> Unit,
            onError: (IOException) -> Unit,
        ): Ic705DatagramChannel = FakeChannel(
            role = role,
            onDatagram = onDatagram,
            localPort = when (role) {
                Ic705ChannelRole.CONTROL -> 41_001
                Ic705ChannelRole.CIV -> 41_002
                Ic705ChannelRole.AUDIO -> 41_003
            },
        ).also {
            requestedLocalAddresses[role] = localAddress
            channels[role] = it
        }

        fun channel(role: Ic705ChannelRole): FakeChannel =
            checkNotNull(channels[role]) { "$role channel was not created" }

        fun requestedLocalAddress(role: Ic705ChannelRole): InetSocketAddress =
            checkNotNull(requestedLocalAddresses[role]) { "$role channel was not requested" }

        fun allSentPackets(): List<ByteArray> = channels.values.flatMap { channel ->
            channel.sentPackets.map(ByteArray::copyOf)
        }

        fun allChannels(): List<FakeChannel> = channels.values.toList()

        fun awaitAllClosed(): Boolean {
            val deadline = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(BARRIER_TIMEOUT_SECONDS)
            while (channels.values.any { it.isOpen } && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }
            return channels.values.none { it.isOpen }
        }
    }

    private class FakeChannel(
        override val role: Ic705ChannelRole,
        private val onDatagram: (Ic705ReceivedDatagram) -> Unit,
        override val localPort: Int,
    ) : Ic705DatagramChannel {
        override val boundLocalAddress: InetAddress = InetAddress.getByName("192.168.1.23")
        val sentPackets = CopyOnWriteArrayList<ByteArray>()

        @Volatile
        override var isOpen: Boolean = false
            private set

        @Volatile
        override var remoteEndpoint: InetSocketAddress? = null
            private set

        override fun open() {
            isOpen = true
        }

        override fun setRemoteEndpoint(endpoint: InetSocketAddress?, lockSource: Boolean) {
            remoteEndpoint = endpoint
        }

        override fun send(data: ByteArray) {
            check(isOpen)
            sentPackets += data.copyOf()
        }

        fun emit(data: ByteArray) {
            check(isOpen)
            onDatagram(
                Ic705ReceivedDatagram(
                    data = data.copyOf(),
                    source = RADIO_ADDRESS,
                ),
            )
        }

        override fun close() {
            isOpen = false
        }
    }

    private object NoOpPcmSink : PcmSink {
        override val format = PcmFormat(
            sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ,
            channelCount = 1,
            encoding = PcmEncoding.PCM_16_LE,
        )

        override fun write(buffer: ShortArray, offset: Int, length: Int) = Unit
        override fun close() = Unit
    }

    private class BlockingPcmSink : PcmSink {
        override val format = NoOpPcmSink.format
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writeCount = AtomicInteger()

        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            writeCount.incrementAndGet()
            writeStarted.countDown()
            var released = false
            while (!released) {
                try {
                    releaseWrite.await()
                    released = true
                } catch (_: InterruptedException) {
                    // close() interrupts the audio executor, but its completion callback
                    // must still wait until the sink has actually released this write.
                }
            }
        }

        override fun close() = Unit
    }

    private companion object {
        const val CONNECTION_REQUEST_TYPE = 0x03
        const val CLIENT_TOKEN_REQUEST = 0x1234
        const val RADIO_TOKEN_REQUEST = 0x4567
        const val RADIO_TOKEN = 0x23456789
        const val CONTROL_LOCAL_ID = 0x0117A029
        const val RIGPLANE_CONTROL_LOCAL_ID = 0x0001A029
        const val CIV_LOCAL_ID = 0x0117A02A
        const val AUDIO_LOCAL_ID = 0x0117A02B
        const val RADIO_ID = 0x33445566
        const val CIV_RADIO_ID = 0x44556677
        const val AUDIO_RADIO_ID = 0x55667711
        const val RADIO_CIV_PORT = 50_002
        const val RADIO_AUDIO_PORT = 50_003
        const val BARRIER_TIMEOUT_SECONDS = 2L

        val RADIO_ADDRESS = InetSocketAddress(InetAddress.getByName("192.0.2.1"), 50_001)

        fun quietTiming() = Ic705RxSessionTiming(
            discoveryPeriodMillis = 30_000L,
            discoveryTimeoutMillis = 30_000L,
            pingPeriodMillis = 30_000L,
            idleCheckPeriodMillis = 30_000L,
            idleAfterMillis = 30_000L,
            tokenRenewalMillis = 30_000L,
            watchdogPeriodMillis = 30_000L,
            channelTimeoutMillis = 30_000L,
            handshakeStageTimeoutMillis = 30_000L,
            negotiationTimeoutMillis = 30_000L,
            connectionInfoSettleMillis = 100L,
            connectionInfoRetryMillis = 30_000L,
            initialReconnectMillis = 30_000L,
            maximumReconnectMillis = 30_000L,
        )

        fun loginAccepted(
            senderId: Int,
            receiverId: Int,
            tokenRequest: Int,
            token: Int,
        ): ByteArray = authenticatedPacket(
            size = Ic705HandshakeCodec.LOGIN_RESPONSE_PACKET_SIZE,
            senderId = senderId,
            receiverId = receiverId,
            requestType = 0,
            tokenRequest = tokenRequest,
            token = token,
        )

        fun tokenRenewalAuthorization(
            senderId: Int,
            receiverId: Int,
            tokenRequest: Int,
            token: Int,
        ): ByteArray = authenticatedPacket(
            size = Ic705HandshakeCodec.TOKEN_PACKET_SIZE,
            senderId = senderId,
            receiverId = receiverId,
            requestType = Ic705HandshakeCodec.TOKEN_REQUEST_RENEWAL,
            tokenRequest = tokenRequest,
            token = token,
        ).apply {
            Ic705WireByteOrder.writeInt32Be(this, 0x30, -1)
        }

        fun connectionAnnouncement(
            senderId: Int,
            receiverId: Int,
            busy: Boolean,
            busyClientName: String? = null,
        ): ByteArray = authenticatedPacket(
            size = Ic705ConnectionInfoCodec.PACKET_SIZE,
            senderId = senderId,
            receiverId = receiverId,
            requestType = CONNECTION_REQUEST_TYPE,
            tokenRequest = RADIO_TOKEN_REQUEST,
            token = RADIO_TOKEN,
        ).apply {
            this[0x60] = if (busy) 1 else 0
            if (busyClientName != null) {
                busyClientName.toByteArray().copyInto(this, destinationOffset = 0x64)
            }
        }

        fun connectedStatus(
            senderId: Int,
            receiverId: Int,
            civPort: Int,
            audioPort: Int,
        ): ByteArray = authenticatedPacket(
            size = Ic705HandshakeCodec.STATUS_PACKET_SIZE,
            senderId = senderId,
            receiverId = receiverId,
            requestType = 0,
            tokenRequest = RADIO_TOKEN_REQUEST,
            token = RADIO_TOKEN,
        ).apply {
            Ic705WireByteOrder.writeInt32Be(this, 0x30, 0)
            this[0x40] = 0
            Ic705WireByteOrder.writeUInt16Be(this, 0x42, civPort)
            Ic705WireByteOrder.writeUInt16Be(this, 0x46, audioPort)
        }

        fun audioDatagram(audioSequence: Int, pcmSample: Int): ByteArray =
            Ic705AudioPacketCodec.encode(
                sequence = audioSequence,
                senderId = AUDIO_RADIO_ID,
                receiverId = AUDIO_LOCAL_ID,
                audioSequence = audioSequence,
                pcmPayload = byteArrayOf(
                    (pcmSample and 0xff).toByte(),
                    ((pcmSample ushr 8) and 0xff).toByte(),
                ),
            )

        fun authenticatedPacket(
            size: Int,
            senderId: Int,
            receiverId: Int,
            requestType: Int,
            tokenRequest: Int,
            token: Int,
        ): ByteArray = ByteArray(size).apply {
            Ic705WireByteOrder.writeInt32Le(this, 0x00, size)
            Ic705WireByteOrder.writeUInt16Le(this, 0x04, Ic705HandshakeCodec.RESPONSE_TYPE)
            Ic705WireByteOrder.writeUInt16Le(this, 0x06, 1)
            Ic705WireByteOrder.writeInt32Le(this, 0x08, senderId)
            Ic705WireByteOrder.writeInt32Le(this, 0x0c, receiverId)
            Ic705WireByteOrder.writeUInt16Be(
                this,
                0x12,
                size - Ic705ControlPacketCodec.PACKET_SIZE,
            )
            this[0x14] = Ic705HandshakeCodec.REQUEST_REPLY_RESPONSE.toByte()
            this[0x15] = requestType.toByte()
            Ic705WireByteOrder.writeUInt16Be(this, 0x16, 1)
            Ic705WireByteOrder.writeUInt16Be(this, 0x1a, tokenRequest)
            Ic705WireByteOrder.writeInt32Be(this, 0x1c, token)
        }

        fun looksLikeAudioPayload(data: ByteArray): Boolean =
            data.size > Ic705AudioPacketCodec.HEADER_SIZE &&
                runCatching {
                    Ic705WireByteOrder.readInt32Le(data, 0x00) == data.size &&
                        Ic705WireByteOrder.readUInt16Be(data, 0x16) ==
                        data.size - Ic705AudioPacketCodec.HEADER_SIZE
                }.getOrDefault(false)
    }
}
