package org.aprsdroid.app

import net.ab0oo.aprs.parser.APRSPacket
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

open class Tnc2Proto(isStream: InputStream, osStream: OutputStream) : TncProto(isStream, osStream) {
    val reader: BufferedReader = BufferedReader(InputStreamReader(isStream), 256)
    val writer: PrintWriter = PrintWriter(OutputStreamWriter(osStream), true)

    override fun readPacket(): String = reader.readLine() ?: ""
    override fun writePacket(p: APRSPacket) = writer.println(p)
}
