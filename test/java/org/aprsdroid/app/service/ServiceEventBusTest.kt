package org.aprsdroid.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
