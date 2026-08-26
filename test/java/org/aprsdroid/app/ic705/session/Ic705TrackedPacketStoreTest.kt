package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.protocol.Ic705ControlPacket
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705WireByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ic705TrackedPacketStoreTest {
    @Test
    fun assignsSequenceAndKeepsImmutableRetransmitCopy() {
        var now = 100L
        val store = Ic705TrackedPacketStore(monotonicMillis = { now })
        val template = controlTemplate()

        val tracked = store.track(template)
        template[0] = 0
        tracked.data[0] = 0

        assertEquals(1, tracked.sequence)
        val cached = store.find(1)!!
        assertEquals(1, Ic705WireByteOrder.readUInt16Le(cached, 0x06))
        assertEquals(Ic705ControlPacketCodec.PACKET_SIZE, cached[0].toInt() and 0xff)
        assertEquals(0, cached[1].toInt() and 0xff)
    }

    @Test
    fun expiresPacketsAndWrapsUnsignedSequence() {
        var now = 100L
        val store = Ic705TrackedPacketStore(
            initialSequence = 0xffff,
            retentionMillis = 10,
            monotonicMillis = { now },
        )

        assertEquals(0xffff, store.track(controlTemplate()).sequence)
        assertEquals(0, store.track(controlTemplate()).sequence)
        now = 111L
        assertNull(store.find(0xffff))
        assertNull(store.find(0))
    }

    @Test
    fun returnedRetransmitBytesCannotMutateTheStore() {
        val store = Ic705TrackedPacketStore()
        val expected = store.track(controlTemplate()).data
        val firstRead = store.find(1)!!
        firstRead.fill(0)

        assertArrayEquals(expected, store.find(1))
    }

    @Test
    fun discardRemovesLocallyUnsentPacketWithoutReusingSequence() {
        val store = Ic705TrackedPacketStore()
        val failed = store.track(controlTemplate())
        store.discard(failed.sequence)

        assertNull(store.find(failed.sequence))
        assertEquals(2, store.track(controlTemplate()).sequence)
    }

    private fun controlTemplate() = Ic705ControlPacketCodec.encode(
        Ic705ControlPacket(
            type = Ic705ControlPacketCodec.TYPE_NULL,
            sequence = 0,
            senderId = 0x11223344,
            receiverId = 0x55667788,
        ),
    )
}
