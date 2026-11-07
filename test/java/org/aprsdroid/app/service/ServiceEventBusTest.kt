package org.aprsdroid.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceEventBusTest {

    @Before
    fun setUp() {
        ServiceEventBus.updateServiceRunning(false)
        ServiceEventBus.updateLinkStatus(0)
        ServiceEventBus.updateLiveStatus(null)
    }

    @Test
    fun serviceRuntimeStateUpdatesEventBusCorrectly() {
        var running = false
        var linkError = 0

        val runtimeState = ServiceRuntimeState(
            readRunning = { running },
            writeRunning = { running = it },
            readLinkError = { linkError },
            writeLinkError = { linkError = it },
        )

        assertFalse(ServiceEventBus.isServiceRunning.value)
        assertEquals(0, ServiceEventBus.linkError.value)

        runtimeState.markStarted()
        assertTrue(running)
        assertTrue(ServiceEventBus.isServiceRunning.value)

        runtimeState.markLinkOff(42)
        assertEquals(42, linkError)
        assertEquals(42, ServiceEventBus.linkError.value)

        runtimeState.markLinkOn()
        assertEquals(0, linkError)
        assertEquals(0, ServiceEventBus.linkError.value)

        runtimeState.markStopped()
        assertFalse(running)
        assertFalse(ServiceEventBus.isServiceRunning.value)
        assertEquals(0, ServiceEventBus.linkError.value)
        assertNull(ServiceEventBus.liveStatus.value)
    }

    @Test
    fun packetAndMessageEventsEmitSuccessfully() {
        val packetEvent = AprsPacketEvent(
            callsign = "BG1ABC",
            type = 1,
            source = "TCPIP",
            packet = "BG1ABC>APRS:=3954.12N/11624.34E-",
        )
        ServiceEventBus.postPacketEvent(packetEvent)

        val messageEvent = AprsMessageEvent(
            destination = "BG1ABC",
            body = "Hello from test",
            isOutgoing = true,
        )
        ServiceEventBus.postMessageEvent(messageEvent)
    }

    @Test
    fun packetEventsCanBeCollectedViaFlow() = runTest {
        val bus = ServiceEventBus()
        val collected = mutableListOf<AprsPacketEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.packetEvents.collect { collected.add(it) }
        }
        val event = AprsPacketEvent(
            callsign = "BI1AXX",
            type = 0,
            source = "IC-705",
            packet = "BI1AXX>APRS:>Flow test",
        )
        bus.postPacketEvent(event)

        assertEquals(listOf(event), collected)
    }

    @Test
    fun messageEventsCanBeCollectedViaFlow() = runTest {
        val bus = ServiceEventBus()
        val collected = mutableListOf<AprsMessageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.messageEvents.collect { collected.add(it) }
        }
        val msg = AprsMessageEvent(
            destination = "BI1AXX",
            body = "Message flow test",
            isOutgoing = true,
        )
        bus.postMessageEvent(msg)

        assertEquals(listOf(msg), collected)
    }
}
