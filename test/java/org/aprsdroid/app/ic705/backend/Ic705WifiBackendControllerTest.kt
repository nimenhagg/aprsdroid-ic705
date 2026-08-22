package org.aprsdroid.app.ic705.backend

import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.FeedableAfskDecoder
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.session.Ic705RadioSession
import org.aprsdroid.app.ic705.session.Ic705RxSessionCallbacks
import org.aprsdroid.app.ic705.session.Ic705RxSessionConfig
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sivantoledo.ax25.Afsk1200Modulator
import sivantoledo.ax25.Packet

/**
 * Fake/lifecycle tests for the receive-only IC-705 backend controller.
 *
 * The controller is the only unit tested here: [Ic705WifiBackend] is a thin
 * adapter whose production wiring is trivial. All Android dependencies are
 * injected, so these tests run on the plain JVM.
 */
class Ic705WifiBackendControllerTest {

    // ------------------------------------------------------------------ fakes

    private class FakeService : Ic705BackendService {
        val startedCalls = AtomicInteger()
        val aborts = CopyOnWriteArrayList<String>()
        val submitted = CopyOnWriteArrayList<String>()

        override fun postPosterStarted() { startedCalls.incrementAndGet() }
        override fun postAbort(message: String) { aborts.add(message) }
        override fun postSubmit(text: String) { submitted.add(text) }
        override fun getString(resId: Int): String = "R$resId"
    }

    private class FakePrefs(
        override var address: String = "192.168.1.143",
        override var controlPort: Int = 50_001,
        override var username: String = "ic705",
        override var password: String = "testpass",
    ) : Ic705BackendPrefs

    private class FakeSocketFactory : Ic705DatagramSocketFactory {
        val createCalls = AtomicInteger()

        override fun create(localAddress: InetSocketAddress): DatagramSocket {
            createCalls.incrementAndGet()
            throw AssertionError("backend must not create sockets itself")
        }
    }

    private class FakeReconnectScheduler : Ic705ReconnectScheduler {
        data class Task(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        val tasks = mutableListOf<Task>()
        var closeCalls = 0

        override fun schedule(delayMillis: Long, action: () -> Unit): Ic705RetryHandle {
            val task = Task(delayMillis, action)
            tasks.add(task)
            return Ic705RetryHandle { task.cancelled = true }
        }

        override fun close() { closeCalls++ }

        fun runNext() {
            val task = tasks.firstOrNull { !it.cancelled } ?: error("no pending retry")
            task.cancelled = true
            task.action()
        }
    }

    private class FakeSessionFactory(
        val sessions: MutableList<FakeSession> = mutableListOf(),
    ) : Ic705RadioSessionFactory {
        override fun create(
            config: Ic705RxSessionConfig,
            audioSink: PcmSink,
            callbacks: Ic705RxSessionCallbacks,
            socketFactory: Ic705DatagramSocketFactory,
        ): Ic705RadioSession {
            val session = FakeSession(config, audioSink, callbacks)
            sessions.add(session)
            return session
        }
    }

    private class FakeSession(
        val config: Ic705RxSessionConfig,
        val audioSink: PcmSink,
        val callbacks: Ic705RxSessionCallbacks,
    ) : Ic705RadioSession {
        val startCalls = AtomicInteger()
        val stopCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        override var state = Ic705RxSessionEngine.State()
        override var isTransmitting: Boolean = false
        val transmittedPackets = mutableListOf<APRSPacket>()
        var transmitResult: Boolean = true
        var closeHandler: (() -> Unit) -> Unit = { it() }

        override fun start() { startCalls.incrementAndGet() }
        override fun stop() { stopCalls.incrementAndGet() }
        override fun transmit(packet: APRSPacket): Boolean {
            transmittedPackets.add(packet)
            return transmitResult
        }
        override fun close() = close {}
        override fun close(onClosed: () -> Unit) {
            closeCalls.incrementAndGet()
            closeHandler(onClosed)
        }

        fun emitState(phase: Ic705RxSessionEngine.Phase) {
            state = state.copy(phase = phase)
            callbacks.onStateChanged(state)
        }
    }

    private class FakeDecoderFactory(
        val decoders: MutableList<FakeSink> = mutableListOf(),
    ) : Ic705DecoderFactory {
        override fun create(format: PcmFormat, onPacket: (ByteArray) -> Unit): PcmSink {
            val sink = FakeSink(format, onPacket)
            decoders.add(sink)
            return sink
        }
    }

