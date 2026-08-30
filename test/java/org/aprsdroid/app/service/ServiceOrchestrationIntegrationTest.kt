package org.aprsdroid.app.service

import org.aprsdroid.app.StorageDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-seam regression coverage for the service orchestration extracted in Rounds 5-6.
 *
 * These tests intentionally model stopSelf() as a stop request and onDestroy() as a later
 * lifecycle phase. The service must persist the event before requesting stop, and runtime
 * compatibility state must only be cleared by the teardown phase.
 */
class ServiceOrchestrationIntegrationTest {
    @Test
    fun errorPostPersistsBeforeStopRequestAndTeardownClearsStateLater() {
        val events = mutableListOf<String>()
        var running = true
        var linkError = 42

        val runtimeState = ServiceRuntimeState(
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

        val postCoordinator = ServicePostCoordinator(
            postToMain = { task ->
                events += "queue"
                task()
            },
            connectionLoggingEnabled = { true },
            addPost = { type, statusId, message ->
                events += "add:$type:$statusId:$message"
            },
            sendPendingMessages = { events += "pending" },
            stopService = { events += "stop-request" },
        )

        postCoordinator.post(StorageDatabase.Companion.Post.TYPE_ERROR, 30, "error")

        assertEquals(
            listOf(
                "queue",
                "add:2:30:error",
                "stop-request",
            ),
            events,
        )
        assertTrue(runtimeState.isRunning)
        assertEquals(42, runtimeState.linkError)

        runtimeState.markStopped()

        assertEquals(
            listOf(
                "queue",
                "add:2:30:error",
                "stop-request",
                "running:false",
                "link:0",
            ),
            events,
        )
    }

    @Test
    fun incomingPostKicksPendingMessagesWithoutChangingRuntimeState() {
        val events = mutableListOf<String>()
        var running = true
        var linkError = 7

        val runtimeState = ServiceRuntimeState(
            readRunning = { running },
            writeRunning = { value -> running = value },
            readLinkError = { linkError },
            writeLinkError = { value -> linkError = value },
        )

        val postCoordinator = ServicePostCoordinator(
            postToMain = { task ->
                events += "queue"
                task()
            },
            connectionLoggingEnabled = { true },
            addPost = { type, statusId, message ->
                events += "add:$type:$statusId:$message"
            },
            sendPendingMessages = { events += "pending" },
            stopService = { events += "stop-request" },
        )

        postCoordinator.post(StorageDatabase.Companion.Post.TYPE_INCMG, 20, "rx")

        assertEquals(
            listOf(
                "queue",
                "add:3:20:rx",
                "pending",
            ),
            events,
        )
        assertTrue(runtimeState.isRunning)
        assertEquals(7, runtimeState.linkError)
    }
}
