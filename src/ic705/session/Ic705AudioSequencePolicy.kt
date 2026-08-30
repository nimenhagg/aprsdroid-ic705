package org.aprsdroid.app.ic705.session

internal const val IC705_AUDIO_HALF_SEQUENCE_SPACE = 0x8000

// IC-705 sends roughly 100 audio datagrams per second. Four packets absorb
// ordinary Wi-Fi scheduling jitter while adding at most about 40 ms when a
// packet is genuinely lost.
internal const val IC705_AUDIO_MAX_REORDER_PACKETS = 4

// The connection negotiation currently requests 12 kHz LPCM. The original
// concealment defaults were copied from a successful 48 kHz RS-BA1 capture
// (682/278 samples per alternating packet) and therefore inserted roughly 4x
// too much silence at 12 kHz. 171/69 keeps the same 20 ms pair duration at
// the negotiated rate. Ic705AudioReorderBuffer also learns real packet sizes
// from the live stream and prefers those once observed.
internal const val IC705_AUDIO_LARGE_RX_PACKET_SAMPLES = 171
internal const val IC705_AUDIO_SMALL_RX_PACKET_SAMPLES = 69
internal const val IC705_AUDIO_MAX_CONCEALED_PACKETS = 2

private const val IC705_AUDIO_SHORT_SEQUENCE_SPACE = 0x4000
private const val IC705_AUDIO_SHORT_SEQUENCE_MASK = IC705_AUDIO_SHORT_SEQUENCE_SPACE - 1
private const val IC705_AUDIO_SHORT_WRAP_WINDOW = IC705_AUDIO_MAX_REORDER_PACKETS + 1

internal fun incrementIc705AudioSequence(sequence: Int): Int = (sequence + 1) and 0xffff

/**
 * Computes forward distance while tolerating the short sequence rollover seen
 * in field diagnostics around the 0x4000 boundary.
 *
 * The normal arithmetic remains 16-bit. Compatibility is only applied in a
 * tiny window around 0x3fff -> 0x0000, so a normal 16-bit stream is unaffected.
 * This avoids turning a plausible counter rollover into a huge false UDP gap
 * and demodulator reset without asserting that every radio uses a 14-bit counter.
 */
internal fun ic705AudioSequenceDistance(from: Int, to: Int): Int {
    val distance16 = (to - from) and 0xffff

    val nearShortWrapForward =
        from in (IC705_AUDIO_SHORT_SEQUENCE_SPACE - IC705_AUDIO_SHORT_WRAP_WINDOW)..IC705_AUDIO_SHORT_SEQUENCE_SPACE &&
            to in 0..IC705_AUDIO_SHORT_WRAP_WINDOW
    if (nearShortWrapForward) {
        return (to - (from and IC705_AUDIO_SHORT_SEQUENCE_MASK)) and IC705_AUDIO_SHORT_SEQUENCE_MASK
    }

    val nearShortWrapReverse =
        from in 0..IC705_AUDIO_SHORT_WRAP_WINDOW &&
            to in (IC705_AUDIO_SHORT_SEQUENCE_SPACE - IC705_AUDIO_SHORT_WRAP_WINDOW) until IC705_AUDIO_SHORT_SEQUENCE_SPACE
    if (nearShortWrapReverse) {
        // Treat a delayed pre-wrap packet as old/out-of-order rather than as a
        // giant forward gap that would reset the demodulator again.
        return 0xffff
    }

    return distance16
}

internal fun ic705SamplesPerReceivePacket(sequence: Int): Int =
    if (sequence and 1 == 0) IC705_AUDIO_LARGE_RX_PACKET_SAMPLES else IC705_AUDIO_SMALL_RX_PACKET_SAMPLES
