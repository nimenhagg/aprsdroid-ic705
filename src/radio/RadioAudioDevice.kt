package org.aprsdroid.app.radio

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Descriptor for an explicit audio endpoint (e.g. USB sound card codec).
 *
 * Used by the generic Radio Audio backend to avoid relying on Android default audio routing.
 */
data class RadioAudioDevice(
    val id: Int,
    val productName: String,
    val type: Int,
    val isSource: Boolean,
    val isSink: Boolean,
) {
    val isUsb: Boolean
        get() = type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET

    companion object {
        fun fromDeviceInfo(device: AudioDeviceInfo): RadioAudioDevice {
            return RadioAudioDevice(
                id = device.id,
                productName = device.productName?.toString().orEmpty(),
                type = device.type,
                isSource = device.isSource,
                isSink = device.isSink,
            )
        }

        fun listUsbDevices(audioManager: AudioManager, isInput: Boolean): List<RadioAudioDevice> {
            val flags = if (isInput) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
            return audioManager.getDevices(flags)
                .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                .map { fromDeviceInfo(it) }
        }

        fun findDeviceInfo(audioManager: AudioManager, deviceId: Int): AudioDeviceInfo? {
            val flags = AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS
            return audioManager.getDevices(flags).firstOrNull { it.id == deviceId }
        }
    }
}
