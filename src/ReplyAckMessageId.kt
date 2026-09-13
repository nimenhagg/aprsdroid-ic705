package org.aprsdroid.app

internal fun nextReplyAckMessageId(lastUsed: Int?, pendingIds: Set<Int>): Int {
    var candidate = (((lastUsed ?: 0).coerceAtLeast(0)) % 99) + 1
    repeat(99) {
        if (candidate !in pendingIds) return candidate
        candidate = (candidate % 99) + 1
    }
    throw IllegalStateException("All APRS Reply-ACK message IDs are still pending")
}
