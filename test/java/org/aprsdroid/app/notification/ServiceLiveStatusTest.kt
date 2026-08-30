package org.aprsdroid.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceLiveStatusTest {
    @Test
    fun protocolMappingCoversSupportedBackends() {
        assertEquals(LiveBackendMode.IC705, LiveBackendMode.fromProtocol("ic705"))
        assertEquals(LiveBackendMode.APRS_IS, LiveBackendMode.fromProtocol("aprsis"))
        assertEquals(LiveBackendMode.AFSK, LiveBackendMode.fromProtocol("afsk"))
        assertEquals(LiveBackendMode.KISS, LiveBackendMode.fromProtocol("kiss"))
        assertEquals(LiveBackendMode.KENWOOD, LiveBackendMode.fromProtocol("kenwood"))
        assertEquals(LiveBackendMode.TNC2, LiveBackendMode.fromProtocol("tnc2"))
        assertEquals(LiveBackendMode.OTHER, LiveBackendMode.fromProtocol("unknown"))
    }

    @Test
    fun readyKindsMatchBackendSemantics() {
        assertEquals(ReadyKind.IDLE, LiveBackendMode.IC705.readyKind)
        assertEquals(ReadyKind.LISTENING, LiveBackendMode.AFSK.readyKind)
        assertEquals(ReadyKind.ONLINE, LiveBackendMode.APRS_IS.readyKind)
        assertEquals(ReadyKind.ONLINE, LiveBackendMode.KISS.readyKind)
    }
}
