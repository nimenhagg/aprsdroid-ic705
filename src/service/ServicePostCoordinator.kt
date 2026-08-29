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
 * Owns the main-thread post-event policy previously embedded in AprsService.postAddPost().
 *
 * Android effects remain callbacks so this class only decides whether an event is queued,
 * preserves addPost-before-follow-up ordering, and selects the legacy follow-up action.
 */
internal class ServicePostCoordinator(
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

        postToMain {
            addPost(postType, statusId, message)
            when (postFollowUp(postType)) {
                PostFollowUp.SEND_PENDING_MESSAGES -> sendPendingMessages()
                PostFollowUp.STOP_SERVICE -> stopService()
                PostFollowUp.NONE -> Unit
            }
        }
    }
}
