package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

class HttpPostUploader(prefs: PrefsWrapper) : AprsBackend(prefs) {
    companion object {
        const val TAG = "APRSdroid.HttpPost"
    }

    private val host = prefs.getString("http.server", "srvr.aprs-is.net")

    override fun start(): Boolean = true

    @Suppress("DEPRECATION")
    fun doPost(urlString: String, content: String): String {
        val client = DefaultHttpClient()
        val post = HttpPost(urlString)
        post.entity = StringEntity(content)
        post.addHeader("Content-Type", "application/octet-stream")
        post.addHeader("Accept-Type", "text/plain")
        val response = client.execute(post)
        Log.d(TAG, "doPost(): " + response.statusLine)
        return "HTTP " + response.statusLine.reasonPhrase
    }

    override fun update(packet: APRSPacket): String {
        var hostname = host
        if (!hostname.contains(":")) {
            hostname = "http://$hostname:8080/"
        }
        return doPost(hostname, login + "\r\n" + packet + "\r\n")
    }

    override fun stop() {}
}
