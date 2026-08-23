package org.aprsdroid.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Parcelable
import android.util.Log
import androidx.core.content.ContextCompat
import com.felhr.usbserial.SerialInputStream
import com.felhr.usbserial.SerialOutputStream
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import net.ab0oo.aprs.parser.APRSPacket
import java.util.Locale

class UsbTnc(
    val service: AprsService,
    prefs: PrefsWrapper
) : AprsBackend(prefs) {

    companion object {
        const val TAG = "APRSdroid.Usb"
        const val USB_PERM_ACTION = "org.aprsdroid.app.UsbTnc.PERM"
        const val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"

        @JvmStatic
        fun deviceHandle(dev: UsbDevice): String {
            return String.format(Locale.US, "usb_%04x_%04x_%s", dev.vendorId, dev.productId, dev.deviceName)
        }

        @JvmStatic
        fun checkDeviceHandle(prefs: SharedPreferences, devP: Parcelable?): Boolean {
            if (devP !is UsbDevice) return false
            val lastUse = prefs.getString(deviceHandle(devP), null) ?: return false
            prefs.edit().putString("proto", lastUse).putString("link", "usb").apply()
            return true
        }
    }

    private val usbManager = service.getSystemService(Context.USB_SERVICE) as UsbManager
    private var thread: UsbThread? = null
    private var dev: UsbDevice? = null
    private var con: UsbDeviceConnection? = null
    private var ser: UsbSerialDevice? = null
    private var alreadyRunning = false

    private val intent = Intent(USB_PERM_ACTION).setPackage(service.packageName)
    private val pendingIntent = PendingIntent.getBroadcast(service, 0, intent, PendingIntent.FLAG_MUTABLE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            Log.d(TAG, "onReceive: $i")
            if (i.action == ACTION_USB_DETACHED) {
                log("USB device detached.")
                ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
                return
            }
            val extras = i.extras
            if (extras == null) {
                service.postAbort("USB permission bug")
                return
            }
            val granted = extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
            if (!granted) {
                service.postAbort(service.getString(R.string.p_serial_noperm))
                return
            }
            log("Obtained USB permissions.")
            val t = UsbThread()
            thread = t
            t.start()
        }
    }

    private var proto: TncProto? = null
    private var sis: SerialInputStream? = null

    @SuppressLint("WrongConstant")
    override fun start(): Boolean {
        val filter = IntentFilter(USB_PERM_ACTION)
        filter.addAction(ACTION_USB_DETACHED)
        ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        alreadyRunning = true
        if (ser == null) requestPermissions()
        return false
    }

    fun log(s: String) {
        service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, s)
    }

    fun requestPermissions() {
        Log.d(TAG, "UsbTnc.requestPermissions")
        val dl = usbManager.deviceList
        for ((_, device) in dl) {
            val deviceVID = device.vendorId
            val devicePID = device.productId
            if (UsbSerialDevice.isSupported(device)) {
                log(String.format(Locale.US, "Found USB device %04x:%04x, requesting permissions.", deviceVID, devicePID))
                this.dev = device
                usbManager.requestPermission(device, pendingIntent)
                return
            } else {
                log(String.format(Locale.US, "Unsupported USB device %04x:%04x.", deviceVID, devicePID))
            }
        }
        service.postAbort(service.getString(R.string.p_serial_notfound))
    }

    override fun update(packet: APRSPacket): String {
        proto?.writePacket(packet)
        return "USB OK"
    }

    override fun stop() {
        if (alreadyRunning) {
            try { service.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
        alreadyRunning = false
        try { ser?.close() } catch (_: Exception) {}
        try { sis?.close() } catch (_: Exception) {}
        try { con?.close() } catch (_: Exception) {}
        val t = thread
        if (t != null) {
            synchronized(t) {
                t.running = false
            }
            t.interrupt()
            try { t.join(50) } catch (_: InterruptedException) {}
        }
        proto?.stop()
    }

    inner class UsbThread : Thread("APRSdroid USB connection") {
        val TAG = "UsbThread"
        var running = true

        fun log(s: String) {
            service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, s)
        }

        override fun run() {
            val d = dev
            if (d == null) {
                service.postAbort("No USB device")
                return
            }
            val connection = usbManager.openDevice(d)
            if (connection == null) {
                service.postAbort("Could not open USB connection")
                return
            }
            con = connection
            val serialDevice = UsbSerialDevice.createUsbSerialDevice(d, connection)
            if (serialDevice == null || !serialDevice.syncOpen()) {
                connection.close()
                service.postAbort(service.getString(R.string.p_serial_unsupported))
                return
            }
            ser = serialDevice
            val baudrate = prefs.getStringInt("baudrate", 115200)
            serialDevice.setBaudRate(baudrate)
            serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8)
            serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1)
            serialDevice.setParity(UsbSerialInterface.PARITY_NONE)
            serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

            prefs.prefs.edit().putString(deviceHandle(d), prefs.getString("proto", "kiss")).apply()

            log("Opened " + serialDevice.javaClass.simpleName + " at " + baudrate + "bd")
            val inputStream = SerialInputStream(serialDevice)
            sis = inputStream
            try {
                proto = AprsBackend.instanciateProto(service, inputStream, SerialOutputStream(serialDevice))
            } catch (e: IllegalArgumentException) {
                service.postAbort(e.message ?: "Protocol error")
                running = false
                return
            }
            service.postPosterStarted()
            while (running) {
                try {
                    val p = proto ?: break
                    val line = p.readPacket()
                    Log.d(TAG, "recv: $line")
                    service.postSubmit(line)
                } catch (e: Exception) {
                    Log.d(TAG, "readPacket exception: $e")
                    if (running) {
                        service.postAbort(e.toString())
                        running = false
                    }
                }
            }
            Log.d(TAG, "terminate()")
        }
    }
}
