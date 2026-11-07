package org.aprsdroid.app

import org.junit.Assert.fail
import java.util.concurrent.locks.LockSupport

internal inline fun awaitUntil(
    timeoutMs: Long = 3000L,
    pollIntervalMs: Long = 5L,
    crossinline predicate: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) {
            return
        }
        Thread.onSpinWait()
        LockSupport.parkNanos(pollIntervalMs * 1_000_000L)
    }
    if (!predicate()) {
        fail("Condition not met within ${timeoutMs}ms")
    }
}

internal fun pauseMillis(millis: Long) {
    LockSupport.parkNanos(millis * 1_000_000L)
}