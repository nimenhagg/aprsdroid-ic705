package org.aprsdroid.app.ic705.session

import java.net.InetAddress
import org.aprsdroid.app.ic705.protocol.Ic705CivCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ic705RxSessionConfigTest {

    private val validAddress = InetAddress.getByName("192.168.59.1")

    @Test
    fun defaultRadioCivAddressIs0xA4() {
        val config = Ic705RxSessionConfig(
            radioAddress = validAddress,
            controlPort = 50001,
            username = "ic705",
            password = "password",
        )
        assertEquals(Ic705CivCommands.DEFAULT_RADIO_ADDRESS, config.radioCivAddress)
        assertEquals(0xA4, config.radioCivAddress)
        assertTrue(config.toString().contains("radioCivAddress=A4"))
    }

    @Test
    fun customRadioCivAddressIsPreserved() {
        val config = Ic705RxSessionConfig(
            radioAddress = validAddress,
            controlPort = 50001,
            username = "ic705",
            password = "password",
            radioCivAddress = 0xA2,
        )
        assertEquals(0xA2, config.radioCivAddress)
        assertTrue(config.toString().contains("radioCivAddress=A2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRadioCivAddressIsRejected() {
        Ic705RxSessionConfig(
            radioAddress = validAddress,
            controlPort = 50001,
            username = "ic705",
            password = "password",
            radioCivAddress = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeRadioCivAddressIsRejected() {
        Ic705RxSessionConfig(
            radioAddress = validAddress,
            controlPort = 50001,
            username = "ic705",
            password = "password",
            radioCivAddress = 0xF0,
        )
    }
}
