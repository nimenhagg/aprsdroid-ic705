package org.aprsdroid.app.ic705.backend

import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.R
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.session.Ic705RadioSession
import org.aprsdroid.app.ic705.session.Ic705RxSessionCallbacks
import org.aprsdroid.app.ic705.session.Ic705RxSessionConfig
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression coverage for IC-705 link reporting and fixed-port generation reuse. */
class Ic705WifiBackendRecoveryPolicyTest {

    private class RecordingService : Ic705BackendService {
        val startedCalls = AtomicInteger()
        val linkOn = mutableListOf<Int>()
        val linkOff = mutableListOf<Int>()
        val aborts = mutableListOf<String>()

        override fun postPosterStarted() {
            startedCalls.incrementAndGet()
        }

        override fun postLinkOn(link: Int) {
            linkOn += link
        }

        override fun postLinkOff(link: Int) {
            linkOff += link
        }

        override fun postAbort(message: String) {
            aborts += message
        }

        override fun postSubmit(text: String) = Unit

        override fun getString(resId: Int): String = "R$resId"
    }

    private object StubPrefs : Ic705BackendPrefs {
        override val address = "192.168.1.143"
        override val controlPort = 50_001
        override val username = "ic705"
        override val password = "testpass"
    }

    private object StubSocketFactory : Ic705DatagramSocketFactory {
        override fun create(localAddress: InetSocketAddress): DatagramSocket {
            throw AssertionError("fake session must not open a real socket")
        }
    }

    private class RecordingScheduler : Ic705ReconnectScheduler {
        data class Task(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        val tasks = mutableListOf<Task>()

        override fun schedule(delayMillis: Long, action: () -> Unit): Ic705RetryHandle {
            val task = Task(delayMillis, action)
            tasks += task
            return Ic705RetryHandle { task.cancelled = true }
        }

        override fun close() = Unit

        fun runNext() {
            val task = tasks.first { !it.cancelled }
            task.cancelled = true
            task.action()
        }
    }

    private class StubSink : PcmSink {
        override val format = PcmFormat(sampleRateHz = 12_000)
        override fun write(buffer: ShortArray, offset: Int, length: Int) = Unit
        override fun close() = Unit
    }

    private class RecordingSessionFactory : Ic705RadioSessionFactory {
        val sessions = mutableListOf<RecordingSession>()

        override fun create(
            config: Ic705RxSessionConfig,
            audioSink: PcmSink,
            callbacks: Ic705RxSessionCallbacks,
            socketFactory: Ic705DatagramSocketFactory,
        ): Ic705RadioSession = RecordingSession(callbacks).also(sessions::add)
    }

    private class RecordingSession(
        private val callbacks: Ic705RxSessionCallbacks,
    ) : Ic705RadioSession {
        override var state = Ic705RxSessionEngine.State()
        override var isTransmitting = false
        var closeCalls = 0

        override fun start() = Unit
        override fun stop() = Unit
        override fun transmit(packet: APRSPacket): Boolean = false
        override fun close() = close {}

        override fun close(onClosed: () -> Unit) {
            closeCalls++
            onClosed()
        }

        fun emit(phase: Ic705RxSessionEngine.Phase) {
            state = state.copy(phase = phase)
            callbacks.onStateChanged(state)
        }
    }

    private fun controller(
        service: RecordingService,
        sessions: RecordingSessionFactory,
        scheduler: RecordingScheduler,
        cooldownMillis: Long = 2_000L,
    ) = Ic705WifiBackendController(
        service = service,
        prefs = StubPrefs,
        sdkAtLeast = { true },
        socketFactoryProvider = { StubSocketFactory },
        sessionFactory = sessions,
        decoderFactory = Ic705DecoderFactory { _, _ -> StubSink() },
        reconnectScheduler = scheduler,
        reconnectBackoff = Ic705ReconnectBackoff(
            initialMillis = 1_000L,
            maxMillis = 30_000L,
            jitterFraction = 0.0,
        ),
        fixedPortReuseCooldownMillis = cooldownMillis,
    )

    @Test
    fun onlyReceivingIsReportedAsLinkUpAcrossRecovery() {
        val service = RecordingService()
        val sessions = RecordingSessionFactory()
        val scheduler = RecordingScheduler()
        val controller = controller(service, sessions, scheduler)

        controller.start()
        val first = sessions.sessions.single()

        first.emit(Ic705RxSessionEngine.Phase.AUTHENTICATING)
        first.emit(Ic705RxSessionEngine.Phase.NEGOTIATING)
        assertTrue(service.linkOn.isEmpty())
        assertTrue(service.linkOff.isEmpty())
        assertEquals(0, service.startedCalls.get())

        first.emit(Ic705RxSessionEngine.Phase.RECEIVING)
        assertEquals(listOf(R.string.p_conn_ic705), service.linkOn)
        assertEquals(1, service.startedCalls.get())

        first.emit(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        assertEquals(listOf(R.string.p_conn_ic705), service.linkOff)

        first.emit(Ic705RxSessionEngine.Phase.FAILED)
        assertEquals(1, first.closeCalls)
        assertEquals(1, scheduler.tasks.size)
        assertEquals(2_000L, scheduler.tasks.single().delayMillis)

        scheduler.runNext()
        val second = sessions.sessions[1]
        second.emit(Ic705RxSessionEngine.Phase.RECEIVING)

        assertEquals(
            listOf(R.string.p_conn_ic705, R.string.p_conn_ic705),
            service.linkOn,
        )
        // Starting the location/poster lifecycle remains a one-shot event.
        assertEquals(1, service.startedCalls.get())
        assertTrue(service.aborts.isEmpty())
    }

    @Test
    fun fixedPortCooldownIsAppliedAfterBackoffCalculation() {
        val service = RecordingService()
        val sessions = RecordingSessionFactory()
        val scheduler = RecordingScheduler()
        val controller = controller(service, sessions, scheduler, cooldownMillis = 2_000L)

        controller.start()
        val first = sessions.sessions.single()
        first.emit(Ic705RxSessionEngine.Phase.RECEIVING)
        first.emit(Ic705RxSessionEngine.Phase.RECONNECT_WAIT)
        first.emit(Ic705RxSessionEngine.Phase.FAILED)

        // The injected first backoff is 1 s, but fixed-port reuse cannot occur before 2 s.
        assertEquals(2_000L, scheduler.tasks.single().delayMillis)
    }
}
