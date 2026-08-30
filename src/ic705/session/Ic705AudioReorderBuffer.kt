package org.aprsdroid.app.ic705.session

/**
 * Keeps IC-705 receive audio in sequence while absorbing a small amount of UDP jitter.
 *
 * Thread safety is provided by the owning [Ic705RxAudioReceiver].
 */
internal class Ic705AudioReorderBuffer(
    private val writeSamples: (ShortArray) -> Unit,
    private val onDiscontinuity: (Ic705AudioDiscontinuity) -> Unit = {},
) {
    private var nextAudioSequence: Int? = null
    private var lastDeliveredSequence: Int? = null
    private val pendingAudio = mutableMapOf<Int, ShortArray>()
    private val observedSampleCountsByParity = IntArray(2)

    fun accept(sequence: Int, samples: ShortArray): Ic705AudioReceiveResult {
        if (lastDeliveredSequence == sequence || pendingAudio.containsKey(sequence)) {
            return Ic705AudioReceiveResult.DUPLICATE_DROPPED
        }

        val expectedSequence = nextAudioSequence
        if (expectedSequence == null) {
            deliver(sequence, samples)
            return Ic705AudioReceiveResult.ACCEPTED
        }

        val forwardDistance = ic705AudioSequenceDistance(expectedSequence, sequence)
        return when {
            forwardDistance == 0 -> {
                deliver(sequence, samples)
                drainContiguousPending()
                Ic705AudioReceiveResult.ACCEPTED
            }
            forwardDistance >= IC705_AUDIO_HALF_SEQUENCE_SPACE -> {
                // This packet arrived after its place in the output stream. The gap was
                // already handled when newer audio was released, so resetting again here
                // would turn one network discontinuity into two demodulator resets.
                Ic705AudioReceiveResult.OUT_OF_ORDER_DROPPED
            }
            forwardDistance > IC705_AUDIO_MAX_REORDER_PACKETS -> {
                reportGapAndDeliver(expectedSequence, sequence, samples)
                drainContiguousPending()
                Ic705AudioReceiveResult.ACCEPTED
            }
            else -> {
                pendingAudio[sequence] = samples
                if (pendingAudio.size >= IC705_AUDIO_MAX_REORDER_PACKETS) {
                    releaseNearestPending(expectedSequence)
                    Ic705AudioReceiveResult.ACCEPTED
                } else {
                    Ic705AudioReceiveResult.BUFFERED
                }
            }
        }
    }

    fun reset() {
        nextAudioSequence = null
        lastDeliveredSequence = null
        pendingAudio.clear()
        observedSampleCountsByParity.fill(0)
    }

    private fun deliver(
        sequence: Int,
        samples: ShortArray,
        observedSampleCount: Int = samples.size,
    ) {
        writeSamples(samples)
        if (observedSampleCount > 0) {
            observedSampleCountsByParity[sequence and 1] = observedSampleCount
        }
        lastDeliveredSequence = sequence
        nextAudioSequence = incrementIc705AudioSequence(sequence)
    }

    private fun drainContiguousPending() {
        while (true) {
            val expected = nextAudioSequence ?: return
            val samples = pendingAudio.remove(expected) ?: return
            deliver(expected, samples)
        }
    }

    private fun releaseNearestPending(expectedSequence: Int) {
        val sequence = pendingAudio.keys.minByOrNull { ic705AudioSequenceDistance(expectedSequence, it) } ?: return
        val samples = pendingAudio.remove(sequence) ?: return
        reportGapAndDeliver(expectedSequence, sequence, samples)
        drainContiguousPending()
    }

    private fun reportGapAndDeliver(
        expectedSequence: Int,
        actualSequence: Int,
        samples: ShortArray,
    ) {
        val missingPacketCount = ic705AudioSequenceDistance(expectedSequence, actualSequence)
        if (missingPacketCount <= IC705_AUDIO_MAX_CONCEALED_PACKETS) {
            val concealedSampleCount = (0 until missingPacketCount).sumOf { offset ->
                samplesForConcealment((expectedSequence + offset) and 0xffff)
            }
            val concealed = ShortArray(concealedSampleCount + samples.size)
            samples.copyInto(concealed, destinationOffset = concealedSampleCount)
            // Preserve the real packet size as the learned observation. Otherwise
            // a concealed packet would teach the buffer an inflated size and make
            // later loss concealment progressively worse.
            deliver(actualSequence, concealed, observedSampleCount = samples.size)
            return
        }
        onDiscontinuity(
            Ic705AudioDiscontinuity(
                kind = Ic705AudioDiscontinuityKind.GAP,
                expectedSequence = expectedSequence,
                actualSequence = actualSequence,
                missingPacketCount = missingPacketCount,
            ),
        )
        deliver(actualSequence, samples)
    }

    private fun samplesForConcealment(sequence: Int): Int {
        val observed = observedSampleCountsByParity[sequence and 1]
        return if (observed > 0) observed else ic705SamplesPerReceivePacket(sequence)
    }
}
