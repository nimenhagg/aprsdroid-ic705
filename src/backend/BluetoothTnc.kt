package org.aprsdroid.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.util.UUID

class BluetoothTnc(
    val service: AprsService,
    prefs: PrefsWrapper
) : AprsBackend(prefs) {

    companion object {
        const val TAG = "APRSdroid.Bluetooth"
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val RECONNECT_SECONDS = 3L
    }

    val btClient = prefs.getBoolean("bt.client", true)
    val tncmac: String? = prefs.getString("bt.mac", null)
    val tncchannel = prefs.getStringInt("bt.channel", -1)
    var conn: BtSocketThread? = null

    override fun start(): Boolean {
        if (conn == null) createConnection()
        return false
    }

    @SuppressLint("MissingPermission")
    fun createConnection() {
        val bm = service.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bm?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            service.postAbort(service.getString(R.string.bt_error_unsupported))
            return
        }
        if (!adapter.isEnabled) {
            service.postAbort(service.getString(R.string.bt_error_disabled))
            return
        }
        if (btClient && tncmac == null) {
            service.postAbort(service.getString(R.string.bt_error_no_tnc))
            return
        }

        val tnc = if (btClient && tncmac != null) adapter.getRemoteDevice(tncmac) else null
        val thread = BtSocketThread(adapter, tnc)
        conn = thread
        thread.start()
    }

    override fun update(packet: APRSPacket): String {
        Log.d(TAG, "BluetoothTnc.update: $packet")
        return conn?.update(packet) ?: "Bluetooth disconnected"
    }

    override fun stop() {
        val c = conn ?: return
        synchronized(c) {
            c.running = false
        }
        c.shutdown()
        c.interrupt()
        try { c.join(50) } catch (_: InterruptedException) {}
    }

    inner class BtSocketThread(
        private val ba: BluetoothAdapter,
        private val tnc: BluetoothDevice?
    ) : Thread("APRSdroid Bluetooth connection") {

        val TAG = "BtSocketThread"
        var running = true
        var socket: BluetoothSocket? = null
        var proto: TncProto? = null

        fun log(s: String) {
            service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, s)
        }

        fun log(id: Int, vararg args: Any) {
            service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, service.getString(id, *args))
        }

        @SuppressLint("MissingPermission")
        fun initSocket() {
            Log.d(TAG, "init_socket()")
            if (socket != null) {
                shutdown()
            }
            val dev = tnc
            val sock: BluetoothSocket = if (dev == null) {
                log(R.string.bt_awaiting)
                val s = ba.listenUsingRfcommWithServiceRecord("SPP", SPP).accept(-1)
                val rDev = s.remoteDevice
                val name = rDev.name ?: rDev.address
                log(R.string.bt_client_connected, name)
                s
            } else if (tncchannel == -1) {
                log(R.string.bt_connecting_to_spp, tncmac ?: "")
                val s = dev.createRfcommSocketToServiceRecord(SPP)
                s.connect()
                s
            } else {
                log(R.string.bt_connecting_to_channel, tncmac ?: "", tncchannel)
                val m = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val s = m.invoke(dev, tncchannel) as BluetoothSocket
                s.connect()
                s
            }
            socket = sock
            log(R.string.bt_connected)
            proto = AprsBackend.instanciateProto(service, sock.inputStream, sock.outputStream)
            Log.d(TAG, "init_socket() done")
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            running = true
            var needReconnect = false
            Log.d(TAG, "BtSocketThread.run()")
            try {
                initSocket()
                service.postPosterStarted()
            } catch (e: IllegalArgumentException) {
                service.postAbort(e.message ?: "Illegal argument")
                running = false
            } catch (e: Exception) {
                e.printStackTrace()
                val name = (if (tnc != null) tnc.name else null) ?: tncmac ?: "TNC"
                service.postAbort(service.getString(R.string.bt_error_connect, name))
                running = false
            }

            while (running) {
                try {
                    if (needReconnect) {
                        log(R.string.bt_reconnecting)
                        try { sleep(RECONNECT_SECONDS * 1000L) } catch (_: InterruptedException) {}
                        initSocket()
                        needReconnect = false
                        service.postLinkOn(R.string.p_link_bt)
                    }
                    Log.d(TAG, "waiting for data...")
                    while (running) {
                        val p = proto ?: break
                        val line = p.readPacket()
                        Log.d(TAG, "recv: $line")
                        service.postSubmit(line)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "exception, reconnecting...")
                    if (running && !needReconnect) {
                        service.postLinkOff(R.string.p_link_bt)
                    }
                    needReconnect = true
                    try {
                        if (running) {
                            service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_error, e.toString())
                        }
                        e.printStackTrace()
                    } catch (_: Exception) {}
                }
            }
            Log.d(TAG, "BtSocketThread.terminate()")
        }

        fun update(packet: APRSPacket): String {
            return try {
                proto?.writePacket(packet)
                "Bluetooth OK"
            } catch (e: Exception) {
                e.printStackTrace()
                try { socket?.close() } catch (_: Exception) {}
                "Bluetooth disconnected"
            }
        }

        private fun catchLog(tag: String, block: () -> Unit) {
            Log.d(TAG, "catchLog($tag)")
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "$tag exception: $e")
            }
        }

        fun shutdown() {
            Log.d(TAG, "shutdown()")
            proto?.stop()
            synchronized(this) {
                val s = socket
                if (s != null) {
                    catchLog("socket.close") { s.close() }
                }
                socket = null
            }
        }
    }
}
