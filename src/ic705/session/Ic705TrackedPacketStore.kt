package org.aprsdroid.app.ic705.session

import java.util.LinkedHashMap
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705WireByteOrder

internal data class Ic705TrackedPacket(
    val sequence: Int,
    val data: ByteArray,
)

/** Assigns outer sequence numbers and retains immutable packets for Icom retransmit requests. */
internal class Ic705TrackedPacketStore(
    initialSequence: Int = 1,
    private val retentionMillis: Long = 10_000L,
    private val maxEntries: Int = 512,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    init {
        require(initialSequence in 0..0xffff)
        require(retentionMillis > 0)
        require(maxEntries > 0)
    }

    private data class Entry(val storedAtMillis: Long, val data: ByteArray)

    private val entries = LinkedHashMap<Int, Entry>()
    private var nextSequence = initialSequence
    private var lastTrackedAtMillis = monotonicMillis()

    @Synchronized
    fun track(template: ByteArray): Ic705TrackedPacket {
        require(template.size >= Ic705ControlPacketCodec.PACKET_SIZE) {
            "Tracked packet must contain the common Icom header"
        }
        val now = monotonicMillis()
        purge(now)
        val sequence = nextSequence
        nextSequence = (nextSequence + 1) and 0xffff
        val immutablePacket = template.copyOf()
        Ic705WireByteOrder.writeUInt16Le(immutablePacket, 0x06, sequence)
        entries[sequence] = Entry(now, immutablePacket)
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
        lastTrackedAtMillis = now
        return Ic705TrackedPacket(sequence, immutablePacket.copyOf())
    }

    /**
     * Removes a packet that never made it onto the UDP socket so a later radio
     * retransmit request cannot resurrect a locally failed command.
     *
     * The sequence number remains consumed. Rewinding it would be unsafe once
     * concurrent/successive packets may already have observed the next value.
     */
    @Synchronized
    fun discard(sequence: Int) {
        require(sequence in 0..0xffff)
        entries.remove(sequence)
    }

    @Synchronized
    fun find(sequence: Int): ByteArray? {
        require(sequence in 0..0xffff)
        purge(monotonicMillis())
        return entries[sequence]?.data?.copyOf()
    }

    @Synchronized
    fun millisSinceLastTracked(): Long =
        (monotonicMillis() - lastTrackedAtMillis).coerceAtLeast(0L)

    private fun purge(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.storedAtMillis > retentionMillis) {
                iterator.remove()
            }
        }
    }
}
