package org.aprsdroid.app

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AfskBluetoothAudioRouterTest {
    @Test
    fun `classic sco is accepted for communication audio`() {
        assertTrue(
            AfskBluetoothAudioRouter.isBluetoothCommunicationDeviceType(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            ),
        )
    }

    @Test
    fun `ble headset is accepted for communication audio`() {
        assertTrue(
            AfskBluetoothAudioRouter.isBluetoothCommunicationDeviceType(
                AudioDeviceInfo.TYPE_BLE_HEADSET,
            ),
        )
    }

    @Test
    fun `speaker is not selected as bluetooth afsk route`() {
        assertFalse(
            AfskBluetoothAudioRouter.isBluetoothCommunicationDeviceType(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            ),
        )
    }
}
