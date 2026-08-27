package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Test

class Ic705AudioSequencePolicyTest {
    @Test
    fun sequenceArithmeticWrapsAtSixteenBits() {
        assertEquals(0, incrementIc705AudioSequence(0xffff))
        assertEquals(1, ic705AudioSequenceDistance(0xffff, 0))
        assertEquals(0xffff, ic705AudioSequenceDistance(0, 0xffff))
    }

    @Test
    fun receivePacketSampleCountsPreserveCapturedAlternation() {
        assertEquals(682, ic705SamplesPerReceivePacket(0))
        assertEquals(278, ic705SamplesPerReceivePacket(1))
        assertEquals(682, ic705SamplesPerReceivePacket(2))
        assertEquals(278, ic705SamplesPerReceivePacket(0xffff))
    }
}
