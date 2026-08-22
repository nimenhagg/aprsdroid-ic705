package org.aprsdroid.app.ic705.backend

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/** Handle for a pending backend-level reconnect attempt. */
fun interface Ic705RetryHandle {
    fun cancel()
}

/**
 * Scheduling boundary for backend-level reconnects.
 *
 * Kept independent from Android so retry/cancellation ordering can be covered by
 * plain JVM tests. Network selection itself remains the responsibility of the
 * injected socketFactoryProvider and is repeated for every attempt.
 */
interface Ic705ReconnectScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Ic705RetryHandle
    fun close()
}

/** Single daemon thread used only while an IC-705 backend instance is alive. */
class Ic705ExecutorReconnectScheduler : Ic705ReconnectScheduler {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ic705-backend-reconnect").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)

    override fun schedule(delayMillis: Long, action: () -> Unit): Ic705RetryHandle {
        check(!closed.get()) { "reconnect scheduler is closed" }
        val future = executor.schedule(action, delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        return Ic705RetryHandle { future.cancel(false) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }
}

/** Exponential reconnect delay with bounded symmetric jitter. */
class Ic705ReconnectBackoff(
    private val initialMillis: Long = 1_000L,
    private val maxMillis: Long = 30_000L,
    private val jitterFraction: Double = 0.20,
    private val randomUnit: () -> Double = { Random.nextDouble() },
) {
    init {
        require(initialMillis > 0L)
        require(maxMillis >= initialMillis)
        require(jitterFraction in 0.0..1.0)
    }

    fun delayMillis(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 30)
        val exponential = if (shift >= 62 || initialMillis > (Long.MAX_VALUE shr shift)) {
            Long.MAX_VALUE
        } else {
            initialMillis shl shift
        }
        val base = min(maxMillis, exponential)
        if (jitterFraction == 0.0) return base

        val unit = randomUnit().coerceIn(0.0, 1.0)
        val factor = 1.0 + ((unit * 2.0) - 1.0) * jitterFraction
        return (base.toDouble() * factor).roundToLong().coerceIn(0L, maxMillis)
    }
}
