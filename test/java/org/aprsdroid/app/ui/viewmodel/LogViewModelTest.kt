package org.aprsdroid.app.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.LogSource
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.service.AprsPacketEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogViewModelTest {

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

    @Before
    fun setUp() {
        ServiceEventBus.resetForTesting()
    }

    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeLogSource()
        val viewModel = LogViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.isRunning)
    }

    @Test
    fun refreshLoadsAllLogsWhenNoFilter() = runBlocking {
        val fakeSource = FakeLogSource()
        val viewModel = LogViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(400L)

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(1, fakeSource.queryCount)
        assertEquals(null, fakeSource.lastFilter)
    }

    @Test
    fun refreshWithFilterAppliesQueryFilter() = runBlocking {
        val fakeSource = FakeLogSource()
        val viewModel = LogViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh("BG1ABC")
        Thread.sleep(400L)

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("BG1ABC", fakeSource.lastFilter)
        assertEquals(1L, state.items[0].id)
    }

    @Test
    fun serviceRunningStateIsReflectedInUiState() = runBlocking {
        val fakeSource = FakeLogSource()
        val viewModel = LogViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        assertFalse(viewModel.uiState.value.isRunning)

        ServiceEventBus.updateServiceRunning(true)
        Thread.sleep(100L)
        assertTrue(viewModel.uiState.value.isRunning)

        ServiceEventBus.updateServiceRunning(false)
        Thread.sleep(100L)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun packetEventTriggersAutomaticRefresh() = runBlocking {
        val fakeSource = FakeLogSource()
        val viewModel = LogViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(400L)
        assertEquals(1, fakeSource.queryCount)

        ServiceEventBus.postPacketEvent(
            AprsPacketEvent(
                callsign = "BI1NEW",
                type = 0,
                source = "TCP",
                packet = "BI1NEW>APRS:New Packet",
            )
        )
        Thread.sleep(400L)

        assertEquals(2, fakeSource.queryCount)
    }
}
