package org.aprsdroid.app.ic705.session

import java.net.Inet4Address
import java.net.InetAddress
import org.aprsdroid.app.ic705.transport.Ic705ChannelRole

/** wfview encodes the route IPv4 suffix and bound UDP port into each client ID. */
internal fun ic705ClientIdForEndpoint(localAddress: InetAddress?, localPort: Int): Int {
    require(localPort in 1..0xffff) { "localPort must be a bound UDP port" }
    if (localAddress is Inet4Address && !localAddress.isAnyLocalAddress) {
        val octets = localAddress.address
        return ((octets[2].toInt() and 0xff) shl 24) or
            ((octets[3].toInt() and 0xff) shl 16) or
            localPort
    }
    // rigplane's endpoint-derived form is the safe fallback for socket providers
    // that cannot expose the selected route's concrete IPv4.
    return 0x0001_0000 or localPort
}

internal fun shouldSendIc705TrackedIdle(
    millisSinceLastTracked: Long,
    idleAfterMillis: Long,
): Boolean = millisSinceLastTracked >= idleAfterMillis

internal fun ic705ChannelWatchdogTimeoutMillis(
    timing: Ic705RxSessionTiming,
    role: Ic705ChannelRole,
): Long = when (role) {
    Ic705ChannelRole.CONTROL -> timing.channelTimeoutMillis
    Ic705ChannelRole.CIV -> timing.civChannelTimeoutMillis
    Ic705ChannelRole.AUDIO -> timing.audioChannelTimeoutMillis
}

internal fun shouldSuppressIc705AudioWatchdog(
    pttPossiblyAsserted: Boolean,
    nowMillis: Long,
    graceUntilMillis: Long,
): Boolean = pttPossiblyAsserted || nowMillis < graceUntilMillis

internal enum class Ic705WatchdogDecision {
    HEALTHY,
    WAIT_FOR_SOFT_RECOVERY,
    START_SOFT_RECOVERY,
    RETRY_SOFT_RECOVERY,
    ESCALATE,
}

internal fun ic705WatchdogDecision(
    role: Ic705ChannelRole,
    ageMillis: Long,
    timeoutMillis: Long,
    pttPossiblyAsserted: Boolean,
    activeRecoveryAttempt: Int?,
    recoveryDeadlineReached: Boolean,
    maxSoftRecoveryAttempts: Int,
): Ic705WatchdogDecision {
    if (ageMillis <= timeoutMillis) return Ic705WatchdogDecision.HEALTHY
    if (role == Ic705ChannelRole.CONTROL) return Ic705WatchdogDecision.ESCALATE
    if (role == Ic705ChannelRole.CIV && pttPossiblyAsserted) {
        return Ic705WatchdogDecision.ESCALATE
    }
    if (activeRecoveryAttempt == null) return Ic705WatchdogDecision.START_SOFT_RECOVERY
    if (!recoveryDeadlineReached) return Ic705WatchdogDecision.WAIT_FOR_SOFT_RECOVERY
    return if (activeRecoveryAttempt < maxSoftRecoveryAttempts) {
        Ic705WatchdogDecision.RETRY_SOFT_RECOVERY
    } else {
        Ic705WatchdogDecision.ESCALATE
    }
}
