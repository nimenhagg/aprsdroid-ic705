package org.aprsdroid.app.ui.viewmodel

import android.content.ContentValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.MessageSource
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.model.MessageItem
import org.aprsdroid.app.service.AprsMessageEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationsViewModelTest {

    @Before
    fun setUp() {
        ServiceEventBus.resetForTesting()
    }

    private class FakeMessageSource : MessageSource {
        var queryCount = 0
        var deletedCall: String? = null
        var clearedAll = false

        val conversations = mutableListOf(
            ConversationItem(
                id = 1L,
                call = "BG1ABC",
                lastMessage = "73 from Beijing",
                ts = 2000L,
            ),
            ConversationItem(
                id = 2L,
                call = "VR2XYZ",
                lastMessage = "Signal 59",
                ts = 3000L,
            )
        )

        override suspend fun getConversations(): List<ConversationItem> {
            queryCount++
            return conversations.toList()
        }

        override suspend fun getMessages(call: String): List<MessageItem> = emptyList()
        override suspend fun deleteMessage(id: Long) {}

        override suspend fun deleteMessages(call: String) {
            deletedCall = call
            conversations.removeAll { it.call.equals(call, ignoreCase = true) }
        }

        override suspend fun deleteAllMessages() {
            clearedAll = true
            conversations.clear()
        }

        override suspend fun updateMessage(id: Long, values: ContentValues): Int = 1
        override suspend fun updateMessageType(id: Long, type: Int): Int = 1
    }

    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeMessageSource()
        val viewModel = ConversationsViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        val state = viewModel.uiState.value
        assertTrue(state.conversations.isEmpty())
    }

    @Test
    fun refreshLoadsConversations() = runBlocking {
        val fakeSource = FakeMessageSource()
        val viewModel = ConversationsViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(150L)

        val state = viewModel.uiState.value
        assertEquals(2, state.conversations.size)
        assertEquals("BG1ABC", state.conversations[0].call)
        assertEquals("VR2XYZ", state.conversations[1].call)
        assertEquals(1, fakeSource.queryCount)
    }

    @Test
    fun deleteConversationRemovesItemAndRefreshes() = runBlocking {
        val fakeSource = FakeMessageSource()
        val viewModel = ConversationsViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(100L)
        assertEquals(2, viewModel.uiState.value.conversations.size)

        viewModel.deleteConversation("BG1ABC")
        Thread.sleep(150L)

        assertEquals("BG1ABC", fakeSource.deletedCall)
        assertEquals(1, viewModel.uiState.value.conversations.size)
        assertEquals("VR2XYZ", viewModel.uiState.value.conversations[0].call)
    }

    @Test
    fun clearAllConversationsClearsListAndRefreshes() = runBlocking {
        val fakeSource = FakeMessageSource()
        val viewModel = ConversationsViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(100L)
        assertEquals(2, viewModel.uiState.value.conversations.size)

        viewModel.clearAllConversations()
        Thread.sleep(150L)

        assertTrue(fakeSource.clearedAll)
        assertTrue(viewModel.uiState.value.conversations.isEmpty())
    }

    @Test
    fun serviceEventBusMessageEventsTriggerAutomaticRefresh() = runBlocking {
        val fakeSource = FakeMessageSource()
        val viewModel = ConversationsViewModel(fakeSource, CoroutineScope(Dispatchers.Default))

        viewModel.refresh()
        Thread.sleep(100L)
        assertEquals(1, fakeSource.queryCount)

        ServiceEventBus.postMessageEvent(
            AprsMessageEvent(
                destination = "N0CALL",
                sender = "BG1ABC",
                body = "New message arrived",
                isOutgoing = false,
            )
        )
        Thread.sleep(150L)

        assertEquals(2, fakeSource.queryCount)
    }
}
