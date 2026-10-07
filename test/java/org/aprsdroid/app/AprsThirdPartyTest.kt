package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsThirdParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsThirdPartyTest {

    @Test
    fun testSingleHopThirdPartyUnwrap() {
        val raw = "IGATE>APRS,TCPIP*:}BG1ABC>APRS,WIDE2-1:!3954.20N/11623.50E#Beijing Club"
        assertTrue(AprsThirdParty.isThirdPartyPayload("}BG1ABC>APRS,WIDE2-1:!3954.20N/11623.50E#Beijing Club"))

        val unwrapped = AprsThirdParty.unwrapRecursively(raw)
        assertEquals(1, unwrapped.gateways.size)
        assertEquals("IGATE", unwrapped.gateways[0].gatewaySource)
        assertEquals("APRS", unwrapped.gateways[0].gatewayDestination)
        assertEquals(listOf("TCPIP*"), unwrapped.gateways[0].gatewayPath)

        assertEquals("BG1ABC", unwrapped.innermostSource)
        assertEquals("APRS", unwrapped.innermostDestination)
        assertEquals(listOf("WIDE2-1"), unwrapped.innermostPath)
        assertEquals("!3954.20N/11623.50E#Beijing Club", unwrapped.innermostPayload)
        assertEquals("BG1ABC>APRS,WIDE2-1:!3954.20N/11623.50E#Beijing Club", unwrapped.innermostRaw)
    }

    @Test
    fun testMultiHopThirdPartyUnwrap() {
        val raw = "SERVER>APRS,TCPIP*:}GW1>APRS,WIDE1-1:}BG7XXX>APRS:=2216.45N/11410.34E#Hong Kong"
        val unwrapped = AprsThirdParty.unwrapRecursively(raw)

        assertEquals(2, unwrapped.gateways.size)
        assertEquals("SERVER", unwrapped.gateways[0].gatewaySource)
        assertEquals("GW1", unwrapped.gateways[1].gatewaySource)

        assertEquals("BG7XXX", unwrapped.innermostSource)
        assertEquals("APRS", unwrapped.innermostDestination)
        assertEquals("=2216.45N/11410.34E#Hong Kong", unwrapped.innermostPayload)
    }

    @Test
    fun testNonThirdPartyPassThrough() {
        val raw = "BG7XXX>APRS,WIDE1-1:!2216.45N/11410.34E#Direct RF"
        val unwrapped = AprsThirdParty.unwrapRecursively(raw)

        assertTrue(unwrapped.gateways.isEmpty())
        assertEquals("BG7XXX", unwrapped.innermostSource)
        assertEquals("APRS", unwrapped.innermostDestination)
        assertEquals(listOf("WIDE1-1"), unwrapped.innermostPath)
        assertEquals("!2216.45N/11410.34E#Direct RF", unwrapped.innermostPayload)
    }
}
