// SPDX-License-Identifier: GPL-2.0-or-later
// See LICENSING.md: this file is additionally available under GPL-2.0-or-later.

package org.aprsdroid.app.ic705.session

import org.aprsdroid.app.ic705.transport.Ic705ChannelRole

internal data class Ic705ChannelIds(
    val localId: Int,
    val remoteId: Int? = null,
    val civSequence: Int = 0,
)

internal data class Ic705ChannelSnapshot(
    val txOuterSequence: Int = 1,
    val txAudioSequence: Int = 1,
    val channels: Map<Ic705ChannelRole, Ic705ChannelIds> = emptyMap(),
    val resetEpoch: Long = 0L,
) {
    fun resetChannels(): Ic705ChannelSnapshot = copy(
        channels = Ic705ChannelRole.values().associateWith { Ic705ChannelIds(localId = 0) },
        resetEpoch = resetEpoch + 1,
    )

    fun withTxSequences(outerSequence: Int, audioSequence: Int): Ic705ChannelSnapshot = copy(
        txOuterSequence = outerSequence,
        txAudioSequence = audioSequence,
    )

    fun withChannelIds(role: Ic705ChannelRole, ids: Ic705ChannelIds): Ic705ChannelSnapshot = copy(
        channels = channels + (role to ids),
    )
}
