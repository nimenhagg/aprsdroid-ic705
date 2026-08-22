package org.aprsdroid.app.ic705.session

import java.net.InetAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705HandshakeCodec
import org.aprsdroid.app.ic705.protocol.Ic705WireByteOrder
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in receive-only probe for a real IC-705 on the local network.
 *
 * This test is skipped unless IC705_HW_TEST=1. Its output is intentionally limited to
 * protocol phases, credential-safe packet diagnostics, and counters. It never prints
 * credentials, tokens, IDs, endpoints, addresses, or datagram/sample contents.
 */
class Ic705RxHardwareProbeTest {
    @Test
    fun receivesPcmFromRealRadioOverStandardUdpTransport() {
        assumeTrue(
            "Set IC705_HW_TEST=1 to run the receive-only IC-705 hardware probe",
            System.getenv(ENABLE_ENV) == "1",
        )

        val host = requiredEnvironment(HOST_ENV)
        val radioAddress = InetAddress.getByName(host)
        val username = requiredEnvironment(USER_ENV)
        val password = requiredEnvironment(PASSWORD_ENV)
        val wireProfile = when (System.getenv(WIRE_PROFILE_ENV)?.lowercase()) {
            null, "", "wfview" -> Ic705RxWireProfile.WFVIEW
            "rigplane" -> Ic705RxWireProfile.RIGPLANE_DIAGNOSTIC
            else -> error("$WIRE_PROFILE_ENV must be wfview or rigplane")
        }
        val durationSeconds = System.getenv(SECONDS_ENV)
            ?.toLongOrNull()
            ?.also { require(it in MIN_SECONDS..MAX_SECONDS) {
                "$SECONDS_ENV must be between $MIN_SECONDS and $MAX_SECONDS"
            } }
            ?: DEFAULT_SECONDS
        val wireTraceEnabled = System.getenv(WIRE_TRACE_ENV) == "1"
        val wireTraceStartedNanos = System.nanoTime()

        val pcmBlocks = AtomicLong()
        val pcmSamples = AtomicLong()
        val audioResets = AtomicLong()
        val issues = AtomicLong()
        val phaseChanges = AtomicInteger()
        val finalPhase = AtomicReference(Ic705RxSessionEngine.Phase.STOPPED)
        val closed = CountDownLatch(1)
        val socketSequence = AtomicInteger()
        val packetSignatures = ConcurrentHashMap<String, AtomicInteger>()
        val discoveredControlId = AtomicReference<Int?>(null)
        val loginLocalId = AtomicReference<Int?>(null)
        val loginTokenRequest = AtomicReference<Int?>(null)
        val loginToken = AtomicReference<Int?>(null)
        val capabilityIdentity = AtomicReference<ByteArray?>(null)
        val capabilityName = AtomicReference<ByteArray?>(null)
        val announcementIdentity = AtomicReference<ByteArray?>(null)
        val announcementName = AtomicReference<ByteArray?>(null)

        fun tracePacket(
            direction: String,
            socketNumber: Int,
            packet: DatagramPacket,
        ) {
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            if (wireTraceEnabled && socketNumber == 1) {
                val elapsedMillis = (System.nanoTime() - wireTraceStartedNanos) / 1_000_000L
                val hex = data.joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                println("IC705_WIRE t=$elapsedMillis direction=$direction hex=$hex")
            }
            val declaredLength = if (data.size >= 4) Ic705WireByteOrder.readInt32Le(data, 0) else null
            val commonType = if (data.size >= 6) Ic705WireByteOrder.readUInt16Le(data, 4) else null
            val outerSequence = if (data.size >= 8) Ic705WireByteOrder.readUInt16Le(data, 6) else null
            val senderId = if (data.size >= 0x0c) Ic705WireByteOrder.readInt32Le(data, 0x08) else null
            val receiverId = if (data.size >= 0x10) Ic705WireByteOrder.readInt32Le(data, 0x0c) else null
            val hasControlSessionHeader = socketNumber == 1 && data.size >= 0x40
            val payloadLength = if (hasControlSessionHeader) {
                Ic705WireByteOrder.readUInt16Be(data, 0x12)
            } else {
                null
            }
            val requestReply = if (hasControlSessionHeader) {
                data[0x14].toInt() and 0xff
            } else {
                null
            }
            val requestType = if (hasControlSessionHeader) {
                data[0x15].toInt() and 0xff
            } else {
                null
            }
            val response = if (hasControlSessionHeader) {
                Ic705WireByteOrder.readInt32Be(data, 0x30)
            } else {
                null
            }
            val tokenRequest = if (hasControlSessionHeader) {
                Ic705WireByteOrder.readUInt16Be(data, 0x1a)
            } else {
                null
            }
            val token = if (hasControlSessionHeader) {
                Ic705WireByteOrder.readInt32Be(data, 0x1c)
            } else {
                null
            }
            if (direction == "RX" && socketNumber == 1 && data.size == 16 && commonType == 4) {
                discoveredControlId.compareAndSet(null, senderId)
            }
            if (direction == "RX" && socketNumber == 1 && data.size == 0x60) {
                loginLocalId.set(receiverId)
                loginTokenRequest.set(tokenRequest)
                loginToken.set(token)
            }
            if (direction == "RX" && socketNumber == 1 && data.size == 0xa8) {
                // The fixed capability header is 0x42 bytes; one IC-705 radio
                // capability record (0x66 bytes) follows it.
                capabilityIdentity.compareAndSet(null, data.copyOfRange(0x42, 0x52))
                capabilityName.compareAndSet(null, data.copyOfRange(0x52, 0x72))
                println(
                    "IC705_HW radioCapabilities " +
                        "commonCapability=${Ic705WireByteOrder.readUInt16Le(data, 0x49)} " +
                        "rxSampleCapability=${Ic705WireByteOrder.readUInt16Le(data, 0x95)} " +
                        "txSampleCapability=${Ic705WireByteOrder.readUInt16Le(data, 0x97)}",
                )
            }
            if (direction == "RX" && socketNumber == 1 && data.size == 0x90) {
                announcementIdentity.compareAndSet(null, data.copyOfRange(0x20, 0x40))
                announcementName.compareAndSet(null, data.copyOfRange(0x40, 0x60))
                println(
                    "IC705_HW connectionAnnouncement " +
                        "busyWord=${Ic705WireByteOrder.readInt32Le(data, 0x60)} " +
                        "identityPrefixMatchesCapability=${capabilityIdentity.get()?.contentEquals(data.copyOfRange(0x20, 0x30))} " +
                        "identityTailZero=${data.copyOfRange(0x30, 0x40).all { it == 0.toByte() }} " +
                        "nameMatchesCapability=${capabilityName.get()?.contentEquals(data.copyOfRange(0x40, 0x60))}",
                )
            }
            val senderMatchesDiscovery = discoveredControlId.get()?.let { it == senderId }
            val tokenRequestMatchesLogin = loginTokenRequest.get()?.let { it == tokenRequest }
            val tokenMatchesLogin = loginToken.get()?.let { it == token }
            val signature = listOf(
                direction,
                socketNumber,
                data.size,
                declaredLength,
                commonType,
                payloadLength,
                requestReply,
                requestType,
                response,
            ).joinToString(":")
            val count = packetSignatures.computeIfAbsent(signature) { AtomicInteger() }.incrementAndGet()
            if (count <= 3 || count % 50 == 0) {
                println(
                    "IC705_HW packet direction=$direction socket=$socketNumber count=$count " +
                        "length=${data.size} declaredLength=$declaredLength commonType=$commonType " +
                        "outerSequence=$outerSequence " +
                        "payloadLength=$payloadLength requestReply=$requestReply " +
                        "requestType=$requestType response=$response " +
                        "senderMatchesDiscovery=$senderMatchesDiscovery " +
                        "tokenRequestMatchesLogin=$tokenRequestMatchesLogin " +
                        "tokenMatchesLogin=$tokenMatchesLogin",
                )
            }
            if (direction == "RX" && commonType == 1 && data.size > 16 && count <= 3) {
                val requestedSequences = (16 until data.size step 2).map { offset ->
                    Ic705WireByteOrder.readUInt16Le(data, offset)
                }
                println("IC705_HW retransmitRequest sequences=$requestedSequences")
            }
            if (direction == "TX" && socketNumber == 1 && data.size == 0x90) {
                println(
                    "IC705_HW connectionParameters " +
                        "senderMatchesLoginLocal=${loginLocalId.get()?.let { it == senderId }} " +
                        "receiverMatchesDiscovery=${discoveredControlId.get()?.let { it == receiverId }} " +
                        "identityMatchesAnnouncement=${announcementIdentity.get()?.contentEquals(data.copyOfRange(0x20, 0x40))} " +
                        "identityPrefixMatchesCapability=${capabilityIdentity.get()?.contentEquals(data.copyOfRange(0x20, 0x30))} " +
                        "identityTailZero=${data.copyOfRange(0x30, 0x40).all { it == 0.toByte() }} " +
                        "nameMatchesAnnouncement=${announcementName.get()?.contentEquals(data.copyOfRange(0x40, 0x60))} " +
                        "nameMatchesCapability=${capabilityName.get()?.contentEquals(data.copyOfRange(0x40, 0x60))} " +
                        "usernameEncodingMatches=${Ic705HandshakeCodec.encodeCredentialPassCode(username).contentEquals(data.copyOfRange(0x60, 0x70))} " +
                        "rxEnabled=${data[0x70].toInt() and 0xff} " +
                        "txEnabled=${data[0x71].toInt() and 0xff} " +
                        "rxCodec=${data[0x72].toInt() and 0xff} " +
                        "txCodec=${data[0x73].toInt() and 0xff} " +
                        "rxRate=${Ic705WireByteOrder.readInt32Be(data, 0x74)} " +
                        "txRate=${Ic705WireByteOrder.readInt32Be(data, 0x78)} " +
                        "portsNonzero=${Ic705WireByteOrder.readInt32Be(data, 0x7c) > 0 && Ic705WireByteOrder.readInt32Be(data, 0x80) > 0} " +
                        "portsDistinct=${Ic705WireByteOrder.readInt32Be(data, 0x7c) != Ic705WireByteOrder.readInt32Be(data, 0x80)} " +
                        "txBuffer=${Ic705WireByteOrder.readInt32Be(data, 0x84)} " +
                        "convert=${data[0x88].toInt() and 0xff}",
                )
            }
            if (direction == "TX" && socketNumber == 1 && data.size == 0x40) {
                println(
                    "IC705_HW tokenRequestFields " +
                        "requestType=$requestType " +
                        "resetCapability=${Ic705WireByteOrder.readUInt16Be(data, 0x24)}",
                )
            }
        }

        val routedLocalAddress = DatagramSocket().use { routeProbe ->
            routeProbe.connect(InetSocketAddress(radioAddress, CONTROL_PORT))
            routeProbe.localAddress
        }
        println("IC705_HW routeBinding=explicit")

        val tracingSocketFactory = Ic705DatagramSocketFactory { localAddress ->
            val socketNumber = socketSequence.incrementAndGet()
            object : DatagramSocket(null as SocketAddress?) {
                override fun receive(packet: DatagramPacket) {
                    super.receive(packet)
                    tracePacket("RX", socketNumber, packet)
                }

                override fun send(packet: DatagramPacket) {
                    tracePacket("TX", socketNumber, packet)
                    super.send(packet)
                }
            }.apply {
                broadcast = true
                bind(InetSocketAddress(routedLocalAddress, localAddress.port))
            }
        }

        val sink = object : PcmSink {
            override val format = PcmFormat(
                sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ,
            )

            override fun write(buffer: ShortArray, offset: Int, length: Int) {
                require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
                pcmBlocks.incrementAndGet()
                pcmSamples.addAndGet(length.toLong())
            }

            override fun close() = Unit
        }

        val clientName = if (wireProfile == Ic705RxWireProfile.RIGPLANE_DIAGNOSTIC) {
            "rigplane"
        } else {
            "APRSdroid RX"
        }
        val session = Ic705RxSession(
            config = Ic705RxSessionConfig(
                radioAddress = radioAddress,
                controlPort = CONTROL_PORT,
                username = username,
                password = password,
                clientName = clientName,
                autoReconnect = false,
            ),
            audioSink = sink,
            callbacks = Ic705RxSessionCallbacks(
                onStateChanged = { state ->
                    finalPhase.set(state.phase)
                    phaseChanges.incrementAndGet()
                    println(
                        "IC705_HW phase=${state.phase} " +
                            "failureReason=${state.failureReason ?: "none"} " +
                            "loginAccepted=${state.loginAccepted} " +
                            "connectionAuthorized=${state.connectionRequestAuthorized} " +
                            "connectionInfoReceived=${state.connectionInfoReceived} " +
                            "endpointsReceived=${state.streamEndpoints != null}",
                    )
                },
                onIssue = { issue ->
                    val issueNumber = issues.incrementAndGet()
                    val channel = issue.channel?.name ?: "SESSION"
                    val packet = issue.packet
                    if (packet == null) {
                        println(
                            "IC705_HW issue=$issueNumber code=${issue.code} " +
                                "channel=$channel packet=none",
                        )
                    } else {
                        println(
                            "IC705_HW issue=$issueNumber code=${issue.code} channel=$channel " +
                                "packet.length=${packet.length} " +
                                "packet.declaredLength=${packet.declaredLength} " +
                                "packet.commonType=${packet.commonType} " +
                                "packet.receiverKind=${packet.receiverKind} " +
                                "packet.payloadLength=${packet.payloadLength} " +
                                "packet.requestReply=${packet.requestReply} " +
                                "packet.requestType=${packet.requestType} " +
                                "packet.rejection=${packet.rejection}",
                        )
                    }
                },
                onAudioReset = { reset ->
                    val resetCount = audioResets.incrementAndGet()
                    println("IC705_HW audioReset=$resetCount reason=${reset.reason}")
                },
            ),
            socketFactory = tracingSocketFactory,
            wireProfile = wireProfile,
        )

        try {
            println(
                "IC705_HW probe=started durationSeconds=$durationSeconds " +
                    "wireProfile=${wireProfile.name}",
            )
            session.start()
            Thread.sleep(TimeUnit.SECONDS.toMillis(durationSeconds))
        } finally {
            session.close { closed.countDown() }
        }

        val closedCleanly = closed.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        println(
            "IC705_HW probe=finished phase=${finalPhase.get()} " +
                "phaseChanges=${phaseChanges.get()} issues=${issues.get()} " +
                "pcmBlocks=${pcmBlocks.get()} pcmSamples=${pcmSamples.get()} " +
                "audioResets=${audioResets.get()} closed=$closedCleanly",
        )

        assertTrue("IC-705 RX session did not close within the timeout", closedCleanly)
        assertTrue(
            "IC-705 RX hardware probe received no PCM samples; inspect IC705_HW diagnostics",
            pcmSamples.get() > 0L,
        )
    }

    private fun requiredEnvironment(name: String): String =
        System.getenv(name)?.takeIf(String::isNotBlank)
            ?: error("$name must be set when $ENABLE_ENV=1")

    private companion object {
        const val ENABLE_ENV = "IC705_HW_TEST"
        const val HOST_ENV = "IC705_HOST"
        const val USER_ENV = "IC705_USER"
        const val PASSWORD_ENV = "IC705_PASSWORD"
        const val WIRE_PROFILE_ENV = "IC705_WIRE_PROFILE"
        const val SECONDS_ENV = "IC705_SECONDS"
        const val WIRE_TRACE_ENV = "IC705_WIRE_TRACE"
        const val CONTROL_PORT = 50_001
        const val DEFAULT_SECONDS = 20L
        const val MIN_SECONDS = 1L
        const val MAX_SECONDS = 300L
        const val CLOSE_TIMEOUT_SECONDS = 10L
    }
}
