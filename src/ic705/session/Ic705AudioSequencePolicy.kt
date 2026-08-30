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

internal fun incrementIc705AudioSequence(sequence: Int): Int = (sequence + 1) and 0xffff

internal fun ic705AudioSequenceDistance(from: Int, to: Int): Int = (to - from) and 0xffff

internal fun ic705SamplesPerReceivePacket(sequence: Int): Int =
    if (sequence and 1 == 0) IC705_AUDIO_LARGE_RX_PACKET_SAMPLES else IC705_AUDIO_SMALL_RX_PACKET_SAMPLES
