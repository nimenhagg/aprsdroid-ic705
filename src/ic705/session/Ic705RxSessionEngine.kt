package org.aprsdroid.app.ic705.session

/**
 * Pure state reducer for bringing up the receive side of an IC-705 LAN session.
 *
 * This type deliberately owns no sockets, clocks, threads, Android objects, CI-V
 * commands, transmit audio, or PTT. A future transport executes [Action] values
 * and feeds the resulting [Event] values back into [reduce].
 */
object Ic705RxSessionEngine {
    enum class Phase {
        STOPPED,
        OPENING_SOCKETS,
        CONTROL_DISCOVERY,
        AUTHENTICATING,
        NEGOTIATING,
        OPENING_STREAMS,
        STREAMS_READY,
        RECEIVING,
        RECONNECT_WAIT,
        FAILED,
    }

    enum class RetryCooldown {
        NORMAL,
        SESSION_NOT_READY,
        SESSION_REJECTED,
    }

    data class StreamEndpoints(
        val civPort: Int,
        val audioPort: Int,
    ) {
        init {
            require(civPort in 1..65_535) { "Invalid CI-V port: $civPort" }
            require(audioPort in 1..65_535) { "Invalid audio port: $audioPort" }
        }
    }

    data class State(
        val phase: Phase = Phase.STOPPED,
        val retryAttempt: Int = 0,
        val failureReason: String? = null,
        internal val socketsOpened: Boolean = false,
        internal val discoverySent: Boolean = false,
        internal val controlDiscovered: Boolean = false,
        internal val controlReady: Boolean = false,
        internal val loginSent: Boolean = false,
        internal val loginAccepted: Boolean = false,
        internal val loginToken: Int? = null,
        internal val tokenSent: Boolean = false,
        internal val connectionRequestAuthorized: Boolean = false,
        internal val connectionInfoReceived: Boolean = false,
        internal val connectionInfoSettlePending: Boolean = false,
        internal val connectionInfoSent: Boolean = false,
        internal val connectionInfoAttempts: Int = 0,
        val streamEndpoints: StreamEndpoints? = null,
        internal val streamsOpenSent: Boolean = false,
        val civReady: Boolean = false,
        val audioReady: Boolean = false,
        val firstAudioSeen: Boolean = false,
    )

    sealed interface Event {
        data object Start : Event
        data object SocketsOpened : Event
        data object ControlDiscovered : Event
        data object ControlReady : Event
        data class LoginAccepted(val token: Int) : Event
        data class LoginRejected(val reason: String) : Event
        /** Radio supplied/confirmed the session identifiers needed for the first 0x90 request. */
        data object ConnectionRequestAuthorized : Event
        data class StatusEndpointsReceived(val endpoints: StreamEndpoints) : Event
        data class StatusNotReady(
            val errorCode: Int,
            val disconnectFlag: Int,
        ) : Event
        data object ConnectionInfoReceived : Event
        data object ConnectionInfoSettleTimerFired : Event
        data object ConnectionInfoRetryTimerFired : Event
        data object CivReady : Event
        data object AudioReady : Event
        data object FirstAudio : Event
        data class RecoverableFailure(val reason: String) : Event
        data object RetryTimerFired : Event
        data object RetryDisabled : Event
        data object Stop : Event
    }

    sealed interface Action {
        data object OpenSockets : Action
        data object CloseSockets : Action
        data object SendDiscovery : Action
        data object SendLogin : Action
        data class SendTokenConfirmation(val token: Int) : Action
        data object ScheduleConnectionInfoSettle : Action
        data object SendConnectionInfo : Action
        data object ScheduleConnectionInfoRetry : Action
        data object CancelConnectionInfoTimers : Action
        data class SendOpenStreams(val endpoints: StreamEndpoints) : Action
        data class ScheduleRetry(
            val attempt: Int,
            val cooldown: RetryCooldown = RetryCooldown.NORMAL,
        ) : Action
        data object CancelRetryTimer : Action
        data object NotifyAudioDiscontinuity : Action
    }

    data class Transition(
        val state: State,
        val actions: List<Action> = emptyList(),
    )

