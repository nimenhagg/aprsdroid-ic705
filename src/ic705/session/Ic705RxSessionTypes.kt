package org.aprsdroid.app.ic705.session

import java.net.InetAddress
import org.aprsdroid.app.ic705.transport.Ic705ChannelRole

data class Ic705RxSessionTiming(
    val discoveryPeriodMillis: Long = 500L,
    val discoveryTimeoutMillis: Long = 10_000L,
    /** RS-BA1 maintains each active LAN control channel at a 10 Hz cadence. */
    val pingPeriodMillis: Long = 100L,
    val idleCheckPeriodMillis: Long = 100L,
    val idleAfterMillis: Long = 100L,
    val tokenRenewalMillis: Long = 60_000L,
    val watchdogPeriodMillis: Long = 500L,
    /** CONTROL is authoritative for whole-session liveness. */
    val channelTimeoutMillis: Long = 5_000L,
    /** CI-V remains latency-sensitive, but is independent from RX audio silence. */
    val civChannelTimeoutMillis: Long = 3_000L,
    /** RX audio may legitimately be silent for long periods, especially around TX. */
    val audioChannelTimeoutMillis: Long = 30_000L,
    /** Give the radio time to resume RX audio after PTT OFF is acknowledged. */
    val audioPostTxGraceMillis: Long = 5_000L,
    /** How long a stream rediscovery attempt may wait for fresh traffic. */
    val streamRecoveryResponseMillis: Long = 3_000L,
    /** Stream-local recovery attempts before escalating to a full session reconnect. */
    val streamRecoveryAttempts: Int = 2,
    val handshakeStageTimeoutMillis: Long = 10_000L,
    val negotiationTimeoutMillis: Long = 45_000L,
    /** RS-BA1 waits about three seconds after login before claiming the streams. */
    val connectionInfoSettleMillis: Long = 3_000L,
    val connectionInfoRetryMillis: Long = 10_000L,
    val initialReconnectMillis: Long = 1_000L,
    val maximumReconnectMillis: Long = 30_000L,
) {
    init {
        val values = listOf(
            discoveryPeriodMillis,
            discoveryTimeoutMillis,
            pingPeriodMillis,
            idleCheckPeriodMillis,
            idleAfterMillis,
            tokenRenewalMillis,
            watchdogPeriodMillis,
            channelTimeoutMillis,
            civChannelTimeoutMillis,
            audioChannelTimeoutMillis,
            audioPostTxGraceMillis,
            streamRecoveryResponseMillis,
            handshakeStageTimeoutMillis,
            negotiationTimeoutMillis,
            connectionInfoSettleMillis,
            connectionInfoRetryMillis,
            initialReconnectMillis,
            maximumReconnectMillis,
        )
        require(values.all { it > 0 }) { "IC-705 timing values must be positive" }
        require(streamRecoveryAttempts > 0) { "streamRecoveryAttempts must be positive" }
        require(maximumReconnectMillis >= initialReconnectMillis)
    }
}

