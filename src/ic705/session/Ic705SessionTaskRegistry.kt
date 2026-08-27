package org.aprsdroid.app.ic705.session

import java.util.concurrent.ScheduledFuture

/** Owns keyed scheduled tasks for one IC-705 session generation. */
internal class Ic705SessionTaskRegistry {
    private val tasks = mutableMapOf<String, ScheduledFuture<*>>()

    fun replace(key: String, task: ScheduledFuture<*>) {
        tasks.put(key, task)?.cancel(false)
    }

    fun cancel(key: String) {
        tasks.remove(key)?.cancel(false)
    }

    fun cancelAll() {
        tasks.values.forEach { it.cancel(false) }
        tasks.clear()
    }
}
