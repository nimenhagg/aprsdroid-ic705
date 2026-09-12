package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsPacketMessageIdentityTest {
    @Test
    fun explicitSsidZeroMatchesBareCall() {
        assertTrue(AprsPacket.sameMessageCallsign("N0CALL", "n0call-0"))
        assertEquals("N0CALL", AprsPacket.normalizeMessageCallsign(" n0call-0 "))
        assertEquals(listOf("N0CALL", "N0CALL-0"), AprsPacket.messageCallsignAliases("N0CALL-0"))
    }

    @Test
    fun nonZeroSsidRemainsDistinct() {
        assertFalse(AprsPacket.sameMessageCallsign("N0CALL-1", "N0CALL"))
        assertEquals(listOf("N0CALL-1"), AprsPacket.messageCallsignAliases("N0CALL-1"))
    }
}
