package org.aprsdroid.app

import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.diagnostic.AppLog

/**
 * Submission sink decoupled from [AprsService] so the Scala [AfskUploader] and
 * the Kotlin IC-705 backend can share one parsing/submission policy without
 * importing each other's concrete service type.
 */
fun interface Ax25SubmitSink {
    fun postSubmit(text: String)
}

/**
 * Shared boundary between a radio/demodulator and APRSdroid's existing APRS
 * business path. Backends provide raw AX.25 frames; this class owns parsing and
 * submission so individual radio implementations do not duplicate that policy.
 */
class Ax25PacketConsumer(
    private val submit: Ax25SubmitSink,
    private val tag: String,
) {
    fun accept(data: ByteArray) {
        val head = boundedAx25Hex(data, maxBytes = 24)
        AppLog.d(
            "AFSK",
            "afsk_frame_decoded",
            mapOf(
                "backend_tag" to tag,
                "length" to data.size,
                "head_hex" to head,
            ),
        )

        val parsed = try {
            Parser.parseAX25(data)
        } catch (error: Exception) {
            // Persist this boundary failure: a diagnostic bundle must be able to
            // distinguish "demodulator emitted nothing" from "AX.25 parser rejected it".
            AppLog.w(
                "AFSK",
                "ax25_parse_failed",
                mapOf(
                    "backend_tag" to tag,
                    "length" to data.size,
                    "head_hex" to head,
                ),
                error,
            )
            return
        }

        val text = parsed.toString().trim()
        AppLog.d(
            "AFSK",
            "ax25_parse_ok",
            mapOf(
                "backend_tag" to tag,
                "length" to data.size,
                "text_length" to text.length,
            ),
        )

        try {
            submit.postSubmit(text)
        } catch (error: Exception) {
            AppLog.e(
                "AFSK",
                "ax25_submit_failed",
                mapOf(
                    "backend_tag" to tag,
                    "length" to data.size,
                ),
                error,
            )
        }
    }
}

internal fun boundedAx25Hex(data: ByteArray, maxBytes: Int = 32): String {
    require(maxBytes >= 0)
    val shown = minOf(data.size, maxBytes)
    val hex = data.take(shown).joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
    return if (shown == data.size) {
        "$hex (${data.size} bytes)"
    } else {
        "$hex … (+${data.size - shown} bytes; ${data.size} total)"
    }
}