/** Credentials are deliberately excluded from [toString]. */
class Ic705RxSessionConfig(
    val radioAddress: InetAddress,
    val controlPort: Int,
    val username: String,
    private val password: String,
    val clientName: String = "APRSdroid",
    val autoReconnect: Boolean = true,
    val timing: Ic705RxSessionTiming = Ic705RxSessionTiming(),
) {
    init {
        require(controlPort in 1..0xffff) { "controlPort must be a valid UDP port" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(username.length <= 16) { "username must be at most 16 characters" }
        require(password.length <= 16) { "password must be at most 16 characters" }
        require(clientName.isNotBlank()) { "clientName must not be blank" }
        require(clientName.length <= 16) { "clientName must be at most 16 characters" }
        require(username.all { it.code <= 0x7f }) { "username must contain US-ASCII only" }
        require(password.all { it.code <= 0x7f }) { "password must contain US-ASCII only" }
        require(clientName.all { it.code <= 0x7f }) { "clientName must contain US-ASCII only" }
    }

    internal fun passwordValue(): String = password

    override fun toString(): String =
        "Ic705RxSessionConfig(radioAddress=$radioAddress, controlPort=$controlPort, " +
            "username=<redacted>, password=<redacted>, clientName=$clientName, " +
            "autoReconnect=$autoReconnect)"
}

enum class Ic705RxSessionIssueCode {
    SOCKET_IO,
    MALFORMED_PACKET,
    AUDIO_QUEUE_OVERFLOW,
}

enum class Ic705PacketReceiverKind {
    LOCAL,
    ZERO,
    OTHER,
    ABSENT,
}

enum class Ic705PacketRejectionKind {
    HEADER_TOO_SHORT,
    DECLARED_LENGTH_MISMATCH,
    RECEIVER_ZERO,
    RECEIVER_OTHER,
    PACKET_CODEC,
}

/** Credential-safe packet metadata for real-radio diagnostics; never contains payload bytes or IDs. */
data class Ic705PacketDiagnostic(
    val length: Int,
    val declaredLength: Int?,
    val commonType: Int?,
    val receiverKind: Ic705PacketReceiverKind,
    val payloadLength: Int?,
    val requestReply: Int?,
    val requestType: Int?,
    val rejection: Ic705PacketRejectionKind,
)

data class Ic705RxSessionIssue(
    val code: Ic705RxSessionIssueCode,
    val channel: Ic705ChannelRole?,
    val packet: Ic705PacketDiagnostic? = null,
)

enum class Ic705AudioResetReason {
    UDP_DISCONTINUITY,
    SESSION_RESTART,
    STREAM_RECOVERY,
    AUDIO_QUEUE_OVERFLOW,
}

data class Ic705AudioReset(
    val reason: Ic705AudioResetReason,
    val discontinuity: Ic705AudioDiscontinuity? = null,
)

enum class Ic705StreamRecoveryOutcome {
    STARTED,
    SUCCEEDED,
    ESCALATED,
}

data class Ic705StreamRecoveryEvent(
    val role: Ic705ChannelRole,
    val outcome: Ic705StreamRecoveryOutcome,
    val attempt: Int,
    val ageMillis: Long,
)

data class Ic705RxSessionCallbacks(
    val onStateChanged: (Ic705RxSessionEngine.State) -> Unit = {},
    val onIssue: (Ic705RxSessionIssue) -> Unit = {},
    val onAudioReset: (Ic705AudioReset) -> Unit = {},
    val onStreamRecovery: (Ic705StreamRecoveryEvent) -> Unit = {},
)

/**
 * Wire-level A/B profile used only by package-local hardware diagnostics.
 * Public application constructors always use [WFVIEW].
 */
internal enum class Ic705RxWireProfile(
    val initialTrackedSequence: Int,
    val initialAuthInnerSequence: Int,
    val randomizeTokenRequest: Boolean,
    val randomizeClientId: Boolean,
    val sendTrackedIdle: Boolean,
    val replyToUnknownRetransmit: Boolean,
    val readySequence: Int,
    val startPingBeforeReady: Boolean,
    val loginAdvancesAuthSequence: Boolean,
    val repeatReadyOnDuplicateDiscovery: Boolean,
) {
    WFVIEW(
        initialTrackedSequence = 1,
        initialAuthInnerSequence = 0x30,
        randomizeTokenRequest = true,
        randomizeClientId = true,
        sendTrackedIdle = true,
        replyToUnknownRetransmit = true,
        readySequence = 1,
        startPingBeforeReady = true,
        loginAdvancesAuthSequence = true,
        repeatReadyOnDuplicateDiscovery = true,
    ),
    RIGPLANE_DIAGNOSTIC(
        initialTrackedSequence = 0,
        initialAuthInnerSequence = 0,
        randomizeTokenRequest = false,
        randomizeClientId = false,
        sendTrackedIdle = false,
        replyToUnknownRetransmit = false,
        readySequence = 0,
        startPingBeforeReady = false,
        loginAdvancesAuthSequence = false,
        repeatReadyOnDuplicateDiscovery = false,
    ),
}
