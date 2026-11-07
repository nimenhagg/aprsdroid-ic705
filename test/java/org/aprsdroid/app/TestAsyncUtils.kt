package org.aprsdroid.app

import java.util.concurrent.locks.LockSupport
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
        LockSupport.parkNanos(1_000_000L)
    }
    if (!predicate()) {
        fail("Condition not met within ${timeoutMs}ms")
    }
}
