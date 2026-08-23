package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpUploader(prefs: PrefsWrapper) : AprsBackend(prefs) {
    companion object {
        const val TAG = "APRSdroid.Udp"
    }

    private val socket: DatagramSocket by lazy { DatagramSocket() }
    private val host = prefs.getString("udp.server", "srvr.aprs-is.net")

    override fun start(): Boolean = true

    override fun update(packet: APRSPacket): String {
        val (h, port) = AprsPacket.parseHostPort(host, 8080)
        val addr = InetAddress.getByName(h)
        val pbytes = (login + "\r\n" + packet + "\r\n").toByteArray()
        socket.send(DatagramPacket(pbytes, pbytes.size, addr, port))
        Log.d(TAG, "update(): sent '$packet' to $host")
        return "UDP OK"
    }

    override fun stop() {}
}
