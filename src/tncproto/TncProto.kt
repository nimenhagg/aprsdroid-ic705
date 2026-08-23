package org.aprsdroid.app

import net.ab0oo.aprs.parser.APRSPacket
import java.io.InputStream
import java.io.OutputStream

abstract class TncProto(val isStream: InputStream?, val osStream: OutputStream?) {
    abstract fun readPacket(): String
    abstract fun writePacket(p: APRSPacket)
    open fun stop() {}
}
