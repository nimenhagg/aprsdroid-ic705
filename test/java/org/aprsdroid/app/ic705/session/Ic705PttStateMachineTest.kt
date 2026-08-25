package org.aprsdroid.app.ic705.session

import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705PttStateMachineTest {

    private class FakePttActions : Ic705PttActions {
        val sentCivFrames: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
        val sentAudioDatagrams = mutableListOf<ByteArray>()
        val stateHistory = mutableListOf<Ic705PttState>()
        val civSendAttempts = AtomicInteger(0)
        val remainingCivFailures = AtomicInteger(0)

        override fun sendCivFrame(frame: ByteArray) {
            civSendAttempts.incrementAndGet()
            while (true) {
                val remaining = remainingCivFailures.get()
                if (remaining <= 0) break
                if (remainingCivFailures.compareAndSet(remaining, remaining - 1)) {
                    throw IllegalStateException("simulated socket failure")
                }
            }
            sentCivFrames.add(frame.copyOf())
        }

        override fun sendAudioDatagram(datagram: ByteArray) {
            sentAudioDatagrams.add(datagram.copyOf())
        }

        override fun onStateChanged(state: Ic705PttState) {
            stateHistory.add(state)
        }
    }

    private fun waitUntil(timeoutMs: Long = 1_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(5L)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }

    private fun ackFrame(radioAddress: Int = 0xa4, controllerAddress: Int = 0xe0): ByteArray =
        byteArrayOf(0xfe.toByte(), 0xfe.toByte(), controllerAddress.toByte(), radioAddress.toByte(), 0xfb.toByte(), 0xfd.toByte())

    private fun nakFrame(radioAddress: Int = 0xa4, controllerAddress: Int = 0xe0): ByteArray =
        byteArrayOf(0xfe.toByte(), 0xfe.toByte(), controllerAddress.toByte(), radioAddress.toByte(), 0xfa.toByte(), 0xfd.toByte())

    @Test
    fun successfulTransmissionLifecycle() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isTransmitting)
        assertFalse(sm.isRadioPttOn)

        assertTrue(sm.beginTransmission())
        assertEquals(Ic705PttState.TX_STREAMING, sm.state)
        assertTrue(sm.isTransmitting)
        assertTrue(sm.isRadioPttOn)
        assertEquals(1, actions.sentCivFrames.size)
        assertEquals(0x01.toByte(), actions.sentCivFrames[0][6])

        assertTrue(sm.onAudioStreamingFinished())
        assertEquals(Ic705PttState.DRAINING, sm.state)

        sm.finishTransmission()
        assertEquals(Ic705PttState.DRAINING, sm.state)
        assertTrue(sm.isRadioPttOn)
        sm.onCivReceived(ackFrame())

        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isTransmitting)
        assertFalse(sm.isRadioPttOn)
        assertEquals(2, actions.sentCivFrames.size)
        assertEquals(0x00.toByte(), actions.sentCivFrames[1][6])
    }

    @Test
    fun radioNakForcesRelease() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertTrue(sm.beginTransmission())
        sm.onCivReceived(nakFrame())
        assertEquals(Ic705PttState.DRAINING, sm.state)
        assertTrue(sm.isRadioPttOn)

        sm.onCivReceived(ackFrame())
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isRadioPttOn)
    }

    @Test
    fun forceReleaseFromStreamingWaitsForOffAck() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertTrue(sm.beginTransmission())
        sm.forceRelease("Testing force release")

        assertEquals(Ic705PttState.DRAINING, sm.state)
        assertTrue(sm.isRadioPttOn)
        assertEquals(2, actions.sentCivFrames.size)
        assertEquals(0x00.toByte(), actions.sentCivFrames[1][6])

        sm.onCivReceived(ackFrame())
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isRadioPttOn)
    }

    @Test
    fun duplicateBeginTransmissionRejectedWhenAlreadyTransmitting() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertTrue(sm.beginTransmission())
        assertFalse(sm.beginTransmission())
        assertEquals(1, actions.sentCivFrames.size)
    }

    @Test
    fun failedPttOffKeepsTransmitStateUntilRetrySucceeds() {
        val actions = FakePttActions()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val sm = Ic705PttStateMachine(
            actions = actions,
            ackTimeoutMs = 10L,
            absoluteWatchdogMs = 60_000L,
            watchdogExecutor = watchdog,
        )

        try {
            assertTrue(sm.beginTransmission())
            assertTrue(sm.onAudioStreamingFinished())
            actions.remainingCivFailures.set(1)

            sm.finishTransmission()
            assertEquals(Ic705PttState.DRAINING, sm.state)
            assertTrue(sm.isRadioPttOn)

            waitUntil { actions.civSendAttempts.get() >= 3 }
            sm.onCivReceived(ackFrame())
            assertEquals(Ic705PttState.RX_IDLE, sm.state)
            assertFalse(sm.isRadioPttOn)
        } finally {
            watchdog.shutdownNow()
        }
    }

    @Test
    fun failedForcedPttOffRetainsAssertedStateAfterRetries() {
        val actions = FakePttActions()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val sm = Ic705PttStateMachine(
            actions = actions,
            ackTimeoutMs = 10L,
            maxReleaseAttempts = 3,
            watchdogExecutor = watchdog,
        )

        try {
            assertTrue(sm.beginTransmission())
            actions.remainingCivFailures.set(3)

            sm.forceRelease("test failure cleanup")
            waitUntil { actions.civSendAttempts.get() >= 4 }

            assertEquals(Ic705PttState.DRAINING, sm.state)
            assertTrue(sm.isTransmitting)
            assertTrue(sm.isRadioPttOn)
        } finally {
            watchdog.shutdownNow()
        }
    }

    @Test
    fun watchdogRetriesAfterAllLocalPttOffSendsFail() {
        val actions = FakePttActions()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val sm = Ic705PttStateMachine(
            actions = actions,
            ackTimeoutMs = 10L,
            absoluteWatchdogMs = 80L,
            maxReleaseAttempts = 3,
            watchdogExecutor = watchdog,
        )

        try {
            assertTrue(sm.beginTransmission())
            actions.remainingCivFailures.set(3)

            sm.finishTransmission()
            waitUntil { actions.civSendAttempts.get() >= 5 }

            assertEquals(Ic705PttState.DRAINING, sm.state)
            assertTrue(sm.isRadioPttOn)
            sm.onCivReceived(ackFrame())
            assertEquals(Ic705PttState.RX_IDLE, sm.state)
            assertFalse(sm.isRadioPttOn)
        } finally {
            watchdog.shutdownNow()
        }
    }

    @Test
    fun missingOffAckRetriesThenUsesCompatibilityFallback() {
        val actions = FakePttActions()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val sm = Ic705PttStateMachine(
            actions = actions,
            ackTimeoutMs = 10L,
            maxReleaseAttempts = 2,
            watchdogExecutor = watchdog,
        )

        try {
            assertTrue(sm.beginTransmission())
            assertTrue(sm.onAudioStreamingFinished())
            sm.finishTransmission()

            waitUntil { sm.state == Ic705PttState.RX_IDLE }
            assertEquals(3, actions.sentCivFrames.size)
            assertFalse(sm.isRadioPttOn)
        } finally {
            watchdog.shutdownNow()
        }
    }

    @Test
    fun offNakTriggersImmediateRetry() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertTrue(sm.beginTransmission())
        sm.onCivReceived(ackFrame())
        assertTrue(sm.onAudioStreamingFinished())
        sm.finishTransmission()

        sm.onCivReceived(nakFrame())
        assertEquals(3, actions.sentCivFrames.size)
        assertEquals(Ic705PttState.DRAINING, sm.state)

        sm.onCivReceived(ackFrame())
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isRadioPttOn)
    }
}
