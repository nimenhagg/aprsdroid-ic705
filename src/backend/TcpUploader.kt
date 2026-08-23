package org.aprsdroid.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import net.ab0oo.aprs.parser.APRSPacket
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class TcpUploader(val service: AprsService, prefs: PrefsWrapper) : AprsBackend(prefs) {
    val TAG = "APRSdroid.TcpUploader"
    val hostport: String = prefs.getString("tcp.server", "euro.aprs2.net")
    val so_timeout: Int = prefs.getStringInt("tcp.sotimeout", 120)
    val RECONNECT = 30
    var conn: TcpSocketThread? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun start(): Boolean {
        if (conn == null) {
            createConnection()
        }
        return false
    }

    fun createConnection() {
        Log.d(TAG, "TcpUploader.createConnection: $hostport")
        val t = TcpSocketThread(hostport)
        conn = t
        t.start()
    }

    override fun update(packet: APRSPacket): String {
        Log.d(TAG, "TcpUploader.update: $packet")
        return conn?.update(packet) ?: "TCP disconnected"
    }

    override fun stop() {
        conn?.let {
            synchronized(it) {
                it.running = false
            }
            executor.submit { it.shutdown() }
            it.interrupt()
            try { it.join(50) } catch (_: Exception) {}
        }
        conn = null
    }

    inner class TcpSocketThread(val hostport: String) : Thread("APRSdroid TCP connection") {
        val TAG = "APRSdroid.TcpSocketThread"
        @Volatile
        var running = true
        var passcode_warned = false
        var socket: Socket? = null
        var tnc: TncProto? = null

        val KEYSTORE_DIR = "keystore"
        val KEYSTORE_PASS = "APRS".toCharArray()

        fun init_ssl_socket(hostport: String): Socket? {
            val dir = service.applicationContext.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE)
            val keyStoreFile = File(dir.toString() + File.separator + prefs.getCallsign() + ".p12")

            val ks = KeyStore.getInstance("PKCS12")
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

            return try {
                val fis = FileInputStream(keyStoreFile)
                ks.load(fis, KEYSTORE_PASS)
                fis.close()
                for (alias in ks.aliases()) {
                    if (ks.isKeyEntry(alias)) {
                        val c = ks.getCertificate(alias) as X509Certificate
                        c.checkValidity()
                        val dn = c.subjectX500Principal.toString().replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
                        service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, "Loaded key: $dn")
                    }
                }
                kmf.init(ks, KEYSTORE_PASS)
                val sc = SSLContext.getInstance("TLS")
                sc.init(kmf.keyManagers, arrayOf(NaiveTrustManager()), null)

                val hostPortPair = AprsPacket.parseHostPort(hostport, 24580)
                val host = hostPortPair.first
                val port = hostPortPair.second
                service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, service.getString(R.string.post_connecting, host, port))

                val s = sc.socketFactory.createSocket(host as String, port) as SSLSocket
                s.enabledCipherSuites = sc.socketFactory.defaultCipherSuites
                s
            } catch (_: java.io.FileNotFoundException) {
                service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, service.getString(R.string.ssl_no_keyfile, prefs.getCallsign()))
                null
            } catch (e: Exception) {
                e.printStackTrace()
                service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, e.toString())
                null
            }
        }

        fun init_socket() {
            Log.d(TAG, "init_socket()")
            synchronized(this) {
                if (!running) {
                    Log.d(TAG, "init_socket() aborted")
                    return
                }
                if (prefs.getProto() == "aprsis") {
                    socket = init_ssl_socket(hostport)
                }
                if (socket == null) {
                    val hostPortPair = AprsPacket.parseHostPort(hostport, 14580)
                    val host = hostPortPair.first
                    val port = hostPortPair.second
                    service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, service.getString(R.string.post_connecting, host, port))

                    if (!passcode_warned && prefs.getProto() == "aprsis" && prefs.getPasscode() == "-1") {
                        service.handler.post {
                            Toast.makeText(service, R.string.anon_warning, Toast.LENGTH_LONG).show()
                        }
                        passcode_warned = true
                    }
                    socket = Socket(host as String, port)
                }
                socket?.let { s ->
                    s.keepAlive = true
                    s.soTimeout = so_timeout * 1000
                    tnc = AprsBackend.instanciateProto(service, s.getInputStream(), s.getOutputStream())
                }
            }
            Log.d(TAG, "init_socket() done")
        }

        override fun run() {
            var need_reconnect = false
            Log.d(TAG, "TcpSocketThread.run()")
            try {
                init_socket()
                service.postLinkOn(R.string.p_aprsis_tcp)
                service.postPosterStarted()
            } catch (e: IllegalArgumentException) {
                service.postAbort(e.message ?: "Illegal argument")
                running = false
            } catch (e: Exception) {
                service.postAbort(e.toString())
                running = false
            }
            while (running) {
                try {
                    if (need_reconnect) {
                        Log.d(TAG, "reconnecting in ${RECONNECT}s")
                        service.postAddPost(
                            StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info,
                            service.getString(R.string.post_reconnect, RECONNECT)
                        )
                        shutdown()
                        sleep((RECONNECT * 1000).toLong())
                        init_socket()
                        need_reconnect = false
                        service.postLinkOn(R.string.p_aprsis_tcp)
                    }
                    Log.d(TAG, "waiting for data...")
                    var line: String? = tnc?.readPacket()
                    while (running && line != null) {
                        Log.d(TAG, "recv: $line")
                        if (line.isNotEmpty() && line[0] != '#') {
                            service.postSubmit(line)
                        } else {
                            service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, line)
                        }
                        line = tnc?.readPacket()
                    }
                    if (running && (line == null || socket?.isConnected != true)) {
                        need_reconnect = true
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    Log.i(TAG, "restarting due to timeout")
                    need_reconnect = true
                } catch (e: Exception) {
                    Log.d(TAG, "Exception $e")
                    need_reconnect = true
                }
                if (need_reconnect) {
                    service.postLinkOff(R.string.p_aprsis_tcp)
                }
            }
            Log.d(TAG, "TcpSocketThread.terminate()")
        }

        fun update(packet: APRSPacket): String {
            val s = socket
            val currentTnc = tnc
            return if (s != null && s.isConnected && currentTnc != null) {
                currentTnc.writePacket(packet)
                "TCP OK"
            } else {
                "TCP disconnected"
            }
        }

        fun catchLog(tag: String, block: () -> Unit) {
            try { block() } catch (e: Exception) { Log.d(TAG, "$tag exception: $e") }
        }

        fun shutdown() {
            Log.d(TAG, "shutdown()")
            tnc?.stop()
            synchronized(this) {
                socket?.let { s ->
                    catchLog("shutdownInput") { s.shutdownInput() }
                    catchLog("shutdownOutput") { s.shutdownOutput() }
                    catchLog("socket.close") { s.close() }
                }
                socket = null
            }
        }
    }

    class NaiveTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
    }
}
