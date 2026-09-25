package org.aprsdroid.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundServiceTypeResolverTest {
    @Test
    fun `afsk with gps uses microphone and location only`() {
        assertEquals(
            ForegroundServiceWork(microphone = true, location = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "afsk",
                protocol = "afsk",
                locationSource = "smartbeaconing",
                kenwoodGps = false,
            ),
        )
    }

    @Test
    fun `bluetooth tnc uses connected device`() {
        assertEquals(
            ForegroundServiceWork(connectedDevice = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "bluetooth",
                protocol = "kiss",
                locationSource = "manual",
                kenwoodGps = false,
            ),
        )
    }

    @Test
    fun `bluetooth tnc with gps combines connected device and location`() {
        assertEquals(
            ForegroundServiceWork(location = true, connectedDevice = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "bluetooth",
                protocol = "kiss",
                locationSource = "periodic",
                kenwoodGps = false,
            ),
        )
    }

    @Test
    fun `ic705 keeps special use and adds location when gps is active`() {
        assertEquals(
            ForegroundServiceWork(location = true, specialUse = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "ic705",
                protocol = "ic705",
                locationSource = "smartbeaconing",
                kenwoodGps = false,
            ),
        )
    }

    @Test
    fun `manual aprsis keeps special use fallback`() {
        assertEquals(
            ForegroundServiceWork(specialUse = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "tcp",
                protocol = "aprsis",
                locationSource = "manual",
                kenwoodGps = false,
            ),
        )
    }

    @Test
    fun `kenwood nmea forwarding declares location work`() {
        assertEquals(
            ForegroundServiceWork(location = true, connectedDevice = true),
            ForegroundServiceTypeResolver.determineWork(
                backendKey = "bluetooth",
                protocol = "kenwood",
                locationSource = "manual",
                kenwoodGps = true,
            ),
        )
    }
}