    fun reduce(state: State, event: Event): Transition {
        if (event is Event.Start) {
            return when (state.phase) {
                Phase.STOPPED, Phase.FAILED -> Transition(
                    state = freshAttempt(phase = Phase.OPENING_SOCKETS),
                    actions = listOf(Action.OpenSockets),
                )
                else -> unchanged(state)
            }
        }

        if (event is Event.Stop) return stop(state)

        if (state.phase == Phase.STOPPED || state.phase == Phase.FAILED) {
            return unchanged(state)
        }

        if (event is Event.RecoverableFailure) return recover(state, event.reason)

        if (state.phase == Phase.RECONNECT_WAIT) {
            return when (event) {
                Event.RetryTimerFired -> Transition(
                    state = freshAttempt(
                        phase = Phase.OPENING_SOCKETS,
                        retryAttempt = state.retryAttempt,
                        failureReason = state.failureReason,
                    ),
                    actions = listOf(Action.CancelRetryTimer, Action.OpenSockets),
                )
                Event.RetryDisabled -> Transition(state.copy(phase = Phase.FAILED))
                else -> unchanged(state)
            }
        }

        if (event is Event.StatusNotReady) {
            return when (
                ic705ConnectionInfoStatusDecision(
                    connectionInfoSent = state.connectionInfoSent,
                    hasStreamEndpoints = state.streamEndpoints != null,
                    errorCode = event.errorCode,
                    disconnectFlag = event.disconnectFlag,
                )
            ) {
                Ic705ConnectionInfoStatusDecision.IGNORE -> unchanged(state)
                Ic705ConnectionInfoStatusDecision.RETRY_SAME_SESSION -> Transition(
                    state = state.copy(failureReason = "radio session not ready"),
                    actions = listOf(Action.ScheduleConnectionInfoRetry),
                )
                Ic705ConnectionInfoStatusDecision.REJECT_SESSION -> recover(
                    state = state,
                    reason = "radio session allocation rejected",
                    cooldown = RetryCooldown.SESSION_REJECTED,
                )
            }
        }

        if (event is Event.ConnectionInfoRetryTimerFired) {
            return when (
                ic705ConnectionInfoRetryDecision(
                    connectionInfoSent = state.connectionInfoSent,
                    hasStreamEndpoints = state.streamEndpoints != null,
                    attempts = state.connectionInfoAttempts,
                )
            ) {
                Ic705ConnectionInfoRetryDecision.IGNORE -> unchanged(state)
                Ic705ConnectionInfoRetryDecision.EXHAUSTED -> recover(
                    state = state,
                    reason = "radio session not ready after connection-info retries",
                    cooldown = RetryCooldown.SESSION_NOT_READY,
                )
                Ic705ConnectionInfoRetryDecision.RETRY -> Transition(
                    state = state.copy(
                        connectionInfoAttempts = state.connectionInfoAttempts + 1,
                        failureReason = "waiting for radio session allocation",
                    ),
                    actions = listOf(
                        Action.SendConnectionInfo,
                        Action.ScheduleConnectionInfoRetry,
                    ),
                )
            }
        }

        val eventActions = mutableListOf<Action>()

        val updated = when (event) {
            Event.SocketsOpened -> state.copy(socketsOpened = true)
            Event.ControlDiscovered -> state.copy(controlDiscovered = true)
            Event.ControlReady -> state.copy(controlReady = true)
            is Event.LoginAccepted -> {
                if (!state.loginSent || state.loginAccepted) state
                else state.copy(loginAccepted = true, loginToken = event.token)
            }
            is Event.LoginRejected -> {
                if (!state.loginSent || state.loginAccepted) return unchanged(state)
                return Transition(
                    state = freshAttempt(
                        phase = Phase.FAILED,
                        retryAttempt = state.retryAttempt,
                        failureReason = event.reason,
                    ),
                    actions = listOf(Action.CloseSockets),
                )
            }
            Event.ConnectionRequestAuthorized -> state.copy(connectionRequestAuthorized = true)
            is Event.StatusEndpointsReceived -> {
                if (!state.connectionInfoSent || state.streamEndpoints == event.endpoints) {
                    state
                } else {
                    eventActions += Action.CancelConnectionInfoTimers
                    state.copy(
                        streamEndpoints = event.endpoints,
                        connectionInfoSettlePending = false,
                    )
                }
            }
            Event.ConnectionInfoReceived -> {
                if (state.streamEndpoints == null) {
                    eventActions += Action.ScheduleConnectionInfoSettle
                    state.copy(
                        connectionInfoReceived = true,
                        connectionInfoSettlePending = true,
                    )
                } else {
                    state
                }
            }
            Event.ConnectionInfoSettleTimerFired -> {
                if (
                    state.loginAccepted &&
                    state.connectionRequestAuthorized &&
                    state.connectionInfoReceived &&
                    state.connectionInfoSettlePending &&
                    state.streamEndpoints == null
                ) {
                    eventActions += Action.SendConnectionInfo
                    eventActions += Action.ScheduleConnectionInfoRetry
                    state.copy(
                        connectionInfoSettlePending = false,
                        connectionInfoSent = true,
                        connectionInfoAttempts = state.connectionInfoAttempts + 1,
                    )
                } else {
                    state
                }
            }
            Event.CivReady -> state.copy(civReady = true)
            Event.AudioReady -> state.copy(audioReady = true)
            // Reaching actual PCM is the success boundary for a reconnect attempt.
            // Do not let unrelated failures accumulated over a long-running session
            // permanently pin later reconnects to the maximum backoff.
            Event.FirstAudio -> state.copy(
                retryAttempt = 0,
                failureReason = null,
                firstAudioSeen = true,
            )
            Event.RetryTimerFired,
            Event.RetryDisabled,
            Event.Start,
            Event.Stop,
            is Event.StatusNotReady,
            Event.ConnectionInfoRetryTimerFired,
            is Event.RecoverableFailure -> state
        }

        val advanced = advance(updated)
        return advanced.copy(actions = eventActions + advanced.actions)
    }

