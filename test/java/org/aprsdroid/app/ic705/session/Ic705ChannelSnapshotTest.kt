// SPDX-License-Identifier: GPL-2.0-or-later
// See LICENSING.md: this file is additionally available under GPL-2.0-or-later.

package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.transport.Ic705ChannelRole

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705ChannelSnapshotTest {
    @Test
    fun concurrentResetAndTransmitNeverRegressAudioSequence() {
        val snapshot = AtomicReference(
            Ic705ChannelSnapshot(channels = Ic705ChannelRole.values().associateWith {
                Ic705ChannelIds(localId = 1, remoteId = 2)
            }),
        )
        val packetizer = Ic705TxAudioPacketizer(
            senderId = 1,
            receiverId = 2,
            initialOuterSequence = 1,
            initialAudioSequence = 1,
            samplesPerPacket = 1,
        )
        val encoded = CountDownLatch(1)
        val allowCommit = CountDownLatch(1)
        val tx = Thread {
            val initial = snapshot.get()
            packetizer.packetize(shortArrayOf(1))
            encoded.countDown()
            allowCommit.await()
            snapshot.updateAndGet { current ->
                if (current.resetEpoch != initial.resetEpoch) current
                else current.withTxSequences(packetizer.outerSequence, packetizer.audioSequence)
            }
        }
        tx.start()
        encoded.await()
        snapshot.updateAndGet(Ic705ChannelSnapshot::resetChannels)
        allowCommit.countDown()
        tx.join()

        val result = snapshot.get()
        assertTrue(result.txAudioSequence >= 1)
        assertTrue(result.txOuterSequence >= 1)
        assertTrue(result.resetEpoch > 0)
        assertTrue(result.channels.values.all { it.remoteId == null })

        val next = Ic705TxAudioPacketizer(
            senderId = 1,
            receiverId = 2,
            initialOuterSequence = result.txOuterSequence,
            initialAudioSequence = result.txAudioSequence,
            samplesPerPacket = 1,
        )
        next.packetize(shortArrayOf(1))
        val committed = result.withTxSequences(next.outerSequence, next.audioSequence)
        assertTrue(committed.txAudioSequence > result.txAudioSequence)
        assertTrue(committed.txOuterSequence > result.txOuterSequence)
    }
}
