package org.aprsdroid.app.ic705.session

private const val SESSION_REJECTED_COOLDOWN_MILLIS = 30_000L

internal enum class Ic705ConnectionInfoTimer(
    val taskKey: String,
    val conflictingTaskKey: String,
) {
    SETTLE(
        taskKey = "connection-info-settle",
        conflictingTaskKey = "connection-info-retry",
    ),
    RETRY(
        taskKey = "connection-info-retry",
        conflictingTaskKey = "connection-info-settle",
    ),
}

internal fun ic705ConnectionInfoTimerDelayMillis(
    timing: Ic705RxSessionTiming,
    timer: Ic705ConnectionInfoTimer,
): Long = when (timer) {
    Ic705ConnectionInfoTimer.SETTLE -> timing.connectionInfoSettleMillis
    Ic705ConnectionInfoTimer.RETRY -> timing.connectionInfoRetryMillis
}

internal fun ic705ConnectionInfoTimerEvent(
    timer: Ic705ConnectionInfoTimer,
): Ic705RxSessionEngine.Event = when (timer) {
    Ic705ConnectionInfoTimer.SETTLE -> Ic705RxSessionEngine.Event.ConnectionInfoSettleTimerFired
    Ic705ConnectionInfoTimer.RETRY -> Ic705RxSessionEngine.Event.ConnectionInfoRetryTimerFired
}

internal fun ic705ReconnectDelayMillis(
    timing: Ic705RxSessionTiming,
    attempt: Int,
    cooldown: Ic705RxSessionEngine.RetryCooldown,
): Long {
    var delay = timing.initialReconnectMillis
    repeat((attempt - 1).coerceAtLeast(0).coerceAtMost(30)) {
        delay = (delay * 2).coerceAtMost(timing.maximumReconnectMillis)
    }
    return maxOf(
        delay,
        when (cooldown) {
            Ic705RxSessionEngine.RetryCooldown.NORMAL -> 0L
            Ic705RxSessionEngine.RetryCooldown.SESSION_NOT_READY -> timing.connectionInfoRetryMillis
            Ic705RxSessionEngine.RetryCooldown.SESSION_REJECTED -> SESSION_REJECTED_COOLDOWN_MILLIS
        },
    )
}

internal fun ic705HandshakeTimeoutMillis(
    timing: Ic705RxSessionTiming,
    phase: Ic705RxSessionEngine.Phase,
): Long? = when (phase) {
    Ic705RxSessionEngine.Phase.OPENING_SOCKETS,
    Ic705RxSessionEngine.Phase.CONTROL_DISCOVERY,
    Ic705RxSessionEngine.Phase.AUTHENTICATING,
    Ic705RxSessionEngine.Phase.OPENING_STREAMS,
    Ic705RxSessionEngine.Phase.STREAMS_READY -> timing.handshakeStageTimeoutMillis

    Ic705RxSessionEngine.Phase.NEGOTIATING -> timing.negotiationTimeoutMillis

    Ic705RxSessionEngine.Phase.STOPPED,
    Ic705RxSessionEngine.Phase.RECEIVING,
    Ic705RxSessionEngine.Phase.RECONNECT_WAIT,
    Ic705RxSessionEngine.Phase.FAILED -> null
}
