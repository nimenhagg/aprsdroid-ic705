package org.aprsdroid.app.ic705.session

import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705SessionTaskRegistryTest {
    @Test
    fun replacingAKeyCancelsOnlyThePreviousTaskWithoutInterruptingIt() {
        val registry = Ic705SessionTaskRegistry()
        val first = RecordingFuture()
        val second = RecordingFuture()

        registry.replace("watchdog", first)
        registry.replace("watchdog", second)

        assertTrue(first.isCancelled)
        assertEquals(false, first.mayInterruptIfRunning)
        assertFalse(second.isCancelled)
    }

    @Test
    fun cancelRemovesTheTaskSoASecondCancelDoesNothing() {
        val registry = Ic705SessionTaskRegistry()
        val task = RecordingFuture()

        registry.replace("retry", task)
        registry.cancel("retry")
        registry.cancel("retry")

        assertTrue(task.isCancelled)
        assertEquals(1, task.cancelCalls)
        assertEquals(false, task.mayInterruptIfRunning)
    }

    @Test
    fun cancelAllCancelsEveryRegisteredTaskWithoutInterrupting() {
        val registry = Ic705SessionTaskRegistry()
        val first = RecordingFuture()
        val second = RecordingFuture()

        registry.replace("ping", first)
        registry.replace("idle", second)
        registry.cancelAll()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertEquals(false, first.mayInterruptIfRunning)
        assertEquals(false, second.mayInterruptIfRunning)
    }

    private class RecordingFuture : ScheduledFuture<Unit> {
        var cancelCalls = 0
            private set
        var mayInterruptIfRunning: Boolean? = null
            private set
        private var cancelled = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCalls += 1
            this.mayInterruptIfRunning = mayInterruptIfRunning
            cancelled = true
            return true
        }

        override fun isCancelled(): Boolean = cancelled
        override fun isDone(): Boolean = cancelled
        override fun get(): Unit = Unit
        override fun get(timeout: Long, unit: TimeUnit): Unit = Unit
        override fun getDelay(unit: TimeUnit): Long = 0L
        override fun compareTo(other: Delayed): Int = 0
    }
}
