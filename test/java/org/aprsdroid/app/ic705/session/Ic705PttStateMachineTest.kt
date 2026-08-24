package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class Ic705PttStateMachineTest {

    private class FakePttActions : Ic705PttActions {
        val sentCivFrames = mutableListOf<ByteArray>()
        val sentAudioDatagrams = mutableListOf<ByteArray>()
        val stateHistory = mutableListOf<Ic705PttState>()
        var nextCivFailure: RuntimeException? = null

        override fun sendCivFrame(frame: ByteArray) {
            nextCivFailure?.let { failure ->
                nextCivFailure = null
                throw failure
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

        // 1. Begin Transmission
        assertTrue(sm.beginTransmission())
        assertEquals(Ic705PttState.TX_STREAMING, sm.state)
        assertTrue(sm.isTransmitting)
        assertTrue(sm.isRadioPttOn)
        assertEquals(1, actions.sentCivFrames.size)
        // Verify PTT ON byte in frame
        assertEquals(0x01.toByte(), actions.sentCivFrames[0][6])

        // 2. Audio streaming finished, start draining
        assertTrue(sm.onAudioStreamingFinished())
        assertEquals(Ic705PttState.DRAINING, sm.state)

        // 3. Finish transmission, send PTT OFF
        sm.finishTransmission()
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
        assertEquals(Ic705PttState.TX_STREAMING, sm.state)

        // Radio sends NAK
        sm.onCivReceived(nakFrame())
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isTransmitting)
        assertFalse(sm.isRadioPttOn)
    }

    @Test
    fun forceReleaseFromStreamingSendsPttOffAndResetsState() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        sm.beginTransmission()
        assertEquals(Ic705PttState.TX_STREAMING, sm.state)
        assertTrue(sm.isRadioPttOn)

        sm.forceRelease("Testing force release")
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isTransmitting)
        assertFalse(sm.isRadioPttOn)
        assertEquals(2, actions.sentCivFrames.size)
        assertEquals(0x00.toByte(), actions.sentCivFrames[1][6])
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
    fun failedPttOffKeepsTransmitStateUntilWatchdogCanRetry() {
        val actions = FakePttActions()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val sm = Ic705PttStateMachine(
            actions = actions,
            absoluteWatchdogMs = 60_000L,
            watchdogExecutor = watchdog,
        )

        try {
            assertTrue(sm.beginTransmission())
            assertTrue(sm.onAudioStreamingFinished())
            actions.nextCivFailure = IllegalStateException("simulated socket failure")

            assertThrows(IllegalStateException::class.java) {
                sm.finishTransmission()
            }
            assertEquals(Ic705PttState.DRAINING, sm.state)
            assertTrue(sm.isTransmitting)
            assertTrue(sm.isRadioPttOn)
        } finally {
            watchdog.shutdownNow()
        }
    }

    @Test
    fun failedForcedPttOffStillResetsState() {
        val actions = FakePttActions()
        val sm = Ic705PttStateMachine(actions)

        assertTrue(sm.beginTransmission())
        actions.nextCivFailure = IllegalStateException("simulated socket failure")

        sm.forceRelease("test failure cleanup")
        assertEquals(Ic705PttState.RX_IDLE, sm.state)
        assertFalse(sm.isTransmitting)
        assertFalse(sm.isRadioPttOn)
    }
}
