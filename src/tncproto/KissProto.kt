package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Parser
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.Locale

class KissProto(val service: AprsService, isStream: InputStream, osStream: OutputStream) : TncProto(isStream, osStream) {
    companion object {
        const val TAG = "APRSdroid.KissProto"
        const val FEND = 0xC0
        const val FESC = 0xDB
        const val TFEND = 0xDC
        const val TFESC = 0xDD
        const val CMD_DATA = 0x00
    }

    init {
        val rawInit = service.prefs.getString("kiss.init", "")
        val initstring = try { URLDecoder.decode(rawInit, "UTF-8") } catch (_: Throwable) { rawInit }
        val initdelay = service.prefs.getStringInt("kiss.delay", 300)
        if (!initstring.isNullOrEmpty()) {
            for (line in initstring.split("\n")) {
                service.postAddPost(StorageDatabase.Companion.Post.TYPE_TX, R.string.p_tnc_init, line)
                osStream.write(line.toByteArray())
                osStream.write('\r'.code)
                osStream.write('\n'.code)
                try { Thread.sleep(initdelay.toLong()) } catch (_: InterruptedException) {}
            }
        }

        if (service.prefs.getCallsign().length > 6) {
            throw IllegalArgumentException(service.getString(R.string.e_toolong_callsign))
        }
    }

    override fun readPacket(): String {
        val buf = ArrayList<Byte>()
        while (true) {
            val ch = isStream?.read() ?: -1
            if (ch >= 0) {
                Log.d(TAG, String.format(Locale.US, "readPacket: %02X '%c'", ch, ch.toChar()))
            }
            when (ch) {
                FEND -> {
                    if (buf.isNotEmpty()) {
                        Log.d(TAG, "readPacket: sending back " + String(buf.toByteArray()))
                        try {
                            return Parser.parseAX25(buf.toByteArray()).toString().trim()
                        } catch (_: Exception) {
                            buf.clear()
                        }
                    }
                }
                FESC -> {
                    when (isStream?.read()) {
                        TFEND -> buf.add(FEND.toByte())
                        TFESC -> buf.add(FESC.toByte())
                    }
                }
                -1 -> throw java.io.IOException("KissReader out of data")
                0 -> {
                    if (buf.isNotEmpty()) buf.add(ch.toByte())
                    else Log.d(TAG, "readPacket: ignoring command byte")
                }
                10 -> {
                    if (buf.size > 1 && buf[0] > 0 && buf[buf.size - 1].toInt() == 13) {
                        return String(buf.toByteArray()).trim()
                    }
                }
                else -> buf.add(ch.toByte())
            }
        }
    }

    override fun writePacket(p: APRSPacket) {
        Log.d(TAG, "writePacket: $p")
        val frame = p.toAX25Frame()
        val combined = ByteArray(frame.size + 3)
        combined[0] = FEND.toByte()
        combined[1] = CMD_DATA.toByte()
        System.arraycopy(frame, 0, combined, 2, frame.size)
        combined[combined.size - 1] = FEND.toByte()
        osStream?.write(combined)
        osStream?.flush()
    }
}
