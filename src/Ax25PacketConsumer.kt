package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.Parser

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
        try {
            submit.postSubmit(Parser.parseAX25(data).toString().trim())
        } catch (e: Exception) {
            // Do not dump an arbitrary full RF frame (or a stack trace) into logs.
            // The backend contract calls for a bounded diagnostic summary only.
            Log.e(tag, "bad AX.25 frame (${e.javaClass.simpleName}): ${boundedAx25Hex(data)}")
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