    private class FakeSink(
        override val format: PcmFormat,
        private val onPacket: (ByteArray) -> Unit,
    ) : PcmSink {
        var closed = false
        val closeCalls = AtomicInteger()

        /** Mirrors the real decoder: packet delivery is synchronous with feeding. */
        fun emitPacket(data: ByteArray) {
            check(!closed) { "decoder is closed" }
            onPacket(data.copyOf())
        }

        /** Simulates a late callback already queued before close completed. */
        fun emitPacketUnchecked(data: ByteArray) {
            onPacket(data.copyOf())
        }

        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            check(!closed) { "decoder is closed" }
        }

        override fun close() {
            closed = true
            closeCalls.incrementAndGet()
        }
    }

    private fun controller(
        service: FakeService = FakeService(),
        prefs: FakePrefs = FakePrefs(),
        sdkAtLeast: (Int) -> Boolean = { true },
        socketFactory: Ic705DatagramSocketFactory? = FakeSocketFactory(),
        socketFactoryProvider: (() -> Ic705DatagramSocketFactory?)? = null,
        sessionFactory: Ic705RadioSessionFactory = FakeSessionFactory(),
        decoderFactory: Ic705DecoderFactory = FakeDecoderFactory(),
        reconnectScheduler: Ic705ReconnectScheduler = FakeReconnectScheduler(),
        reconnectBackoff: Ic705ReconnectBackoff = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterFraction = 0.0,
        ),
    ) = Ic705WifiBackendController(
        service = service,
        prefs = prefs,
        sdkAtLeast = sdkAtLeast,
        socketFactoryProvider = socketFactoryProvider ?: { socketFactory },
        sessionFactory = sessionFactory,
        decoderFactory = decoderFactory,
        reconnectScheduler = reconnectScheduler,
        reconnectBackoff = reconnectBackoff,
    )

    // ------------------------------------------------------------ fixtures

    private fun testPacket(): Packet = Packet(
        "N0CALL",
        "APRS",
        arrayOf("WIDE1-1"),
        Packet.AX25_CONTROL_APRS,
        Packet.AX25_PROTOCOL_NO_LAYER_3,
        "!4903.50N/07201.75W-Test APRS frame".toByteArray(Charsets.ISO_8859_1),
    )

    private fun expectedText(packet: Packet): String =
        Parser.parseAX25(packet.bytesWithoutCRC()).toString().trim()

    // ------------------------------------------------------------- contract

    @Test
    fun startCreatesExactlyOneSessionAndDecoderCombo() {
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val socketFactory = FakeSocketFactory()
        val ctl = controller(sessionFactory = sessionFactory, decoderFactory = decoderFactory, socketFactory = socketFactory)

        assertFalse(ctl.start())

        assertEquals(1, sessionFactory.sessions.size)
        assertEquals(1, decoderFactory.decoders.size)
        assertEquals(1, sessionFactory.sessions.single().startCalls.get())
        // The backend never opens UDP sockets itself; the session owns them.
        assertEquals(0, socketFactory.createCalls.get())
    }

    @Test
    fun doesNotReportStartedBeforeReceivingAndReportsItOnlyOnce() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()

        session.emitState(Ic705RxSessionEngine.Phase.OPENING_SOCKETS)
        session.emitState(Ic705RxSessionEngine.Phase.AUTHENTICATING)
        assertEquals(0, service.startedCalls.get())

        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        assertEquals(1, service.startedCalls.get())

        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        assertEquals(1, service.startedCalls.get())
    }

    @Test
    fun failedStateAbortsAndDoesNotReportStarted() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory)

        ctl.start()
        sessionFactory.sessions.single().emitState(Ic705RxSessionEngine.Phase.FAILED)

        assertEquals(0, service.startedCalls.get())
        assertEquals(
            listOf(service.getString(R.string.ic705_backend_failed)),
            service.aborts.toList(),
        )
    }

    @Test
    fun decodedRawAx25IsParsedAndSubmittedExactlyOnce() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory, decoderFactory = decoderFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        val decoder = decoderFactory.decoders.single()
        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)

        val packet = testPacket()
        decoder.emitPacket(packet.bytesWithoutCRC())

        assertEquals(listOf(expectedText(packet)), service.submitted.toList())
        assertEquals(0, session.closeCalls.get())
    }

    @Test
    fun realAfskRoundTripReachesPostSubmitExactlyOnce() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(
            service = service,
            sessionFactory = sessionFactory,
            decoderFactory = Ic705DecoderFactory { format, onPacket ->
                FeedableAfskDecoder(format, onPacket)
            },
        )

        ctl.start()
        val decoder = sessionFactory.sessions.single().audioSink
        sessionFactory.sessions.single().emitState(Ic705RxSessionEngine.Phase.RECEIVING)

        val packet = testPacket()
        decoder.write(modulate(packet))

        assertEquals(listOf(expectedText(packet)), service.submitted.toList())
    }

    @Test
    fun badAx25DoesNotCloseTheSession() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory, decoderFactory = decoderFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        val decoder = decoderFactory.decoders.single()

        decoder.emitPacket(byteArrayOf(0x00, 0x01, 0x02))

        assertEquals(0, service.submitted.size)
        assertEquals(0, session.closeCalls.get())
        assertFalse(decoder.closed)
    }

    @Test
    fun updateTransmitsSuccessfullyWhenReceiving() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)

        val packet = Parser.parseAX25(testPacket().bytesWithoutCRC())
        val result = ctl.update(packet)

        assertEquals(service.getString(R.string.ic705_backend_tx_ok), result)
        assertEquals(listOf(packet), session.transmittedPackets)
    }

    @Test
    fun updateReturnsNotConnectedBeforeReceiving() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        session.emitState(Ic705RxSessionEngine.Phase.AUTHENTICATING)

        val packet = Parser.parseAX25(testPacket().bytesWithoutCRC())
        val result = ctl.update(packet)

        assertEquals(service.getString(R.string.ic705_backend_not_connected), result)
        assertEquals(0, session.transmittedPackets.size)
    }

    @Test
    fun updateReturnsBusyWhenAlreadyTransmitting() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        session.isTransmitting = true

        val packet = Parser.parseAX25(testPacket().bytesWithoutCRC())
        val result = ctl.update(packet)

        assertEquals(service.getString(R.string.ic705_backend_tx_busy), result)
        assertEquals(0, session.transmittedPackets.size)
    }

    @Test
    fun doubleStopDoesNotCrashOrDoubleClose() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory, decoderFactory = decoderFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        val decoder = decoderFactory.decoders.single()

        ctl.stop()
        ctl.stop()

        assertEquals(1, session.closeCalls.get())
        assertEquals(1, decoder.closeCalls.get())
        assertTrue(decoder.closed)
        // Post-stop state changes are suppressed.
        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        assertEquals(0, service.startedCalls.get())
    }

    @Test
    fun stopRacingInFlightDeliveryLeavesNoStaleCallbacksAfterClose() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory, decoderFactory = decoderFactory)

        ctl.start()
        val session = sessionFactory.sessions.single()
        val decoder = decoderFactory.decoders.single()
        val packet = testPacket()

        // A frame delivered before close is submitted normally.
        decoder.emitPacket(packet.bytesWithoutCRC())
        assertEquals(listOf(expectedText(packet)), service.submitted.toList())

        val closed = CountDownLatch(1)
        session.closeHandler = { onClosed ->
            Thread {
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                onClosed()
                closed.countDown()
            }.start()
        }

        ctl.stop()
        assertFalse(decoder.closed)
        assertTrue(closed.await(2, TimeUnit.SECONDS))
        assertTrue(decoder.closed)
        assertEquals(1, session.closeCalls.get())

        // Feeding after close must not deliver anything.
        val submittedBefore = service.submitted.size
        try {
            decoder.emitPacket(packet.bytesWithoutCRC())
            fail("decoder must reject samples after close")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(submittedBefore, service.submitted.size)
        assertEquals(1, service.submitted.size)
    }

    @Test
    fun missingWifiFailsWithoutCreatingSessionDecoderOrSocket() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            service = service,
            socketFactory = null,
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
        )

        assertFalse(ctl.start())

        assertEquals(listOf(service.getString(R.string.ic705_backend_no_wifi)), service.aborts.toList())
        assertEquals(0, sessionFactory.sessions.size)
        assertEquals(0, decoderFactory.decoders.size)
    }

    @Test
    fun startBelowMinimumSdkFailsCleanly() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(service = service, sessionFactory = sessionFactory, sdkAtLeast = { false })

        assertFalse(ctl.start())

        assertEquals(
            listOf(service.getString(R.string.ic705_backend_requires_api_22)),
            service.aborts.toList(),
        )
        assertEquals(0, sessionFactory.sessions.size)
    }

    @Test
    fun invalidSettingsFailCleanlyWithoutCreatingSessionOrDecoder() {
        val service = FakeService()
        val prefs = FakePrefs(address = "")
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            service = service,
            prefs = prefs,
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
        )

        assertFalse(ctl.start())

        assertEquals(
            listOf(service.getString(R.string.ic705_backend_invalid_settings)),
            service.aborts.toList(),
        )
        assertEquals(0, sessionFactory.sessions.size)
        assertEquals(0, decoderFactory.decoders.size)
    }

    @Test
    fun sessionCreationFailureClosesTheCreatedDecoder() {
        val service = FakeService()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            service = service,
            sessionFactory = Ic705RadioSessionFactory { _, _, _, _ ->
                throw RuntimeException("session create failed")
            },
            decoderFactory = decoderFactory,
        )

        assertFalse(ctl.start())

        assertEquals(
            listOf(service.getString(R.string.ic705_backend_invalid_settings)),
            service.aborts.toList(),
        )
        assertEquals(1, decoderFactory.decoders.size)
        assertTrue(decoderFactory.decoders.single().closed)
    }

    @Test
    fun duplicateStartDoesNotCreateAnotherGeneration() {
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(sessionFactory = sessionFactory)

        assertFalse(ctl.start())
        assertFalse(ctl.start())

        assertEquals(1, sessionFactory.sessions.size)
        assertEquals(1, sessionFactory.sessions.single().startCalls.get())
    }

    @Test
    fun recoverableFailureClosesOldGenerationBeforeRetryAndReacquiresWifi() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val scheduler = FakeReconnectScheduler()
        val firstFactory = FakeSocketFactory()
        val secondFactory = FakeSocketFactory()
        val providers = mutableListOf<Ic705DatagramSocketFactory?>(firstFactory, secondFactory)
        val providerCalls = AtomicInteger()
        val ctl = controller(
            service = service,
            socketFactoryProvider = {
                providerCalls.incrementAndGet()
                if (providers.isEmpty()) null else providers.removeAt(0)
            },
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            reconnectScheduler = scheduler,
        )

        ctl.start()
        val first = sessionFactory.sessions.single()
        val firstDecoder = decoderFactory.decoders.single()
        first.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        first.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        first.emitState(Ic705RxSessionEngine.Phase.FAILED)

        assertEquals(1, first.closeCalls.get())
        assertTrue(firstDecoder.closed)
        assertEquals(1, scheduler.tasks.size)
        assertEquals(1_000L, scheduler.tasks.single().delayMillis)
        assertEquals(1, providerCalls.get())
        assertEquals(1, sessionFactory.sessions.size)
        assertTrue(service.aborts.isEmpty())

        scheduler.runNext()

        assertEquals(2, providerCalls.get())
        assertEquals(2, sessionFactory.sessions.size)
        assertEquals(2, decoderFactory.decoders.size)
        assertEquals(1, sessionFactory.sessions[1].startCalls.get())
        assertFalse(decoderFactory.decoders[1].closed)
    }

    @Test
    fun recoveryWaitsForAsynchronousCloseBeforeArmingRetry() {
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val scheduler = FakeReconnectScheduler()
        val ctl = controller(
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            reconnectScheduler = scheduler,
        )
        ctl.start()
        val first = sessionFactory.sessions.single()
        var finishClose: (() -> Unit)? = null
        first.closeHandler = { onClosed -> finishClose = onClosed }

        first.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        first.emitState(Ic705RxSessionEngine.Phase.FAILED)

        assertEquals(1, first.closeCalls.get())
        assertFalse(decoderFactory.decoders.single().closed)
        assertTrue(scheduler.tasks.isEmpty())

        finishClose!!.invoke()

        assertTrue(decoderFactory.decoders.single().closed)
        assertEquals(1, scheduler.tasks.size)
    }

    @Test
    fun wifiMissingDuringRecoveryKeepsRetryingWithoutDefaultRouteSession() {
        val sessionFactory = FakeSessionFactory()
        val scheduler = FakeReconnectScheduler()
        val firstFactory = FakeSocketFactory()
        var providerCall = 0
        val ctl = controller(
            socketFactoryProvider = {
                providerCall++
                if (providerCall == 1) firstFactory else null
            },
            sessionFactory = sessionFactory,
            reconnectScheduler = scheduler,
        )

        ctl.start()
        val first = sessionFactory.sessions.single()
        first.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        first.emitState(Ic705RxSessionEngine.Phase.FAILED)
        scheduler.runNext()

        assertEquals(2, providerCall)
        assertEquals(1, sessionFactory.sessions.size)
        assertEquals(2, scheduler.tasks.size)
        assertEquals(2_000L, scheduler.tasks.last().delayMillis)
    }

    @Test
    fun receivingResetsReconnectBackoff() {
        val sessionFactory = FakeSessionFactory()
        val scheduler = FakeReconnectScheduler()
        val ctl = controller(
            sessionFactory = sessionFactory,
            reconnectScheduler = scheduler,
        )

        ctl.start()
        var current = sessionFactory.sessions[0]
        current.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        current.emitState(Ic705RxSessionEngine.Phase.FAILED)
        assertEquals(1_000L, scheduler.tasks.last().delayMillis)
        scheduler.runNext()

        current = sessionFactory.sessions[1]
        current.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        current.emitState(Ic705RxSessionEngine.Phase.FAILED)
        assertEquals(2_000L, scheduler.tasks.last().delayMillis)
        scheduler.runNext()

        current = sessionFactory.sessions[2]
        current.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        current.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        current.emitState(Ic705RxSessionEngine.Phase.FAILED)
        assertEquals(1_000L, scheduler.tasks.last().delayMillis)
    }

    @Test
    fun stopDuringBackoffCancelsPendingRetryAndPreventsNewGeneration() {
        val sessionFactory = FakeSessionFactory()
        val scheduler = FakeReconnectScheduler()
        val ctl = controller(
            sessionFactory = sessionFactory,
            reconnectScheduler = scheduler,
        )

        ctl.start()
        val first = sessionFactory.sessions.single()
        first.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        first.emitState(Ic705RxSessionEngine.Phase.FAILED)
        val pending = scheduler.tasks.single()

        ctl.stop()

        assertTrue(pending.cancelled)
        assertEquals(1, scheduler.closeCalls)
        pending.action() // Simulate a timer race after cancellation.
        assertEquals(1, sessionFactory.sessions.size)
    }

    @Test
    fun staleGenerationCannotReportStartedResetOrSubmitAfterRecoveryBegins() {
        val service = FakeService()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val scheduler = FakeReconnectScheduler()
        val ctl = controller(
            service = service,
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            reconnectScheduler = scheduler,
        )

        ctl.start()
        val oldSession = sessionFactory.sessions.single()
        val oldDecoder = decoderFactory.decoders.single()
        oldSession.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        oldSession.emitState(Ic705RxSessionEngine.Phase.FAILED)

        // The generation is invalidated before close/retry, so late callbacks are ignored.
        oldSession.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        oldDecoder.emitPacketUnchecked(testPacket().bytesWithoutCRC())
        assertEquals(0, service.startedCalls.get())
        assertTrue(service.submitted.isEmpty())
        assertTrue(oldDecoder.closed)
    }

    @Test
    fun reconnectBackoffIsExponentialBoundedAndJitterable() {
        val noJitter = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 5_000L,
            jitterFraction = 0.0,
        )
        assertEquals(1_000L, noJitter.delayMillis(0))
        assertEquals(2_000L, noJitter.delayMillis(1))
        assertEquals(4_000L, noJitter.delayMillis(2))
        assertEquals(5_000L, noJitter.delayMillis(3))
        assertEquals(5_000L, noJitter.delayMillis(20))

        val lowJitter = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterFraction = 0.20,
            randomUnit = { 0.0 },
        )
        val highJitter = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterFraction = 0.20,
            randomUnit = { 1.0 },
        )
        assertEquals(800L, lowJitter.delayMillis(0))
        assertEquals(1_200L, highJitter.delayMillis(0))
    }

    // ------------------------------------------------------------- helpers

    /** Modulates an AX.25 frame into 48 kHz mono PCM16 signed shorts. */
    private fun modulate(packet: Packet, sampleRate: Int = 12_000): ShortArray {
        val modulator = Afsk1200Modulator(sampleRate)
        modulator.prepareToTransmit(packet)
        val samples = mutableListOf<Float>()
        while (true) {
            val buffer = modulator.getTxSamplesBuffer()
            val count = modulator.getSamples()
            if (count == 0) break
            for (index in 0 until count) samples.add(buffer[index])
            check(samples.size <= sampleRate * 3) { "modulator produced too many samples" }
        }
        return ShortArray(samples.size) { index ->
            (samples[index] * 32_767f).toInt().toShort()
        }
    }
}
