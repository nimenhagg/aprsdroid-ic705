package org.aprsdroid.app.ic705.session

internal const val IC705_AUDIO_HALF_SEQUENCE_SPACE = 0x8000

// IC-705 sends roughly 100 audio datagrams per second. Four packets absorb
// ordinary Wi-Fi scheduling jitter while adding at most about 40 ms when a
// packet is genuinely lost.
internal const val IC705_AUDIO_MAX_REORDER_PACKETS = 4

// A successful 48 kHz RS-BA1 capture shows alternating 682/278-sample
// packets (960 samples, or 20 ms, per pair), starting with the larger
// packet at sequence zero. Conceal an isolated loss with equal-duration
// silence so the AFSK decoder keeps a correct time base.
internal const val IC705_AUDIO_LARGE_RX_PACKET_SAMPLES = 682
internal const val IC705_AUDIO_SMALL_RX_PACKET_SAMPLES = 278
internal const val IC705_AUDIO_MAX_CONCEALED_PACKETS = 2

internal fun incrementIc705AudioSequence(sequence: Int): Int = (sequence + 1) and 0xffff

internal fun ic705AudioSequenceDistance(from: Int, to: Int): Int = (to - from) and 0xffff

internal fun ic705SamplesPerReceivePacket(sequence: Int): Int =
    if (sequence and 1 == 0) IC705_AUDIO_LARGE_RX_PACKET_SAMPLES else IC705_AUDIO_SMALL_RX_PACKET_SAMPLES
