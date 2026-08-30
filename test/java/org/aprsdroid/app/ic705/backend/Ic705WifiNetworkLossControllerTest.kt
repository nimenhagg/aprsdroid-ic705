package org.aprsdroid.app.ic705.backend

import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import net.ab0oo.aprs.parser.APRSPacket
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
import org.junit.Test

class Ic705WifiNetworkLossControllerTest {
    private class FakeService : Ic705BackendService {
        val startedCalls = AtomicInteger()
        val linkOffCalls = AtomicInteger()
        val aborts = CopyOnWriteArrayList<String>()
        val submitted = CopyOnWriteArrayList<String>()

        override fun postPosterStarted() { startedCalls.incrementAndGet() }
        override fun postLinkOff(link: Int) { linkOffCalls.incrementAndGet() }
        override fun postAbort(message: String) { aborts.add(message) }
        override fun postSubmit(text: String) { submitted.add(text) }
        override fun getString(resId: Int): String = "R$resId"
    }

    private class FakePrefs : Ic705BackendPrefs {
        override val address: String = "192.168.1.143"
        override val controlPort: Int = 50_001
        override val username: String = "ic705"
        override val password: String = "testpass"
    }

    private class FakeSocketFactory : Ic705DatagramSocketFactory {
        override fun create(localAddress: InetSocketAddress): DatagramSocket {
            throw AssertionError("controller must pass socket factories to sessions without opening sockets itself")
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
            val task = tasks.first { !it.cancelled }
            task.cancelled = true
            task.action()
        }
    }

    private class FakeSessionFactory : Ic705RadioSessionFactory {
        val sessions = mutableListOf<FakeSession>()

        override fun create(
            config: Ic705RxSessionConfig,
            audioSink: PcmSink,
            callbacks: Ic705RxSessionCallbacks,
            socketFactory: Ic705DatagramSocketFactory,
        ): Ic705RadioSession {
            return FakeSession(callbacks).also(sessions::add)
        }
    }

    private class FakeSession(
        private val callbacks: Ic705RxSessionCallbacks,
    ) : Ic705RadioSession {
        val startCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        override var state = Ic705RxSessionEngine.State()
        override var isTransmitting: Boolean = false
        var closeHandler: (() -> Unit) -> Unit = { it() }

        override fun start() { startCalls.incrementAndGet() }
        override fun stop() = Unit
        override fun transmit(packet: APRSPacket): Boolean = false
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

    private class FakeDecoderFactory : Ic705DecoderFactory {
        val decoders = mutableListOf<FakeSink>()

        override fun create(format: PcmFormat, onPacket: (ByteArray) -> Unit): PcmSink {
            return FakeSink(format, onPacket).also(decoders::add)
        }
    }

    private class FakeSink(
        override val format: PcmFormat,
        private val onPacket: (ByteArray) -> Unit,
    ) : PcmSink {
        var closed = false
        val closeCalls = AtomicInteger()

        override fun write(buffer: ShortArray, offset: Int, length: Int) = Unit
        override fun close() {
            closed = true
            closeCalls.incrementAndGet()
        }

        fun emitPacketUnchecked(data: ByteArray) {
            onPacket(data.copyOf())
        }
    }

    private fun controller(
        service: FakeService = FakeService(),
        socketFactoryProvider: () -> Ic705DatagramSocketFactory? = { FakeSocketFactory() },
        sessionFactory: FakeSessionFactory = FakeSessionFactory(),
        decoderFactory: FakeDecoderFactory = FakeDecoderFactory(),
        scheduler: FakeReconnectScheduler = FakeReconnectScheduler(),
        fixedPortReuseCooldownMillis: Long = 0L,
    ): Ic705WifiBackendController = Ic705WifiBackendController(
        service = service,
        prefs = FakePrefs(),
        sdkAtLeast = { true },
        socketFactoryProvider = socketFactoryProvider,
        sessionFactory = sessionFactory,
        decoderFactory = decoderFactory,
        reconnectScheduler = scheduler,
        reconnectBackoff = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterFraction = 0.0,
        ),
        fixedPortReuseCooldownMillis = fixedPortReuseCooldownMillis,
    )

