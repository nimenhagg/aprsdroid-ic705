package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Test

class Ic705AudioSequencePolicyTest {
    @Test
    fun sequenceArithmeticPreservesSixteenBitAndToleratesShortBoundaryRollover() {
        assertEquals(0, incrementIc705AudioSequence(0xffff))
        assertEquals(1, ic705AudioSequenceDistance(0xffff, 0))
        assertEquals(0xffff, ic705AudioSequenceDistance(0, 0xffff))

        // Field diagnostics show a plausible shorter counter rollover near
        // 0x4000. Tolerate that narrow boundary without globally changing the
        // normal 16-bit sequence arithmetic.
        assertEquals(1, ic705AudioSequenceDistance(0x3fff, 0))
        assertEquals(0, ic705AudioSequenceDistance(0x4000, 0))
        assertEquals(0xffff, ic705AudioSequenceDistance(0, 0x3fff))
    }

    @Test
    fun receivePacketFallbackSampleCountsMatchNegotiatedTwelveKhzRate() {
        assertEquals(171, ic705SamplesPerReceivePacket(0))
        assertEquals(69, ic705SamplesPerReceivePacket(1))
        assertEquals(171, ic705SamplesPerReceivePacket(2))
        assertEquals(69, ic705SamplesPerReceivePacket(3))
        assertEquals(240, ic705SamplesPerReceivePacket(0) + ic705SamplesPerReceivePacket(1))
    }
}
