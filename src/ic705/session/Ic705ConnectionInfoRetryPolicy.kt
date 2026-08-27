package org.aprsdroid.app.ic705.session

internal const val IC705_MAX_CONNECTION_INFO_ATTEMPTS = 4

internal enum class Ic705ConnectionInfoStatusDecision {
    IGNORE,
    RETRY_SAME_SESSION,
    REJECT_SESSION,
}

internal fun ic705ConnectionInfoStatusDecision(
    connectionInfoSent: Boolean,
    hasStreamEndpoints: Boolean,
    errorCode: Int,
    disconnectFlag: Int,
): Ic705ConnectionInfoStatusDecision {
    if (!connectionInfoSent || hasStreamEndpoints) {
        return Ic705ConnectionInfoStatusDecision.IGNORE
    }
    return if (errorCode == 0 && disconnectFlag == 0) {
        Ic705ConnectionInfoStatusDecision.RETRY_SAME_SESSION
    } else {
        Ic705ConnectionInfoStatusDecision.REJECT_SESSION
    }
}

internal enum class Ic705ConnectionInfoRetryDecision {
    IGNORE,
    RETRY,
    EXHAUSTED,
}

internal fun ic705ConnectionInfoRetryDecision(
    connectionInfoSent: Boolean,
    hasStreamEndpoints: Boolean,
    attempts: Int,
): Ic705ConnectionInfoRetryDecision {
    if (!connectionInfoSent || hasStreamEndpoints) {
        return Ic705ConnectionInfoRetryDecision.IGNORE
    }
    return if (attempts >= IC705_MAX_CONNECTION_INFO_ATTEMPTS) {
        Ic705ConnectionInfoRetryDecision.EXHAUSTED
    } else {
        Ic705ConnectionInfoRetryDecision.RETRY
    }
}
