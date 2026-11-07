package org.aprsdroid.app.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.aprsdroid.app.data.repository.StationDetailsData
import org.aprsdroid.app.data.repository.StationDetailsSource
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.service.AprsPacketEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.aprsdroid.app.awaitUntil
import org.junit.Test

class StationViewModelTest {


    private class FakeStationDetailsSource : StationDetailsSource {
        var queryCount = 0

        override suspend fun getStationDetails(
            targetCall: String,
            myCall: String,
            postLimit: String,
        ): StationDetailsData {
            queryCount++
            return StationDetailsData(
                myLat = 39904200,
                myLon = 116407400,
                stationItem = StationItem(
                    id = 1L,
                    call = targetCall,
                    comment = "Test Station",
                    qrg = "438.500MHz",
                    symbol = "/[",
                    lat = 31230400,
                    lon = 121473700,
                    ts = 1000L,
                ),
                ssidList = emptyList(),
                postList = emptyList(),
            )
        }
    }

    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeStationDetailsSource()
        val viewModel = StationViewModel(fakeSource)

        val state = viewModel.uiState.value
        assertEquals("", state.targetCall)
        assertNull(state.stationItem)
        assertEquals(0, state.myLat)
        assertEquals(0, state.myLon)
    }

    @Test
    fun requestRefreshUpdatesStateAfterBatchingWindow() = runBlocking {
        val fakeSource = FakeStationDetailsSource()
        val viewModel = StationViewModel(fakeSource)

        viewModel.requestRefresh("BG1ABC", "N0CALL")

        awaitUntil { viewModel.uiState.value.targetCall == "BG1ABC" && viewModel.uiState.value.stationItem != null }

        val state = viewModel.uiState.value
        assertEquals("BG1ABC", state.targetCall)
        assertEquals(39904200, state.myLat)
        assertEquals(116407400, state.myLon)
        assertEquals("BG1ABC", state.stationItem?.call)
        assertEquals("438.500MHz", state.stationItem?.qrg)
    }

    @Test
    fun rapidConsecutiveRequestsAreConflatedIntoSingleQuery() = runBlocking {
        val fakeSource = FakeStationDetailsSource()
        val viewModel = StationViewModel(fakeSource)

        // Dispatch multiple rapid bursts within batching window
        viewModel.requestRefresh("BG1ABC-1", "N0CALL")
        viewModel.requestRefresh("BG1ABC-2", "N0CALL")
        viewModel.requestRefresh("BG1ABC-9", "N0CALL")

        awaitUntil { viewModel.uiState.value.targetCall == "BG1ABC-9" && fakeSource.queryCount == 1 }

        // Only the latest query should be executed due to Conflated Channel
        assertEquals(1, fakeSource.queryCount)
        assertEquals("BG1ABC-9", viewModel.uiState.value.targetCall)
    }

    @Test
    fun serviceEventBusPacketEventTriggersAutomaticRefresh() = runBlocking {
        val fakeSource = FakeStationDetailsSource()
        val viewModel = StationViewModel(fakeSource)

        viewModel.requestRefresh("BG1ABC-9", "N0CALL")
        awaitUntil { viewModel.uiState.value.targetCall == "BG1ABC-9" && fakeSource.queryCount == 1 }
        assertEquals(1, fakeSource.queryCount)

        ServiceEventBus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BG1ABC-9",
                type = 3,
                source = "IC-705",
                packet = "BG1ABC-9>APRS:>Hello",
            )
        )

        awaitUntil { fakeSource.queryCount == 2 }
        assertEquals(2, fakeSource.queryCount)
    }
}
