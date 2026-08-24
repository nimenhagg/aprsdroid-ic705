package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class HttpPostResult(
    val statusCode: Int,
    val reasonPhrase: String?,
) {
    fun summary(): String = reasonPhrase
        ?.takeIf { it.isNotBlank() }
        ?.let { "HTTP $it" }
        ?: "HTTP $statusCode"
}

internal class HttpPostClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as? HttpURLConnection
            ?: throw IOException("Unsupported HTTP endpoint: $url")
    },
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    fun post(urlString: String, content: String): HttpPostResult {
        val body = content.toByteArray(Charsets.UTF_8)
        val connection = connectionFactory(URL(urlString))
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Accept", "text/plain")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { output -> output.write(body) }

            val statusCode = connection.responseCode
            val reasonPhrase = connection.responseMessage
            val responseStream = if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                runCatching { connection.inputStream }.getOrNull()
            }
            responseStream?.close()
            return HttpPostResult(statusCode, reasonPhrase)
        } finally {
            connection.disconnect()
        }
    }
}

class HttpPostUploader(prefs: PrefsWrapper) : AprsBackend(prefs) {
    companion object {
        const val TAG = "APRSdroid.HttpPost"

        private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

        internal fun endpointFor(server: String): String {
            val trimmed = server.trim()
            require(trimmed.isNotEmpty()) { "HTTP server must not be empty" }
            if (URI_SCHEME.containsMatchIn(trimmed)) return trimmed

            val parsed = URL("http://$trimmed")
            if (parsed.port >= 0) return parsed.toExternalForm()
            return URL(parsed.protocol, parsed.host, 8080, parsed.file.ifEmpty { "/" })
                .toExternalForm()
        }
    }

    private val host = prefs.getString("http.server", "srvr.aprs-is.net")
    private val client = HttpPostClient()

    override fun start(): Boolean = true

    fun doPost(urlString: String, content: String): String {
        val result = client.post(urlString, content)
        Log.d(TAG, "doPost(): HTTP ${result.statusCode} ${result.reasonPhrase.orEmpty()}".trimEnd())
        return result.summary()
    }

    override fun update(packet: APRSPacket): String {
        return doPost(endpointFor(host), login + "\r\n" + packet + "\r\n")
    }

    override fun stop() {}
}
