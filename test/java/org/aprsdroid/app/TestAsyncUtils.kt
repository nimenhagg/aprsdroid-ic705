package org.aprsdroid.app

import org.junit.Assert.fail

internal inline fun awaitUntil(
    timeoutMs: Long = 3000L,
    crossinline predicate: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) {
            return
        }
        Thread.yield()
    }
    if (!predicate()) {
        fail("Condition not met within ${timeoutMs}ms")
    }
}
