package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine.Action
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine.Event
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine.Phase
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine.State
import org.aprsdroid.app.ic705.session.Ic705RxSessionEngine.StreamEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705RxSessionEngineTest {
    private val endpoints = StreamEndpoints(civPort = 50_001, audioPort = 50_002)

    @Test
    fun happyPathFollowsReceiveOnlyHandshake() {
        var state = State()

        state = step(state, Event.Start, Phase.OPENING_SOCKETS, Action.OpenSockets)
        state = step(state, Event.SocketsOpened, Phase.CONTROL_DISCOVERY, Action.SendDiscovery)
        state = step(state, Event.ControlDiscovered, Phase.CONTROL_DISCOVERY)
        state = step(state, Event.ControlReady, Phase.AUTHENTICATING, Action.SendLogin)
        state = step(
            state,
            Event.LoginAccepted(token = 0x12345678),
            Phase.NEGOTIATING,
            Action.SendTokenConfirmation(0x12345678),
        )
        state = step(
            state,
            Event.ConnectionRequestAuthorized,
            Phase.NEGOTIATING,
        )
        state = step(
            state,
            Event.ConnectionInfoReceived,
            Phase.NEGOTIATING,
            Action.ScheduleConnectionInfoSettle,
        )
        state = step(
            state,
            Event.ConnectionInfoSettleTimerFired,
            Phase.NEGOTIATING,
            Action.SendConnectionInfo,
            Action.ScheduleConnectionInfoRetry,
        )
        state = step(
            state,
            Event.StatusEndpointsReceived(endpoints),
            Phase.OPENING_STREAMS,
            Action.CancelConnectionInfoTimers,
            Action.SendOpenStreams(endpoints),
        )
        state = step(state, Event.CivReady, Phase.OPENING_STREAMS)
        state = step(state, Event.AudioReady, Phase.STREAMS_READY)
        state = step(state, Event.FirstAudio, Phase.RECEIVING)

        assertTrue(state.firstAudioSeen)
    }

    @Test
    fun repeatedAndOutOfOrderControlEventsAreIdempotent() {
        var state = State()
        state = reduce(state, Event.Start).state

        state = step(state, Event.ControlReady, Phase.OPENING_SOCKETS)
        state = step(state, Event.ControlReady, Phase.OPENING_SOCKETS)
        state = step(state, Event.ControlDiscovered, Phase.OPENING_SOCKETS)
        state = step(state, Event.SocketsOpened, Phase.AUTHENTICATING, Action.SendDiscovery, Action.SendLogin)

        state = step(state, Event.ControlDiscovered, Phase.AUTHENTICATING)
        state = step(state, Event.ControlReady, Phase.AUTHENTICATING)
        step(state, Event.SocketsOpened, Phase.AUTHENTICATING)
    }

    @Test
    fun outOfOrderNegotiationFactsAdvanceOncePrerequisitesArrive() {
        var state = authenticatedState()

        state = step(
            state,
            Event.ConnectionInfoReceived,
            Phase.AUTHENTICATING,
            Action.ScheduleConnectionInfoSettle,
        )
        state = step(
            state,
            Event.StatusEndpointsReceived(endpoints),
            Phase.AUTHENTICATING,
        )
        state = step(state, Event.ConnectionRequestAuthorized, Phase.AUTHENTICATING)
        state = step(
            state,
            Event.LoginAccepted(token = 99),
            Phase.NEGOTIATING,
            Action.SendTokenConfirmation(99),
        )
        state = step(
            state,
            Event.ConnectionInfoSettleTimerFired,
            Phase.NEGOTIATING,
            Action.SendConnectionInfo,
            Action.ScheduleConnectionInfoRetry,
        )
        state = step(
            state,
            Event.StatusEndpointsReceived(endpoints),
            Phase.OPENING_STREAMS,
            Action.CancelConnectionInfoTimers,
            Action.SendOpenStreams(endpoints),
        )

        state = step(state, Event.LoginAccepted(token = 100), Phase.OPENING_STREAMS)
        state = step(state, Event.ConnectionRequestAuthorized, Phase.OPENING_STREAMS)
        state = step(state, Event.ConnectionInfoReceived, Phase.OPENING_STREAMS)
        step(state, Event.StatusEndpointsReceived(endpoints), Phase.OPENING_STREAMS)
    }

    @Test
    fun recoverableFailureClosesNotifiesAndRetriesWithFreshFacts() {
        var state = receivingState()

        var transition = reduce(state, Event.RecoverableFailure("audio timeout"))
        assertEquals(Phase.RECONNECT_WAIT, transition.state.phase)
        assertEquals(1, transition.state.retryAttempt)
        assertEquals("audio timeout", transition.state.failureReason)
        assertEquals(
            listOf(
                Action.CloseSockets,
                Action.NotifyAudioDiscontinuity,
                Action.ScheduleRetry(attempt = 1),
            ),
            transition.actions,
        )

        state = transition.state
        state = step(state, Event.RecoverableFailure("duplicate"), Phase.RECONNECT_WAIT)
        transition = reduce(state, Event.RetryTimerFired)
        assertEquals(Phase.OPENING_SOCKETS, transition.state.phase)
        assertEquals(1, transition.state.retryAttempt)
        assertEquals(listOf(Action.CancelRetryTimer, Action.OpenSockets), transition.actions)
        assertFalse(transition.state.civReady)
        assertFalse(transition.state.audioReady)
        assertFalse(transition.state.firstAudioSeen)
        assertEquals(null, transition.state.streamEndpoints)
    }

    @Test
    fun duplicateAnnouncementsAreCoalescedUntilTheSettleTimerFires() {
        var state = authenticatedState()
        state = reduce(state, Event.LoginAccepted(token = 1)).state
        state = reduce(state, Event.ConnectionRequestAuthorized).state

        state = step(
            state,
            Event.ConnectionInfoReceived,
            Phase.NEGOTIATING,
            Action.ScheduleConnectionInfoSettle,
        )
        state = step(
            state,
            Event.ConnectionInfoReceived,
            Phase.NEGOTIATING,
            Action.ScheduleConnectionInfoSettle,
        )
        assertEquals(0, state.connectionInfoAttempts)

        state = step(
            state,
            Event.ConnectionInfoSettleTimerFired,
            Phase.NEGOTIATING,
            Action.SendConnectionInfo,
            Action.ScheduleConnectionInfoRetry,
        )
        assertEquals(1, state.connectionInfoAttempts)
    }

    @Test
    fun zeroPortStatusRetriesSameSessionThenReleasesBeforeCooldown() {
        var state = authenticatedState()
        state = reduce(state, Event.LoginAccepted(token = 1)).state
        state = reduce(state, Event.ConnectionRequestAuthorized).state
        state = reduce(state, Event.ConnectionInfoReceived).state
        state = reduce(state, Event.ConnectionInfoSettleTimerFired).state

        var transition = reduce(state, Event.StatusNotReady(errorCode = 0, disconnectFlag = 0))
        assertEquals(listOf(Action.ScheduleConnectionInfoRetry), transition.actions)

        repeat(3) {
            transition = reduce(transition.state, Event.ConnectionInfoRetryTimerFired)
            assertEquals(
                listOf(Action.SendConnectionInfo, Action.ScheduleConnectionInfoRetry),
                transition.actions,
            )
        }
        transition = reduce(transition.state, Event.ConnectionInfoRetryTimerFired)
        assertEquals(Phase.RECONNECT_WAIT, transition.state.phase)
        assertEquals(
            listOf(
                Action.CloseSockets,
                Action.ScheduleRetry(1, Ic705RxSessionEngine.RetryCooldown.SESSION_NOT_READY),
            ),
            transition.actions,
        )
    }

    @Test
    fun explicitSessionRejectionReleasesImmediatelyAndUsesLongCooldown() {
        var state = authenticatedState()
        state = reduce(state, Event.LoginAccepted(token = 1)).state
        state = reduce(state, Event.ConnectionRequestAuthorized).state
        state = reduce(state, Event.ConnectionInfoReceived).state
        state = reduce(state, Event.ConnectionInfoSettleTimerFired).state

        val transition = reduce(
            state,
            Event.StatusNotReady(errorCode = -1, disconnectFlag = 0),
        )

        assertEquals(Phase.RECONNECT_WAIT, transition.state.phase)
        assertEquals(
            listOf(
                Action.CloseSockets,
                Action.ScheduleRetry(1, Ic705RxSessionEngine.RetryCooldown.SESSION_REJECTED),
            ),
            transition.actions,
        )
    }

    @Test
    fun authenticationRejectionIsTerminalUntilManualStart() {
        var state = authenticatedState()
        var transition = reduce(state, Event.LoginRejected("bad credentials"))

        assertEquals(Phase.FAILED, transition.state.phase)
        assertEquals("bad credentials", transition.state.failureReason)
        assertEquals(listOf(Action.CloseSockets), transition.actions)

        transition = reduce(transition.state, Event.RecoverableFailure("socket closed"))
        assertEquals(Phase.FAILED, transition.state.phase)
        assertTrue(transition.actions.isEmpty())

        transition = reduce(transition.state, Event.RetryTimerFired)
        assertEquals(Phase.FAILED, transition.state.phase)
        assertTrue(transition.actions.isEmpty())

        transition = reduce(transition.state, Event.Start)
        assertEquals(Phase.OPENING_SOCKETS, transition.state.phase)
        assertEquals(listOf(Action.OpenSockets), transition.actions)
    }

    @Test
    fun stopCancelsRetryAndMakesLaterTimerEventHarmless() {
        var transition = reduce(
            reduce(State(), Event.Start).state,
            Event.RecoverableFailure("network lost"),
        )
        assertEquals(Phase.RECONNECT_WAIT, transition.state.phase)

        transition = reduce(transition.state, Event.Stop)
        assertEquals(Phase.STOPPED, transition.state.phase)
        assertEquals(listOf(Action.CancelRetryTimer), transition.actions)

        transition = reduce(transition.state, Event.RetryTimerFired)
        assertEquals(Phase.STOPPED, transition.state.phase)
        assertTrue(transition.actions.isEmpty())
    }

    @Test
    fun disabledReconnectEndsInFailedInsteadOfWaitingForANonexistentTimer() {
        var transition = reduce(
            reduce(State(), Event.Start).state,
            Event.RecoverableFailure("network lost"),
        )
        assertEquals(Phase.RECONNECT_WAIT, transition.state.phase)

        transition = reduce(transition.state, Event.RetryDisabled)

        assertEquals(Phase.FAILED, transition.state.phase)
        assertEquals("network lost", transition.state.failureReason)
        assertTrue(transition.actions.isEmpty())
    }

    @Test
    fun firstAudioAfterReconnectResetsRetryBackoff() {
        var state = receivingState()
        state = reduce(state, Event.RecoverableFailure("audio timeout")).state
        assertEquals(1, state.retryAttempt)

        state = reduce(state, Event.RetryTimerFired).state
        state = reduce(state, Event.SocketsOpened).state
        state = reduce(state, Event.ControlDiscovered).state
        state = reduce(state, Event.ControlReady).state
        state = reduce(state, Event.LoginAccepted(token = 2)).state
        state = reduce(state, Event.ConnectionRequestAuthorized).state
        state = reduce(state, Event.ConnectionInfoReceived).state
        state = reduce(state, Event.ConnectionInfoSettleTimerFired).state
        state = reduce(state, Event.StatusEndpointsReceived(endpoints)).state
        state = reduce(state, Event.CivReady).state
        state = reduce(state, Event.AudioReady).state

        assertEquals(Phase.STREAMS_READY, state.phase)
        assertEquals(1, state.retryAttempt)
        assertEquals("audio timeout", state.failureReason)

        state = reduce(state, Event.FirstAudio).state

        assertEquals(Phase.RECEIVING, state.phase)
        assertEquals(0, state.retryAttempt)
        assertEquals(null, state.failureReason)
    }

    private fun authenticatedState(): State {
        var state = State()
        state = reduce(state, Event.Start).state
        state = reduce(state, Event.SocketsOpened).state
        state = reduce(state, Event.ControlDiscovered).state
        state = reduce(state, Event.ControlReady).state
        assertEquals(Phase.AUTHENTICATING, state.phase)
        return state
    }

    private fun receivingState(): State {
        var state = authenticatedState()
        state = reduce(state, Event.LoginAccepted(token = 1)).state
        state = reduce(state, Event.ConnectionRequestAuthorized).state
        state = reduce(state, Event.ConnectionInfoReceived).state
        state = reduce(state, Event.ConnectionInfoSettleTimerFired).state
        state = reduce(state, Event.StatusEndpointsReceived(endpoints)).state
        state = reduce(state, Event.CivReady).state
        state = reduce(state, Event.AudioReady).state
        state = reduce(state, Event.FirstAudio).state
        assertEquals(Phase.RECEIVING, state.phase)
        return state
    }

    private fun step(
        state: State,
        event: Event,
        expectedPhase: Phase,
        vararg expectedActions: Action,
    ): State {
        val transition = reduce(state, event)
        assertEquals(expectedPhase, transition.state.phase)
        assertEquals(expectedActions.toList(), transition.actions)
        return transition.state
    }

    private fun reduce(state: State, event: Event) = Ic705RxSessionEngine.reduce(state, event)
}
