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
}
