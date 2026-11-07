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
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.LogSource
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.service.AprsPacketEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val bus = ServiceEventBus()
    private var viewModel: LogViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    private class FakeLogSource : LogSource {
        var queryCount = 0
        var lastFilter: String? = null

        val logs = mutableListOf(
            LogPostItem(
                id = 1L,
                ts = 1000L,
                tss = "12:00:00",
                type = StorageDatabase.Companion.Post.TYPE_POST,
                status = "OK",
                message = "BG1ABC>APRS:=3954.12N/11624.34E-",
            ),
            LogPostItem(
                id = 2L,
                ts = 2000L,
                tss = "12:01:00",
                type = StorageDatabase.Companion.Post.TYPE_INCMG,
                status = "OK",
                message = "BI1XYZ>APRS:>Hello world",
            ),
        )

        override suspend fun getLogs(filter: String?): List<LogPostItem> {
            queryCount++
            lastFilter = filter
            return if (filter != null) {
                logs.filter { it.message.contains(filter, ignoreCase = true) }
            } else {
                logs.toList()
            }
        }
    }



    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeLogSource()
        viewModel = LogViewModel(fakeSource, bus)

        val state = viewModel!!.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.isRunning)
    }

    @Test
    fun refreshLoadsAllLogsWhenNoFilter() = runTest(testScheduler) {
        val fakeSource = FakeLogSource()
        viewModel = LogViewModel(fakeSource, bus)

        viewModel!!.refresh()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(1, fakeSource.queryCount)
        assertEquals(null, fakeSource.lastFilter)
    }

    @Test
    fun refreshWithFilterAppliesQueryFilter() = runTest(testScheduler) {
        val fakeSource = FakeLogSource()
        viewModel = LogViewModel(fakeSource, bus)

        viewModel!!.refresh("BG1ABC")
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("BG1ABC", fakeSource.lastFilter)
        assertEquals(1L, state.items[0].id)
    }

    @Test
    fun serviceRunningStateIsReflectedInUiState() = runTest(testScheduler) {
        val fakeSource = FakeLogSource()
        viewModel = LogViewModel(fakeSource, bus)

        assertFalse(viewModel!!.uiState.value.isRunning)

        bus.updateServiceRunning(true)
        advanceUntilIdle()
        assertTrue(viewModel!!.uiState.value.isRunning)

        bus.updateServiceRunning(false)
        advanceUntilIdle()
        assertFalse(viewModel!!.uiState.value.isRunning)
    }

    @Test
    fun packetEventTriggersAutomaticRefresh() = runTest(testScheduler) {
        val fakeSource = FakeLogSource()
        viewModel = LogViewModel(fakeSource, bus)

        viewModel!!.refresh()
        advanceUntilIdle()
        assertEquals(1, fakeSource.queryCount)

        bus.postPacketEvent(
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