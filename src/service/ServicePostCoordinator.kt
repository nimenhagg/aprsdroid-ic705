package org.aprsdroid.app.service

import org.aprsdroid.app.StorageDatabase

internal enum class PostFollowUp {
    NONE,
    SEND_PENDING_MESSAGES,
    STOP_SERVICE,
}

internal fun shouldDispatchServicePost(
    postType: Int,
    connectionLoggingEnabled: Boolean,
): Boolean {
    return postType != StorageDatabase.Companion.Post.TYPE_INFO || connectionLoggingEnabled
}

internal fun postFollowUp(postType: Int): PostFollowUp = when (postType) {
    StorageDatabase.Companion.Post.TYPE_INCMG -> PostFollowUp.SEND_PENDING_MESSAGES
    StorageDatabase.Companion.Post.TYPE_ERROR -> PostFollowUp.STOP_SERVICE
    else -> PostFollowUp.NONE
}

/**
 * Owns post-event persistence and follow-up dispatching.
 *
 * Ensures database insertion and log/position trimming run on a background
 * executor (postToBackground) instead of blocking the Android UI thread,
 * while sequencing service and UI lifecycle actions back to the main thread (postToMain).
 */
internal class ServicePostCoordinator(
    private val postToBackground: ((() -> Unit) -> Unit) = { task -> task() },
    private val postToMain: ((() -> Unit) -> Unit),
    private val connectionLoggingEnabled: () -> Boolean,
    private val addPost: (postType: Int, statusId: Int, message: String) -> Unit,
    private val sendPendingMessages: () -> Unit,
    private val stopService: () -> Unit,
) {
    fun post(postType: Int, statusId: Int, message: String) {
        if (!shouldDispatchServicePost(postType, connectionLoggingEnabled())) {
            return
        }

        postToBackground {
            addPost(postType, statusId, message)
            when (postFollowUp(postType)) {
                PostFollowUp.SEND_PENDING_MESSAGES -> postToMain { sendPendingMessages() }
                PostFollowUp.STOP_SERVICE -> postToMain { stopService() }
                PostFollowUp.NONE -> Unit
            }
        }
    }
}
