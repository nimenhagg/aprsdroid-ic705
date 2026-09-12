package org.aprsdroid.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class AprsPacketTests {
    @Test
    public void testBasic() {
        assertEquals(18403, AprsPacket.passcode("AB1CD"));
    }

    @Test
    public void formatCallSsidSuppressesZeroAndBlank() {
        assertEquals("BD3QID", AprsPacket.formatCallSsid("BD3QID", null));
        assertEquals("BD3QID", AprsPacket.formatCallSsid("BD3QID", ""));
        assertEquals("BD3QID", AprsPacket.formatCallSsid("BD3QID", "0"));
        assertEquals("BD3QID", AprsPacket.formatCallSsid("BD3QID", " 0 "));
    }

    @Test
    public void formatCallSsidKeepsNonZeroSsid() {
        assertEquals("BD3QID-5", AprsPacket.formatCallSsid("BD3QID", "5"));
        assertEquals("BD3QID-12", AprsPacket.formatCallSsid("BD3QID", " 12 "));
    }
}
