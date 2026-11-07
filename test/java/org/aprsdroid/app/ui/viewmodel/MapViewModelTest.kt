package org.aprsdroid.app.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.aprsdroid.app.awaitUntil
import org.aprsdroid.app.data.repository.MapStationSource
import org.aprsdroid.app.map.MapStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelTest {


    private class FakeMapStationSource : MapStationSource {
        var queryCount = 0
        var lastShowObjects: Boolean? = null
        var shouldThrow = false

        val stations = mutableListOf(
            MapStation(
                call = "BG1ABC",
                symbol = "/>",
                lat = 39.9042,
                lon = 116.4074,
                comment = "Base station"
            ),
            MapStation(
                call = "BI1XYZ-9",
                symbol = "/[",
                lat = 39.9142,
                lon = 116.4174,
                comment = "Mobile"
            )
        )

        override suspend fun getStations(showObjects: Boolean, targetCall: String): List<MapStation> {
            queryCount++
            lastShowObjects = showObjects
            if (shouldThrow) {
                throw RuntimeException("Database error")
            }
            return if (showObjects) {
                stations.toList()
            } else {
                stations.filter { !it.call.contains("XYZ") }
            }
        }
    }

    @Test
    fun initialStateIsEmptyBeforeRefresh() {
        val fakeSource = FakeMapStationSource()
        val viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        val state = viewModel.uiState.value
        assertEquals(0, state.stations.size)
        assertTrue(state.showObjects)
        assertFalse(state.isLoading)
        assertEquals(0, fakeSource.queryCount)
    }

    @Test
    fun refreshLoadsStations() = runBlocking {
        val fakeSource = FakeMapStationSource()
        val viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.stations.size == 2 && fakeSource.queryCount == 1 }

        val state = viewModel.uiState.value
        assertEquals(2, state.stations.size)
        assertTrue(state.showObjects)
        assertFalse(state.isLoading)
        assertEquals(1, fakeSource.queryCount)
        assertEquals(true, fakeSource.lastShowObjects)
    }

    @Test
    fun refreshWithShowObjectsToggled() = runBlocking {
        val fakeSource = FakeMapStationSource()
        val viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.stations.size == 2 }
        assertEquals(2, viewModel.uiState.value.stations.size)

        viewModel.refresh(showObjects = false)
        awaitUntil { viewModel.uiState.value.stations.size == 1 && !viewModel.uiState.value.showObjects }

        val state = viewModel.uiState.value
        assertEquals(1, state.stations.size)
        assertEquals("BG1ABC", state.stations[0].call)
        assertFalse(state.showObjects)
        assertEquals(false, fakeSource.lastShowObjects)
    }

    @Test
    fun refreshPreservesShowObjectsState() = runBlocking {
        val fakeSource = FakeMapStationSource()
        val viewModel = MapViewModel(fakeSource, initialShowObjects = false)

        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.stations.size == 1 }
        assertEquals(1, viewModel.uiState.value.stations.size)

        fakeSource.stations.add(
            MapStation(
                call = "BA1AA",
                symbol = "/-",
                lat = 39.92,
                lon = 116.42,
                comment = "House"
            )
        )

        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.stations.size == 2 }

        val state = viewModel.uiState.value
        assertEquals(2, state.stations.size)
        assertFalse(state.showObjects)
    }

    @Test
    fun failureResetsLoading() = runBlocking {
        val fakeSource = FakeMapStationSource().apply { shouldThrow = true }
        val viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading && fakeSource.queryCount == 1 }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.stations.size)
    }
}