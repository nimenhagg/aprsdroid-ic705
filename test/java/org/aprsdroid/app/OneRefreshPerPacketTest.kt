package org.aprsdroid.app

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
import org.aprsdroid.app.service.AprsPacketEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.aprsdroid.app.ui.viewmodel.HubViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OneRefreshPerPacketTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private var viewModel: HubViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    private class TestPreferenceStore : AppPreferenceStore {
        override fun getString(key: String, defValue: String?): String = defValue ?: ""
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun getStringInt(key: String, defValue: Int): Int = defValue
        override fun set(name: String, newVal: String): String = newVal
        override fun set(name: String, newVal: Boolean): Boolean = newVal
        override fun saveCredentials(callsign: String, passcode: String) {}
    }

    private class TrackingHubStationSource : HubStationSource {
        var queryCount = 0

        override suspend fun getHubData(
            myCall: String,
            maxAgeMs: Long,
            limit: String,
            includeStations: Boolean,
            searchQuery: String,
        ): HubData {
            queryCount++
            return HubData(
                myLat = 0,
                myLon = 0,
                stations = emptyList(),
            )
        }
    }

    @Test
    fun singlePacketEventTriggersExactlyOneQuery() = runTest(testScheduler) {
        val bus = ServiceEventBus()
        val source = TrackingHubStationSource()
        val prefs = TestPreferenceStore()

        viewModel = HubViewModel(source, prefs, bus)
        advanceUntilIdle()
        assertEquals(0, source.queryCount)

        bus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BI1AXX",
                type = 0,
                source = "IC-705",
                packet = "BI1AXX>APRS:>Single query regression",
            )
        )
        advanceUntilIdle()

        assertEquals(1, source.queryCount)
    }

    @Test
    fun priorPacketDoesNotTriggerQueryOnNewSubscriberWhenReplayZero() = runTest(testScheduler) {
        val bus = ServiceEventBus()
        val source = TrackingHubStationSource()
        val prefs = TestPreferenceStore()

        // Prior packet sent before subscriber exists
        bus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BI1AXX",
                type = 0,
                source = "IC-705",
                packet = "BI1AXX>APRS:>Prior packet",
            )
        )

        // New subscriber joins
        viewModel = HubViewModel(source, prefs, bus)
        advanceUntilIdle()

        // With replay = 0, no old packet is delivered, so queryCount is 0.
        // If replay was >= 1, the old packet would be delivered, triggering queryCount == 1.
        assertEquals(0, source.queryCount)

        // Then posting 1 new packet triggers exactly 1 query
        bus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BI1AXX",
                type = 0,
                source = "IC-705",
                packet = "BI1AXX>APRS:>New packet",
            )
        )
        advanceUntilIdle()
        assertEquals(1, source.queryCount)
    }
}
