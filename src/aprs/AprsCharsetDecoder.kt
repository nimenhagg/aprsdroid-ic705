package org.aprsdroid.app.aprs

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Intelligent charset detector and decoder for amateur radio APRS.
 *
 * Historically, Chinese amateur radio stations and TNC hardware used GB2312 / GBK / GB18030
 * for comments, while modern clients and APRS-IS servers use UTF-8.
 *
 * This decoder attempts strict UTF-8 decoding first. If the byte sequence contains invalid
 * UTF-8 byte sequences, it automatically falls back to GB18030 (superset of GBK/GB2312),
 * preventing garbled Chinese text across both legacy and modern stations.
 */
object AprsCharsetDecoder {

    private val GB18030_CHARSET: Charset by lazy {
        try {
            Charset.forName("GB18030")
        } catch (e: Exception) {
            try {
                Charset.forName("GBK")
            } catch (e2: Exception) {
                StandardCharsets.UTF_8
            }
        }
    }

    /**
     * Decode a byte array into a String, automatically determining whether it is UTF-8 or GB18030/GBK.
     */
    fun decodeSmart(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
        if (length <= 0) return ""

        var isPureAscii = true
        for (i in offset until (offset + length)) {
            if ((bytes[i].toInt() and 0x80) != 0) {
                isPureAscii = false
                break
            }
        }
        if (isPureAscii) {
            return String(bytes, offset, length, StandardCharsets.US_ASCII)
        }

        // 1. Try strict UTF-8
        try {
            val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val buffer = ByteBuffer.wrap(bytes, offset, length)
            return utf8Decoder.decode(buffer).toString()
        } catch (_: Exception) {
            // Not valid UTF-8
        }

        // 2. Try GB18030 / GBK
        try {
            val gbkDecoder = GB18030_CHARSET.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val buffer = ByteBuffer.wrap(bytes, offset, length)
            return gbkDecoder.decode(buffer).toString()
        } catch (_: Exception) {
            // Not valid GBK either
        }

        // 3. Fallback to ISO-8859-1 lossless
        return String(bytes, offset, length, StandardCharsets.ISO_8859_1)
    }

    /**
     * Repair strings that were read as ISO-8859-1 by javAPRSlib or legacy socket streams.
     * Recovers raw byte stream from 0x80..0xFF chars and re-evaluates through [decodeSmart].
     */
    fun repairString(input: String): String {
        if (input.isEmpty()) return input

        var hasHighBytes = false
        for (i in input.indices) {
            val code = input[i].code
            if (code in 0x80..0xFF) {
                hasHighBytes = true
            } else if (code > 0xFF) {
                // Already contains Unicode multi-byte characters beyond 8-bit Latin
                return input
            }
        }

        if (!hasHighBytes) return input

        val recovered = ByteArray(input.length)
        for (i in input.indices) {
            recovered[i] = input[i].code.toByte()
        }

        return decodeSmart(recovered)
    }

    fun repairNullable(input: String?): String? = input?.let { repairString(it) }
}
