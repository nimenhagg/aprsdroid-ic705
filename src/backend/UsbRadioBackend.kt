package org.aprsdroid.app.backend

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.AprsBackend
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.Ax25PacketConsumer
import org.aprsdroid.app.Ax25SubmitSink
import org.aprsdroid.app.audio.PcmEncoding
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.hamlib.HamlibRigCatalog
import org.aprsdroid.app.radio.AudioRecordPcmSource
import org.aprsdroid.app.radio.AudioTrackPcmSink
import org.aprsdroid.app.radio.HamlibRadioControl
import org.aprsdroid.app.radio.RadioAudioDevice
import org.aprsdroid.app.radio.RadioProfile
import org.aprsdroid.app.radio.RadioPttSequence
import org.aprsdroid.app.radio.UsbRadioSession
import org.aprsdroid.app.radio.UsbSerialByteStream

class UsbRadioBackend(
    val service: AprsService,
    prefs: PrefsWrapper,
) : AprsBackend(prefs) {

    companion object {
        const val TAG = "APRSdroid.UsbRadio"
        const val USB_PERM_ACTION = "org.aprsdroid.app.UsbRadioBackend.PERM"
        const val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
    }

    private val usbManager = service.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val intent = Intent(USB_PERM_ACTION).setPackage(service.packageName)
    private val pendingIntent = PendingIntent.getBroadcast(service, 0, intent, PendingIntent.FLAG_MUTABLE)

    private var registeredReceivers = false
    private var targetDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var serialDevice: UsbSerialDevice? = null
    private var session: UsbRadioSession? = null

    private val ax25PacketConsumer = Ax25PacketConsumer(
        Ax25SubmitSink { text -> service.postSubmit(text) },
        TAG,
    )

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            val extras = i.extras ?: return
            val granted = extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
            if (!granted) {
                service.postAbort(service.getString(R.string.p_serial_noperm))
                return
            }
            log("Obtained USB permissions for radio.")
            if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                service.postAbort("Audio recording permission is not granted")
                return
            }
            startSessionWithDevice()
        }
    }

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            log("USB radio device detached.")
            session?.onUsbDetached()
            ctx.stopService(AprsService.intent(ctx, AprsService.SERVICE))
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(): Boolean {
        ContextCompat.registerReceiver(
            service,
            permissionReceiver,
            IntentFilter(USB_PERM_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            service,
            detachReceiver,
            IntentFilter(ACTION_USB_DETACHED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        registeredReceivers = true

        val device = findSupportedUsbDevice()
        if (device == null) {
            service.postAbort(service.getString(R.string.p_serial_notfound))
            return false
        }
        targetDevice = device

        if (usbManager.hasPermission(device)) {
            startSessionWithDevice()
        } else {
            log("Requesting USB permission for ${device.deviceName}...")
            usbManager.requestPermission(device, pendingIntent)
        }
        return false
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startSessionWithDevice() {
        val dev = targetDevice ?: return
        val conn = usbManager.openDevice(dev)
        if (conn == null) {
            service.postAbort("Could not open USB connection")
            return
        }
        connection = conn

        val serial = UsbSerialDevice.createUsbSerialDevice(dev, conn)
        if (serial == null || !serial.syncOpen()) {
            conn.close()
            service.postAbort(service.getString(R.string.p_serial_unsupported))
            return
        }
        serialDevice = serial

        val profile = resolveProfile()
        val baudRate = prefs.getStringInt("radio.baudrate", profile.defaultBaudRate)
        serial.setBaudRate(baudRate)
        serial.setDataBits(UsbSerialInterface.DATA_BITS_8)
        serial.setStopBits(UsbSerialInterface.STOP_BITS_1)
        serial.setParity(UsbSerialInterface.PARITY_NONE)
        serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

        log("USB Serial opened at ${baudRate}bd for ${profile.name}")

        val catStream = UsbSerialByteStream(serial)

        // Select explicit USB audio devices if available
        val usbInputs = RadioAudioDevice.listUsbDevices(audioManager, isInput = true)
        val usbOutputs = RadioAudioDevice.listUsbDevices(audioManager, isInput = false)
        val preferredInput = usbInputs.firstOrNull()?.let { RadioAudioDevice.findDeviceInfo(audioManager, it.id) }
        val preferredOutput = usbOutputs.firstOrNull()?.let { RadioAudioDevice.findDeviceInfo(audioManager, it.id) }

        val pcmFormat = PcmFormat(
            sampleRateHz = profile.defaultAudioSampleRateHz,
            channelCount = 1,
            encoding = PcmEncoding.PCM_16_LE,
        )

        val audioSource = AudioRecordPcmSource(pcmFormat, preferredDevice = preferredInput)
        val audioSink = AudioTrackPcmSink(pcmFormat, preferredDevice = preferredOutput)

        val newSession = UsbRadioSession(
            profile = profile,
            catStream = catStream,
            audioSource = audioSource,
            audioSink = audioSink,
            radioControlFactory = { transport ->
                val rig = HamlibRigCatalog.findByModelId(profile.hamlibModelId)
                    ?: throw IllegalStateException("Model ${profile.hamlibModelId} (${profile.name}) not found in Hamlib catalog")
                HamlibRadioControl.create(rig, transport)
            },
            onPacketReceived = { frame ->
                ax25PacketConsumer.accept(frame)
            },
            onStateChanged = { state, msg ->
                log("Session state: $state ($msg)")
                if (state == UsbRadioSession.State.RUNNING) {
                    service.postPosterStarted()
                }
            },
        )
        session = newSession

        try {
            newSession.start()
        } catch (e: Exception) {
            log("Session start error: ${e.message}")
            service.postAbort(e.message ?: "Failed to start USB radio session")
        }
    }

    override fun update(packet: APRSPacket): String {
        val s = session ?: return "USB radio not connected"
        return when (s.transmit(packet)) {
            is RadioPttSequence.Result.Completed -> "Radio TX OK"
            is RadioPttSequence.Result.AudioFailed -> "Radio Audio Failed"
            is RadioPttSequence.Result.PttOnNotConfirmed -> "Radio PTT Refused"
            is RadioPttSequence.Result.PttOffNotConfirmed -> "Radio PTT Stuck"
        }
    }

    override fun stop() {
        if (registeredReceivers) {
            try { service.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
            try { service.unregisterReceiver(detachReceiver) } catch (_: Exception) {}
            registeredReceivers = false
        }
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { serialDevice?.close() } catch (_: Exception) {}
        serialDevice = null
        try { connection?.close() } catch (_: Exception) {}
        connection = null
    }

    private fun findSupportedUsbDevice(): UsbDevice? {
        val dl = usbManager.deviceList
        for ((_, device) in dl) {
            if (UsbSerialDevice.isSupported(device)) {
                return device
            }
        }
        return null
    }

    private fun resolveProfile(): RadioProfile {
        val modelId = prefs.getStringInt("radio.model_id", RadioProfile.IC705_USB.hamlibModelId)
        return RadioProfile.findByModelId(modelId) ?: RadioProfile.IC705_USB
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, msg)
    }
}
