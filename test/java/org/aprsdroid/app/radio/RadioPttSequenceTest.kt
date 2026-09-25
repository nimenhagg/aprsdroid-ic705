package org.aprsdroid.app.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPttSequenceTest {
    @Test
    fun audioRunsOnlyAfterPttReadbackAndPttIsReleased() {
        val radio = FakeRadio()
        var audioRuns = false

        val result = RadioPttSequence(radio).transmit {
            audioRuns = true
            assertTrue(radio.ptt)
        }

        assertEquals(RadioPttSequence.Result.Completed, result)
        assertTrue(audioRuns)
        assertTrue(!radio.ptt)
        assertEquals(listOf(true, false), radio.commands)
    }

    @Test
    fun audioIsNotStartedWhenPttOnCannotBeConfirmed() {
        val radio = FakeRadio(readback = false)
        var audioRuns = false

        val result = RadioPttSequence(radio).transmit {
            audioRuns = true
        }

        assertEquals(RadioPttSequence.Result.PttOnNotConfirmed, result)
        assertTrue(!audioRuns)
        assertTrue(!radio.ptt)
        assertEquals(listOf(true, false), radio.commands)
    }

    @Test
    fun audioFailureStillRequestsPttOff() {
        val radio = FakeRadio()

        val result = RadioPttSequence(radio).transmit {
            throw IllegalStateException("synthetic audio failure")
        }

        assertEquals(RadioPttSequence.Result.AudioFailed, result)
        assertTrue(!radio.ptt)
        assertEquals(listOf(true, false), radio.commands)
    }

    private class FakeRadio(
        private val readback: Boolean = true,
    ) : RadioControl {
        override val radio = RadioDescriptor(1, "Test", "Fake")
        override val capabilities = RadioCapabilities(canGetPtt = true, canSetPtt = true)
        override var isOpen: Boolean = true
        var ptt: Boolean = false
        val commands = mutableListOf<Boolean>()

        override fun open() = Unit
        override fun closePort() = Unit
        override fun isPttOn(): Boolean = ptt && readback
        override fun setPtt(on: Boolean) {
            commands += on
            ptt = on
        }
        override fun close() = Unit
    }
}
