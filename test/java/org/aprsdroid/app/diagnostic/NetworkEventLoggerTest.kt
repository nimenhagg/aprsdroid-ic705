package org.aprsdroid.app.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkEventLoggerTest {
    @Test
    fun `snapshot keeps active network first and appends tracked wifi`() {
        assertEquals(
            listOf("cellular", "wifi-radio", "wifi-home"),
            NetworkEventLogger.mergeSnapshotNetworks(
                activeNetwork = "cellular",
                trackedWifiNetworks = listOf("wifi-radio", "wifi-home"),
            ),
        )
    }

    @Test
    fun `snapshot deduplicates active wifi already tracked`() {
        assertEquals(
            listOf("wifi-radio", "wifi-home"),
            NetworkEventLogger.mergeSnapshotNetworks(
                activeNetwork = "wifi-radio",
                trackedWifiNetworks = listOf("wifi-radio", "wifi-home"),
            ),
        )
    }

    @Test
    fun `snapshot preserves tracked wifi order without active network`() {
        assertEquals(
            listOf("wifi-radio", "wifi-home"),
            NetworkEventLogger.mergeSnapshotNetworks(
                activeNetwork = null,
                trackedWifiNetworks = listOf("wifi-radio", "wifi-home"),
            ),
        )
    }
}
