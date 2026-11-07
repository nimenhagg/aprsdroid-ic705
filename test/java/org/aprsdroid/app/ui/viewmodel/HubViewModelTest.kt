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
import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.aprsdroid.app.data.repository.HubData
import org.aprsdroid.app.data.repository.HubStationSource
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.service.AprsPacketEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private var viewModel: HubViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ServiceEventBus.resetForTesting()
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    private class FakePreferenceStore : AppPreferenceStore {
        val stringMap = mutableMapOf(
            "callsign" to "BG1ABC",
            "ssid" to "9",
            "show_age" to "30",
        )

        override fun getString(key: String, defValue: String?): String = stringMap[key] ?: (defValue ?: "")
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun getStringInt(key: String, defValue: Int): Int = stringMap[key]?.toIntOrNull() ?: defValue
        override fun set(name: String, newVal: String): String { stringMap[name] = newVal; return newVal }
        override fun set(name: String, newVal: Boolean): Boolean = newVal
        override fun saveCredentials(callsign: String, passcode: String) {}
    }

    private class FakeHubStationSource : HubStationSource {
        var queryCount = 0
        var lastSearchQuery = ""
        var lastIncludeStations = true

        val allStations = listOf(
            StationItem(
                id = 1L,
                call = "BG1ABC-9",
                comment = "Mobile station",
                qrg = "144.640MHz",
                symbol = "/>",
                lat = 39904200,
                lon = 116407400,
                ts = 1000L,
            ),
            StationItem(
                id = 2L,
                call = "BI1XYZ-7",
                comment = "Handheld",
                qrg = "144.640MHz",
                symbol = "/[",
                lat = 39910000,
                lon = 116410000,
                ts = 2000L,
            ),
        )

        override suspend fun getHubData(
            myCall: String,
            maxAgeMs: Long,
            limit: String,
            includeStations: Boolean,
            searchQuery: String,
        ): HubData {
            queryCount++
            lastSearchQuery = searchQuery
            lastIncludeStations = includeStations

            val filtered = if (searchQuery.isNotEmpty()) {
                allStations.filter {
                    it.call.contains(searchQuery, ignoreCase = true) ||
                    it.comment?.contains(searchQuery, ignoreCase = true) == true
                }
            } else {
                allStations
            }

            return HubData(
                myLat = 39904200,
                myLon = 116407400,
                stations = if (includeStations) filtered else emptyList(),
            )
        }
    }



    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeHubStationSource()
        val fakePrefs = FakePreferenceStore()
        viewModel = HubViewModel(
            fakeSource,
            fakePrefs,
            statusProvider = { "READY" },
        )

        val state = viewModel!!.uiState.value
        assertTrue(state.stations.isEmpty())
        assertEquals("", state.searchQuery)
        assertFalse(state.isSearching)
        assertFalse(state.isRunning)
    }

    @Test
    fun refreshLoadsHubDataAfterBatchingWindow() = runTest(testScheduler) {
        val fakeSource = FakeHubStationSource()
        val fakePrefs = FakePreferenceStore()
        viewModel = HubViewModel(
            fakeSource,
            fakePrefs,
            statusProvider = { "READY" },
        )

        viewModel!!.refresh()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals("BG1ABC-9", state.myCall)
        assertEquals(39904200, state.myLat)
        assertEquals(116407400, state.myLon)
        assertEquals(2, state.stations.size)
        assertEquals("BG1ABC-9", state.stations[0].call)
        assertEquals(1, fakeSource.queryCount)
    }

    @Test
    fun setSearchQueryFiltersStations() = runTest(testScheduler) {
        val fakeSource = FakeHubStationSource()
        val fakePrefs = FakePreferenceStore()
        viewModel = HubViewModel(
            fakeSource,
            fakePrefs,
            statusProvider = { "READY" },
        )

        viewModel!!.setSearchQuery("BI1XYZ")
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals("BI1XYZ", state.searchQuery)
        assertEquals(1, state.stations.size)
        assertEquals("BI1XYZ-7", state.stations[0].call)
        assertEquals("BI1XYZ", fakeSource.lastSearchQuery)
    }

    @Test
    fun serviceRunningStateIsReflected() = runTest(testScheduler) {
        val fakeSource = FakeHubStationSource()
        val fakePrefs = FakePreferenceStore()
        viewModel = HubViewModel(
            fakeSource,
            fakePrefs,
            statusProvider = { "CONNECTED" },
        )

        assertFalse(viewModel!!.uiState.value.isRunning)

        ServiceEventBus.updateServiceRunning(true)
        advanceUntilIdle()

        assertTrue(viewModel!!.uiState.value.isRunning)
        assertEquals("CONNECTED", viewModel!!.uiState.value.serviceStatus)

        ServiceEventBus.updateServiceRunning(false)
        advanceUntilIdle()
        assertFalse(viewModel!!.uiState.value.isRunning)
    }

    @Test
    fun packetEventTriggersAutomaticRefresh() = runTest(testScheduler) {
        val fakeSource = FakeHubStationSource()
        val fakePrefs = FakePreferenceStore()
        viewModel = HubViewModel(
            fakeSource,
            fakePrefs,
            statusProvider = { "READY" },
        )

        viewModel!!.refresh()
        advanceUntilIdle()
        assertEquals(1, fakeSource.queryCount)

        ServiceEventBus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BI1NEW",
                type = 0,
                source = "TCP",
                packet = "BI1NEW>APRS:New Packet",
            )
        )
        advanceUntilIdle()

        assertEquals(2, fakeSource.queryCount)
    }
}
