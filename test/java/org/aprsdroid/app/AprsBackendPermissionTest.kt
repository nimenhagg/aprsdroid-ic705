package org.aprsdroid.app

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsBackendPermissionTest {
    @Test
    fun localNetworkPermissionIsRequiredForIc705OnAndroid17() {
        assertTrue(
            AprsBackend.requiresLocalNetworkPermission(
                backendKey = "ic705",
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN
            )
        )
    }

    @Test
    fun localNetworkPermissionIsRequiredForLanTncOnAndroid17() {
        assertTrue(
            AprsBackend.requiresLocalNetworkPermission(
                backendKey = "tcpip",
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN
            )
        )
    }

    @Test
    fun localNetworkPermissionIsNotRequestedForInternetOrOlderAndroid() {
        assertFalse(
            AprsBackend.requiresLocalNetworkPermission(
                backendKey = "tcp",
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN
            )
        )
        assertFalse(
            AprsBackend.requiresLocalNetworkPermission(
                backendKey = "ic705",
                sdkInt = Build.VERSION_CODES.BAKLAVA
            )
        )
    }
}