    private fun advance(input: State): Transition {
        var state = input
        val actions = mutableListOf<Action>()

        if (!state.socketsOpened) return unchanged(state)

        if (!state.discoverySent) {
            state = state.copy(
                phase = Phase.CONTROL_DISCOVERY,
                discoverySent = true,
            )
            actions += Action.SendDiscovery
        }

        if (state.controlDiscovered && state.controlReady && !state.loginSent) {
            state = state.copy(
                phase = Phase.AUTHENTICATING,
                loginSent = true,
            )
            actions += Action.SendLogin
        }

        if (state.loginAccepted) {
            // Do not regress OPENING_STREAMS/STREAMS_READY/RECEIVING when a later
            // idempotent event causes the reducer to run again.
            if (!state.streamsOpenSent) state = state.copy(phase = Phase.NEGOTIATING)
            if (!state.tokenSent) {
                state = state.copy(tokenSent = true)
                actions += Action.SendTokenConfirmation(requireNotNull(state.loginToken))
            }
        }

        val endpoints = state.streamEndpoints
        if (
            state.loginAccepted &&
            state.connectionRequestAuthorized &&
            state.connectionInfoSent &&
            endpoints != null &&
            !state.streamsOpenSent
        ) {
            state = state.copy(
                phase = Phase.OPENING_STREAMS,
                streamsOpenSent = true,
            )
            actions += Action.SendOpenStreams(endpoints)
        }

        if (state.streamsOpenSent && state.civReady && state.audioReady) {
            state = state.copy(
                phase = if (state.firstAudioSeen) Phase.RECEIVING else Phase.STREAMS_READY,
            )
        }

        return Transition(state = state, actions = actions)
    }

    private fun recover(
        state: State,
        reason: String,
        cooldown: RetryCooldown = RetryCooldown.NORMAL,
    ): Transition {
        if (state.phase == Phase.RECONNECT_WAIT) return unchanged(state)

        val nextAttempt = state.retryAttempt + 1
        val actions = mutableListOf<Action>(Action.CloseSockets)
        if (state.hasLiveAudio()) actions += Action.NotifyAudioDiscontinuity
        actions += Action.ScheduleRetry(nextAttempt, cooldown)

        return Transition(
            state = freshAttempt(
                phase = Phase.RECONNECT_WAIT,
                retryAttempt = nextAttempt,
                failureReason = reason,
            ),
            actions = actions,
        )
    }

    private fun stop(state: State): Transition {
        if (state.phase == Phase.STOPPED) return unchanged(state)

        val actions = mutableListOf<Action>()
        if (state.phase == Phase.RECONNECT_WAIT) actions += Action.CancelRetryTimer
        if (state.phase != Phase.FAILED && state.phase != Phase.RECONNECT_WAIT) {
            actions += Action.CloseSockets
        }
        if (state.hasLiveAudio()) actions += Action.NotifyAudioDiscontinuity
        return Transition(state = State(), actions = actions)
    }

    private fun State.hasLiveAudio(): Boolean =
        audioReady || firstAudioSeen || phase == Phase.STREAMS_READY || phase == Phase.RECEIVING

    private fun freshAttempt(
        phase: Phase,
        retryAttempt: Int = 0,
        failureReason: String? = null,
    ): State = State(
        phase = phase,
        retryAttempt = retryAttempt,
        failureReason = failureReason,
    )

    private fun unchanged(state: State): Transition = Transition(state)
}
