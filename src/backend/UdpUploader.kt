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

    @Volatile
    private var socket: DatagramSocket? = null
    private val host = prefs.getString("udp.server", "srvr.aprs-is.net")

    override fun start(): Boolean {
        if (socket == null || socket?.isClosed == true) {
            socket = DatagramSocket()
        }
        return true
    }

    override fun update(packet: APRSPacket): String {
        val currentSocket = socket ?: DatagramSocket().also { socket = it }
        val (h, port) = AprsPacket.parseHostPort(host, 8080)
        val addr = InetAddress.getByName(h)
        val pbytes = (login + "\r\n" + packet + "\r\n").toByteArray()
        currentSocket.send(DatagramPacket(pbytes, pbytes.size, addr, port))
        Log.d(TAG, "update(): sent '$packet' to $host")
        return "UDP OK"
    }

    override fun stop() {
        runCatching { socket?.close() }
        socket = null
    }
}
