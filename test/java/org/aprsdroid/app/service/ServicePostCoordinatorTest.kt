package org.aprsdroid.app.service

import org.aprsdroid.app.StorageDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePostCoordinatorTest {
    @Test
    fun infoPostIsSuppressedWhenConnectionLoggingIsDisabled() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events, loggingEnabled = false)

        coordinator.post(StorageDatabase.Companion.Post.TYPE_INFO, 10, "info")

        assertTrue(events.isEmpty())
    }

    @Test
    fun infoPostIsQueuedWhenConnectionLoggingIsEnabled() {
        assertFalse(
            shouldDispatchServicePost(
                StorageDatabase.Companion.Post.TYPE_INFO,
                connectionLoggingEnabled = false,
            )
        )
        assertTrue(
            shouldDispatchServicePost(
                StorageDatabase.Companion.Post.TYPE_INFO,
                connectionLoggingEnabled = true,
            )
        )
    }

    @Test
    fun infoLoggingDecisionIsTakenBeforeBackgroundExecution() {
        val events = mutableListOf<String>()
        var loggingEnabled = true
        var queuedTask: (() -> Unit)? = null
        val coordinator = ServicePostCoordinator(
            postToBackground = { task -> queuedTask = task },
            postToMain = { task -> task() },
            connectionLoggingEnabled = { loggingEnabled },
            addPost = { type, statusId, message ->
                events += "add:$type:$statusId:$message"
            },
            sendPendingMessages = { events += "pending" },
            stopService = { events += "stop" },
        )

        coordinator.post(StorageDatabase.Companion.Post.TYPE_INFO, 10, "info")
        loggingEnabled = false

        assertNotNull(queuedTask)
        queuedTask!!.invoke()
        assertEquals(listOf("add:1:10:info"), events)
    }

    @Test
    fun incomingPostPersistsInBackgroundBeforePendingMessageKickOnMain() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events, loggingEnabled = true)

        coordinator.post(StorageDatabase.Companion.Post.TYPE_INCMG, 20, "rx")

        assertEquals(
            listOf(
                "bg",
                "add:3:20:rx",
                "main",
                "pending",
            ),
            events,
        )
    }

    @Test
    fun errorPostPersistsInBackgroundBeforeServiceStopOnMain() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events, loggingEnabled = true)

        coordinator.post(StorageDatabase.Companion.Post.TYPE_ERROR, 30, "error")

        assertEquals(
            listOf(
                "bg",
                "add:2:30:error",
                "main",
                "stop",
            ),
            events,
        )
    }

    @Test
    fun normalPostHasNoFollowUp() {
        assertEquals(
            PostFollowUp.NONE,
            postFollowUp(StorageDatabase.Companion.Post.TYPE_POST),
        )
    }

    @Test
    fun postInfoDispatchesBackgroundDirectly() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events, loggingEnabled = false)

        coordinator.postInfo("direct-info")

        assertEquals(
            listOf(
                "bg",
                "add:1:null:direct-info",
            ),
            events,
        )
    }

    private fun coordinator(
        events: MutableList<String>,
        loggingEnabled: Boolean,
    ): ServicePostCoordinator {
        return ServicePostCoordinator(
            postToBackground = { task ->
                events += "bg"
                task()
            },
            postToMain = { task ->
                events += "main"
                task()
            },
            connectionLoggingEnabled = { loggingEnabled },
            addPost = { type, statusId, message ->
                events += "add:$type:$statusId:$message"
            },
            sendPendingMessages = { events += "pending" },
            stopService = { events += "stop" },
        )
    }
}
