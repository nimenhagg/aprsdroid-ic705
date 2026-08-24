package org.aprsdroid.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpPostUploaderTest {
    @Test
    fun endpointForAddsPlainHttpAndDefaultAprsPortToBareHost() {
        assertEquals(
            "http://srvr.aprs-is.net:8080/",
            HttpPostUploader.endpointFor("srvr.aprs-is.net"),
        )
        assertEquals(
            "http://example.net:14580/upload",
            HttpPostUploader.endpointFor("example.net:14580/upload"),
        )
    }

    @Test
    fun endpointForPreservesExplicitScheme() {
        assertEquals(
            "http://example.net/upload",
            HttpPostUploader.endpointFor("http://example.net/upload"),
        )
        assertEquals(
            "https://example.net/upload",
            HttpPostUploader.endpointFor("https://example.net/upload"),
        )
    }

    @Test
    fun clientSendsUtf8OctetStreamAndClosesConnection() {
        lateinit var connection: FakeHttpConnection
        val client = HttpPostClient(
            connectionFactory = { url ->
                connection = FakeHttpConnection(url, 202, "Accepted")
                connection
            },
            connectTimeoutMillis = 1_234,
            readTimeoutMillis = 5_678,
        )

        val result = client.post("http://example.net:8080/", "login\r\n位置\r\n")

        assertEquals(202, result.statusCode)
        assertEquals("HTTP Accepted", result.summary())
        assertEquals("POST", connection.requestMethod)
        assertEquals(1_234, connection.connectTimeout)
        assertEquals(5_678, connection.readTimeout)
        assertTrue(connection.doOutput)
        assertFalse(connection.useCaches)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("application/octet-stream", connection.getRequestProperty("Content-Type"))
        assertEquals("text/plain", connection.getRequestProperty("Accept"))
        assertEquals("login\r\n位置\r\n", connection.requestBody.toString(Charsets.UTF_8.name()))
        assertEquals(connection.requestBody.size(), connection.configuredLength)
        assertTrue(connection.disconnected)
    }

    @Test
    fun resultFallsBackToNumericStatusWhenReasonIsMissing() {
        assertEquals("HTTP 503", HttpPostResult(503, null).summary())
        assertEquals("HTTP 503", HttpPostResult(503, "").summary())
    }

    private class FakeHttpConnection(
        url: URL,
        private val code: Int,
        private val reason: String,
    ) : HttpURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        var disconnected = false
        val configuredLength: Int
            get() = fixedContentLength

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = requestBody

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getResponseCode(): Int = code

        override fun getResponseMessage(): String = reason
    }
}
