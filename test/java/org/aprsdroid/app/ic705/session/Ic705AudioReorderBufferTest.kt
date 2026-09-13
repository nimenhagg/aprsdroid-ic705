package org.aprsdroid.app.ic705.session

import org.junit.Assert.assertEquals
import org.junit.Test

class Ic705AudioReorderBufferTest {
    @Test
    fun buffersShortForwardGapAndDrainsInOrder() {
        val writes = mutableListOf<Int>()
        val buffer = Ic705AudioReorderBuffer(
            writeSamples = { samples -> writes += samples.single().toInt() },
        )

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, buffer.accept(7, shortArrayOf(7)))
        assertEquals(Ic705AudioReceiveResult.BUFFERED, buffer.accept(9, shortArrayOf(9)))
        assertEquals(Ic705AudioReceiveResult.ACCEPTED, buffer.accept(8, shortArrayOf(8)))
        assertEquals(listOf(7, 8, 9), writes)
    }

    @Test
    fun resetClearsDuplicateAndSequenceState() {
        val writes = mutableListOf<Int>()
        val buffer = Ic705AudioReorderBuffer(
            writeSamples = { samples -> writes += samples.single().toInt() },
        )

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, buffer.accept(0xffff, shortArrayOf(1)))
        assertEquals(Ic705AudioReceiveResult.DUPLICATE_DROPPED, buffer.accept(0xffff, shortArrayOf(2)))

        buffer.reset()

        assertEquals(Ic705AudioReceiveResult.ACCEPTED, buffer.accept(0xffff, shortArrayOf(3)))
        assertEquals(listOf(1, 3), writes)
    }

    @Test
    fun reportsSevereGapWithoutConcealingIt() {
        val discontinuities = mutableListOf<Ic705AudioDiscontinuity>()
        val writes = mutableListOf<Int>()
        val buffer = Ic705AudioReorderBuffer(
            writeSamples = { samples -> writes += samples.last().toInt() },
            onDiscontinuity = discontinuities::add,
        )

        buffer.accept(7, shortArrayOf(7))
        assertEquals(Ic705AudioReceiveResult.ACCEPTED, buffer.accept(20, shortArrayOf(20)))

        assertEquals(
            Ic705AudioDiscontinuity(
                kind = Ic705AudioDiscontinuityKind.GAP,
                expectedSequence = 8,
                actualSequence = 20,
                missingPacketCount = 12,
            ),
            discontinuities.single(),
        )
        assertEquals(listOf(7, 20), writes)
    }

    @Test
    fun concealmentPrefersObservedPacketSizes() {
        val writes = mutableListOf<ShortArray>()
        val buffer = Ic705AudioReorderBuffer(
            writeSamples = { samples -> writes += samples.copyOf() },
        )

        // Teach the buffer that even packets contain 3 samples and odd packets 2.
        buffer.accept(0, shortArrayOf(10, 10, 10))
        buffer.accept(1, shortArrayOf(11, 11))

        // Leave sequence 2 missing. Once four pending packets accumulate, the
        // nearest packet is released with one concealed even packet in front.
        buffer.accept(3, shortArrayOf(13, 13))
        buffer.accept(4, shortArrayOf(14, 14, 14))
        buffer.accept(5, shortArrayOf(15, 15))
        buffer.accept(6, shortArrayOf(16, 16, 16))

        val concealed = writes[2]
        assertEquals(5, concealed.size)
        assertEquals(0, concealed[0].toInt())
        assertEquals(0, concealed[1].toInt())
        assertEquals(0, concealed[2].toInt())
        assertEquals(13, concealed[3].toInt())
        assertEquals(13, concealed[4].toInt())
    }
}
