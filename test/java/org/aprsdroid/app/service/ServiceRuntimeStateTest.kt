package org.aprsdroid.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRuntimeStateTest {
    @Test
    fun startedStateWritesThroughCompatibilityBacking() {
        var running = false
        var linkError = 7
        val state = state(
            readRunning = { running },
            writeRunning = { running = it },
            readLinkError = { linkError },
            writeLinkError = { linkError = it },
        )

        state.markStarted()

        assertTrue(running)
        assertTrue(state.isRunning)
        assertEquals(7, state.linkError)
    }

    @Test
    fun stoppedStateClearsRunningAndLinkError() {
        var running = true
        var linkError = 99
        val state = state(
            readRunning = { running },
            writeRunning = { running = it },
            readLinkError = { linkError },
            writeLinkError = { linkError = it },
        )

        state.markStopped()

        assertFalse(running)
        assertFalse(state.isRunning)
        assertEquals(0, linkError)
    }

    @Test
    fun stoppedStateWritesRunningFalseBeforeClearingLinkError() {
        val events = mutableListOf<String>()
        var running = true
        var linkError = 9
        val state = state(
            readRunning = { running },
            writeRunning = { value ->
                running = value
                events += "running:$value"
            },
            readLinkError = { linkError },
            writeLinkError = { value ->
                linkError = value
                events += "link:$value"
            },
        )

        state.markStopped()

        assertEquals(listOf("running:false", "link:0"), events)
    }

    @Test
    fun linkTransitionsDoNotChangeRunningState() {
        var running = true
        var linkError = 0
        val state = state(
            readRunning = { running },
            writeRunning = { running = it },
            readLinkError = { linkError },
            writeLinkError = { linkError = it },
        )

        state.markLinkOff(42)
        assertEquals(42, state.linkError)
        assertTrue(state.isRunning)

        state.markLinkOn()
        assertEquals(0, state.linkError)
        assertTrue(state.isRunning)
    }

    @Test
    fun readsRemainLiveAgainstExternalCompatibilityMutation() {
        var running = false
        var linkError = 0
        val state = state(
            readRunning = { running },
            writeRunning = { running = it },
            readLinkError = { linkError },
            writeLinkError = { linkError = it },
        )

        running = true
        linkError = 12

        assertTrue(state.isRunning)
        assertEquals(12, state.linkError)
    }

    private fun state(
        readRunning: () -> Boolean,
        writeRunning: (Boolean) -> Unit,
        readLinkError: () -> Int,
        writeLinkError: (Int) -> Unit,
    ): ServiceRuntimeState {
        return ServiceRuntimeState(
            readRunning = readRunning,
            writeRunning = writeRunning,
            readLinkError = readLinkError,
            writeLinkError = writeLinkError,
        )
    }
}
