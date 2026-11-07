package org.aprsdroid.app

import org.aprsdroid.app.ui.navigation.MainRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HubRouteRefreshTest {

    @Test
    fun resolvesStationsRoute() {
        assertEquals(HubRefreshTarget.STATIONS, resolveRouteRefreshTarget(MainRoutes.STATIONS))
    }

    @Test
    fun resolvesMapRoute() {
        assertEquals(HubRefreshTarget.MAP, resolveRouteRefreshTarget(MainRoutes.MAP))
    }

    @Test
    fun resolvesMessagesRoute() {
        assertEquals(HubRefreshTarget.MESSAGES, resolveRouteRefreshTarget(MainRoutes.MESSAGES))
    }

    @Test
    fun resolvesPacketsRoute() {
        assertEquals(HubRefreshTarget.PACKETS, resolveRouteRefreshTarget(MainRoutes.PACKETS))
    }

    @Test
    fun unhandledRouteThrowsIllegalStateException() {
        try {
            resolveRouteRefreshTarget("unknown_unregistered_route")
            fail("Expected IllegalStateException for unhandled route")
        } catch (e: IllegalStateException) {
            assertEquals("unhandled route: unknown_unregistered_route", e.message)
        }
    }
}