    @Test
    fun selectedWifiLossClosesGenerationAndReacquiresWifiOnRetry() {
        val service = FakeService()
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val providerCalls = AtomicInteger()
        val factories = mutableListOf<Ic705DatagramSocketFactory>(FakeSocketFactory(), FakeSocketFactory())
        val ctl = controller(
            service = service,
            socketFactoryProvider = {
                providerCalls.incrementAndGet()
                factories.removeAt(0)
            },
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            scheduler = scheduler,
        )

        ctl.start()
        val firstSession = sessionFactory.sessions.single()
        firstSession.emitState(Ic705RxSessionEngine.Phase.RECEIVING)

        ctl.onSelectedWifiLost()

        assertEquals(1, firstSession.closeCalls.get())
        assertTrue(decoderFactory.decoders.single().closed)
        assertEquals(1, service.linkOffCalls.get())
        assertEquals(1, providerCalls.get())
        assertEquals(1, scheduler.tasks.size)
        assertEquals(1_000L, scheduler.tasks.single().delayMillis)

        scheduler.runNext()

        assertEquals(2, providerCalls.get())
        assertEquals(2, sessionFactory.sessions.size)
        assertEquals(1, sessionFactory.sessions[1].startCalls.get())
    }

    @Test
    fun duplicateWifiLossWhileCloseIsPendingDoesNotDoubleCloseOrRetry() {
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            scheduler = scheduler,
        )

        ctl.start()
        val session = sessionFactory.sessions.single()
        var finishClose: (() -> Unit)? = null
        session.closeHandler = { onClosed -> finishClose = onClosed }

        ctl.onSelectedWifiLost()
        ctl.onSelectedWifiLost()

        assertEquals(1, session.closeCalls.get())
        assertFalse(decoderFactory.decoders.single().closed)
        assertTrue(scheduler.tasks.isEmpty())

        finishClose!!.invoke()

        assertTrue(decoderFactory.decoders.single().closed)
        assertEquals(1, scheduler.tasks.size)
    }

    @Test
    fun stopDuringNetworkLossClosePreventsLateReconnect() {
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            scheduler = scheduler,
        )

        ctl.start()
        val session = sessionFactory.sessions.single()
        var finishClose: (() -> Unit)? = null
        session.closeHandler = { onClosed -> finishClose = onClosed }

        ctl.onSelectedWifiLost()
        ctl.stop()
        finishClose!!.invoke()

        assertEquals(1, session.closeCalls.get())
        assertTrue(decoderFactory.decoders.single().closed)
        assertTrue(scheduler.tasks.isEmpty())
        assertEquals(1, scheduler.closeCalls)
    }

    @Test
    fun staleCallbacksAfterWifiLossCannotReviveOldGeneration() {
        val service = FakeService()
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val decoderFactory = FakeDecoderFactory()
        val ctl = controller(
            service = service,
            sessionFactory = sessionFactory,
            decoderFactory = decoderFactory,
            scheduler = scheduler,
        )

        ctl.start()
        val session = sessionFactory.sessions.single()
        val decoder = decoderFactory.decoders.single()
        var finishClose: (() -> Unit)? = null
        session.closeHandler = { onClosed -> finishClose = onClosed }

        ctl.onSelectedWifiLost()
        session.emitState(Ic705RxSessionEngine.Phase.RECEIVING)
        decoder.emitPacketUnchecked(byteArrayOf(0x00, 0x01, 0x02))

        assertEquals(0, service.startedCalls.get())
        assertTrue(service.submitted.isEmpty())
        assertTrue(service.aborts.isEmpty())

        finishClose!!.invoke()
        assertEquals(1, scheduler.tasks.size)
    }

    @Test
    fun wifiLossDuringExistingBackoffDoesNotScheduleAnotherRetry() {
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(sessionFactory = sessionFactory, scheduler = scheduler)

        ctl.start()
        val session = sessionFactory.sessions.single()
        session.emitState(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        session.emitState(Ic705RxSessionEngine.Phase.FAILED)
        assertEquals(1, scheduler.tasks.size)

        ctl.onSelectedWifiLost()

        assertEquals(1, scheduler.tasks.size)
        assertEquals(1, session.closeCalls.get())
    }

    @Test
    fun networkLossHonorsFixedPortReuseCooldown() {
        val scheduler = FakeReconnectScheduler()
        val sessionFactory = FakeSessionFactory()
        val ctl = controller(
            sessionFactory = sessionFactory,
            scheduler = scheduler,
            fixedPortReuseCooldownMillis = 2_000L,
        )

        ctl.start()
        ctl.onSelectedWifiLost()

        assertEquals(1, scheduler.tasks.size)
        assertEquals(2_000L, scheduler.tasks.single().delayMillis)
    }
}
