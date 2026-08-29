package org.aprsdroid.app.service

import net.ab0oo.aprs.parser.APRSPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendLifecycleCoordinatorTest {
    @Test
    fun replacingBackendStopsOldInstanceBeforeStartingNewOne() {
        val events = mutableListOf<String>()
        val backends = ArrayDeque<ServiceBackend>().apply {
            add(FakeBackend("first", events))
            add(FakeBackend("second", events))
        }
        val coordinator = BackendLifecycleCoordinator { backends.removeFirst() }

        assertTrue(coordinator.replaceAndStart())
        assertTrue(coordinator.replaceAndStart())

        assertEquals(
            listOf("first:start", "first:stop", "second:start"),
            events,
        )
    }

    @Test
    fun failedStartRemainsOwnedUntilTeardown() {
        val events = mutableListOf<String>()
        val coordinator = BackendLifecycleCoordinator {
            FakeBackend("failed", events, startResult = false)
        }

        assertFalse(coordinator.replaceAndStart())
        assertTrue(coordinator.stop())

        assertEquals(listOf("failed:start", "failed:stop"), events)
    }

    @Test
    fun stopIsIdempotent() {
        val events = mutableListOf<String>()
        val coordinator = BackendLifecycleCoordinator {
            FakeBackend("only", events)
        }

        assertTrue(coordinator.replaceAndStart())
        assertTrue(coordinator.stop())
        assertFalse(coordinator.stop())

        assertEquals(listOf("only:start", "only:stop"), events)
    }

    private class FakeBackend(
        private val name: String,
        private val events: MutableList<String>,
        private val startResult: Boolean = true,
    ) : ServiceBackend {
        override fun start(): Boolean {
            events += "$name:start"
            return startResult
        }

        override fun stop() {
            events += "$name:stop"
        }

        override fun update(packet: APRSPacket): String = name
    }
}
