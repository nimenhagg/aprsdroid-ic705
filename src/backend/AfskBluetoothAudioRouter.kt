package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Routes AFSK audio through Bluetooth while preserving the legacy SCO fallback. */
class AfskBluetoothAudioRouter(
    private val service: AprsService,
    private val onConnected: () -> Unit,
) {
    companion object {
        private const val TAG = "APRSdroid.AfskBtAudio"

        @RequiresApi(Build.VERSION_CODES.S)
        internal fun isBluetoothCommunicationDeviceType(type: Int): Boolean {
            return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    private val audioManager: AudioManager by lazy {
        service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val connectedDelivered = AtomicBoolean(false)
    @Volatile
    private var active = false
    private var modernRoute: ModernCommunicationRoute? = null
    private var legacyReceiverRegistered = false
    private var legacyScoRequested = false

    private val legacyScoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: -1
            Log.d(TAG, "legacy SCO state: $state")
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                deliverConnected()
                unregisterLegacyReceiver()
            }
        }
    }

    fun start() {
        active = true
        connectedDelivered.set(false)

        // startBluetoothSco() is deprecated from API 34. Keep the proven legacy path
        // on Android 8.1-13, and isolate the replacement API from those runtimes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val route = ModernCommunicationRoute(
                audioManager = audioManager,
                callbackExecutor = service.mainExecutor,
                onConnected = ::deliverConnected,
            )
            if (route.start()) {
                modernRoute = route
                return
            }
        }
        requestLegacySco()
    }

    fun stop() {
        active = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernRoute?.stop()
            modernRoute = null
        }

        unregisterLegacyReceiver()
        if (legacyScoRequested) {
            @Suppress("DEPRECATION")
            runCatching { audioManager.stopBluetoothSco() }
                .onFailure { Log.w(TAG, "legacy SCO stop failed", it) }
            legacyScoRequested = false
        }
    }

    private fun deliverConnected() {
        if (active && connectedDelivered.compareAndSet(false, true)) {
            onConnected()
        }
    }

    private fun unregisterLegacyReceiver() {
        if (!legacyReceiverRegistered) return
        runCatching { service.unregisterReceiver(legacyScoReceiver) }
        legacyReceiverRegistered = false
    }

    @Suppress("DEPRECATION")
    private fun requestLegacySco() {
        if (!legacyReceiverRegistered) {
            ContextCompat.registerReceiver(
                service,
                legacyScoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            legacyReceiverRegistered = true
        }
        audioManager.startBluetoothSco()
        legacyScoRequested = true
    }

    /**
     * Kept in its own generated class so API 31 audio symbols are never resolved while
     * loading the outer router on Android 8.1-11.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCommunicationRoute(
        private val audioManager: AudioManager,
        private val callbackExecutor: Executor,
        private val onConnected: () -> Unit,
    ) {
        private var listener: AudioManager.OnCommunicationDeviceChangedListener? = null
        private var routeRequested = false

        fun start(): Boolean {
            val target = audioManager.availableCommunicationDevices.firstOrNull { device ->
                isBluetoothCommunicationDeviceType(device.type)
            } ?: run {
                Log.w(TAG, "no Bluetooth communication device available; falling back to legacy SCO")
                return false
            }

            val routeListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                Log.d(
                    TAG,
                    "communication device changed: ${device?.id ?: -1}/${device?.type ?: -1}",
                )
                if (device != null && device.id == target.id) {
                    onConnected()
                }
            }
            listener = routeListener
            audioManager.addOnCommunicationDeviceChangedListener(callbackExecutor, routeListener)

            val accepted = runCatching { audioManager.setCommunicationDevice(target) }
                .onFailure { Log.w(TAG, "setCommunicationDevice failed", it) }
                .getOrDefault(false)
            if (!accepted) {
                runCatching { audioManager.removeOnCommunicationDeviceChangedListener(routeListener) }
                listener = null
                return false
            }
            routeRequested = true

            if (audioManager.communicationDevice?.id == target.id) {
                onConnected()
            }
            return true
        }

        fun stop() {
            listener?.let { routeListener ->
                runCatching { audioManager.removeOnCommunicationDeviceChangedListener(routeListener) }
                    .onFailure { Log.w(TAG, "remove communication-device listener failed", it) }
            }
            listener = null
            if (routeRequested) {
                runCatching { audioManager.clearCommunicationDevice() }
                    .onFailure { Log.w(TAG, "clear communication device failed", it) }
            }
            routeRequested = false
        }
    }
}
