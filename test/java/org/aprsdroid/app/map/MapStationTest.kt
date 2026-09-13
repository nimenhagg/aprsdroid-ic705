package org.aprsdroid.app.map

import org.junit.Assert.*
import org.junit.Test

class MapStationTest {
    private val station = MapStation("TEST-1", "/>", 1.0, 2.0, "comment")

    @Test fun identicalDatabaseSnapshotsCompareEqual() {
        assertEquals(listOf(station), listOf(station.copy()))
        assertEquals(station.feature, station.copy(comment = "new comment").feature)
        assertNotEquals(station, station.copy(comment = "new comment"))
    }

    @Test fun movementAndInsertionReuseExistingImages() {
        val images = stationImages(listOf(station.feature))
        assertEquals(images, stationImages(listOf(station.copy(lat = 3.0).feature)))
        val inserted = station.copy(call = "AAA-1")
        val next = stationImages(listOf(inserted.feature, station.feature))
        assertEquals(setOf(StationImage("AAA-1", station.symbol)), next - images)
        assertEquals(next, stationImages(listOf(station.feature, inserted.feature)))
    }

    @Test fun symbolChangeInvalidatesOnlyThatStationsImage() {
        val other = station.copy(call = "TEST-2")
        val old = stationImages(listOf(station.feature, other.feature))
        val next = stationImages(listOf(station.copy(symbol = "/-").feature, other.feature))
        assertEquals(setOf(StationImage(null, "/-"), StationImage("TEST-1", "/-")), next - old)
        assertTrue(next.contains(StationImage("TEST-2", station.symbol)))
        assertTrue(stationImages(emptyList()).isEmpty())
    }
}
