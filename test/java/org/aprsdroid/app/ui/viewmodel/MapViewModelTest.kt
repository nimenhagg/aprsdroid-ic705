package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.aprsdroid.app.data.repository.MapStationSource
import org.aprsdroid.app.map.MapStation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private var viewModel: MapViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

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
        viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        val state = viewModel!!.uiState.value
        assertEquals(0, state.stations.size)
        assertTrue(state.showObjects)
        assertFalse(state.isLoading)
        assertEquals(0, fakeSource.queryCount)
    }

    @Test
    fun refreshLoadsStations() = runTest(testScheduler) {
        val fakeSource = FakeMapStationSource()
        viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel!!.refresh()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(2, state.stations.size)
        assertTrue(state.showObjects)
        assertFalse(state.isLoading)
        assertEquals(1, fakeSource.queryCount)
        assertEquals(true, fakeSource.lastShowObjects)
    }

    @Test
    fun refreshWithShowObjectsToggled() = runTest(testScheduler) {
        val fakeSource = FakeMapStationSource()
        viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel!!.refresh()
        advanceUntilIdle()
        assertEquals(2, viewModel!!.uiState.value.stations.size)

        viewModel!!.refresh(showObjects = false)
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(1, state.stations.size)
        assertEquals("BG1ABC", state.stations[0].call)
        assertFalse(state.showObjects)
        assertEquals(false, fakeSource.lastShowObjects)
    }

    @Test
    fun refreshPreservesShowObjectsState() = runTest(testScheduler) {
        val fakeSource = FakeMapStationSource()
        viewModel = MapViewModel(fakeSource, initialShowObjects = false)

        viewModel!!.refresh()
        advanceUntilIdle()
        assertEquals(1, viewModel!!.uiState.value.stations.size)

        fakeSource.stations.add(
            MapStation(
                call = "BA1AA",
                symbol = "/-",
                lat = 39.92,
                lon = 116.42,
                comment = "House"
            )
        )

        viewModel!!.refresh()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(2, state.stations.size)
        assertFalse(state.showObjects)
    }

    @Test
    fun failureResetsLoading() = runTest(testScheduler) {
        val fakeSource = FakeMapStationSource().apply { shouldThrow = true }
        viewModel = MapViewModel(fakeSource, initialShowObjects = true)

        viewModel!!.refresh()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.stations.size)
    }
}