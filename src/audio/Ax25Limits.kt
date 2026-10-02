package org.aprsdroid.app.audio

/**
 * Hard limits of the AX.25 link layer, enforced at the boundary before any
 * vendor library sees untrusted data.
 *
 * The bundled AFSK/AX.25 libraries are third-party code with long release
 * cycles. They differ in how they react to an over-long callsign:
 * [jsoundmodem]'s `APRSFrame.parseCall` writes past its 7-byte address field
 * and throws, while [javAX25]'s `Packet.addCall` silently truncates the
 * callsign — the packet still goes on the air, addressed to the wrong station,
 * with no error surfaced to the operator.
 *
 * Validating here keeps both behaviours from being reachable, instead of
 * depending on per-library guards that may not exist or may be disabled.
 */
object Ax25Limits {
    /**
     * AX.25 address field payload: 6 characters plus an optional SSID.
     * `AfskUploader` and `KissProto` already enforce this; the IC-705 and USB
     * radio transmit paths used to skip the check entirely.
     */
    const val MAX_CALLSIGN_CHARS = 6

    /** Maximum AX.25 UI frame size in bytes, matching the library frame buffer. */
    const val MAX_FRAME_BYTES = 330

    /**
     * Returns the callsign when it fits an AX.25 address field, or `null`.
     *
     * The caller-facing name is used verbatim here (no trimming or casing):
     * pre-normalisation is `PrefsWrapper.getCallsign()`'s job.
     */
    fun fitsCallsign(callsign: String): Boolean = callsign.length <= MAX_CALLSIGN_CHARS

    /**
     * Normalises a decoded frame buffer length check.
     *
     * AX.25 frames are at most [MAX_FRAME_BYTES] bytes including the 2-byte
     * FCS, so anything larger cannot be a valid frame and must not be handed
     * to the parser.
     */
    fun fitsFrame(length: Int): Boolean = length in 1..MAX_FRAME_BYTES
}

/**
 * Raised when a callsign cannot be represented in an AX.25 address field.
 *
 * Extends [IllegalArgumentException] so existing generic handlers keep working,
 * mirroring [Ax25PayloadEncodingException].
 */
class Ax25CallsignException(callsign: String) :
    IllegalArgumentException(
        "AX.25 callsign \"$callsign\" exceeds ${Ax25Limits.MAX_CALLSIGN_CHARS} characters",
    )
