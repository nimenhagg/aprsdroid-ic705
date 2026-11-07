package org.aprsdroid.app.ui.viewmodel

import android.content.ContentValues
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
import org.aprsdroid.app.data.repository.MessageSource
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.model.MessageItem
import org.aprsdroid.app.service.AprsMessageEvent
import org.aprsdroid.app.service.ServiceEventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageChatViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val bus = ServiceEventBus()
    private var viewModel: MessageChatViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    private class FakeMessageSource : MessageSource {
        var queryCount = 0
        var deletedId: Long? = null
        var deletedCall: String? = null
        var clearedAll: Boolean = false
        var updatedId: Long? = null
        var updatedValues: ContentValues? = null
        var updatedType: Int? = null

        val messages = mutableListOf(
            MessageItem(
                id = 1L,
                call = "BG1ABC",
                type = StorageDatabase.Companion.Message.TYPE_INCOMING,
                text = "Hello 705",
                ts = 1000L,
                tss = "12:00",
                retryCnt = 0,
                msgId = "101",
            )
        )

        override suspend fun getConversations(): List<ConversationItem> = emptyList()

        override suspend fun getMessages(call: String): List<MessageItem> {
            queryCount++
            return messages.filter { it.call.equals(call, ignoreCase = true) }
        }

        override suspend fun deleteMessage(id: Long) {
            deletedId = id
            messages.removeAll { it.id == id }
        }

        override suspend fun deleteMessages(call: String) {
            deletedCall = call
            messages.removeAll { it.call.equals(call, ignoreCase = true) }
        }

        override suspend fun deleteAllMessages() {
            clearedAll = true
            messages.clear()
        }

        override suspend fun updateMessage(id: Long, values: ContentValues): Int {
            updatedId = id
            updatedValues = values
            return 1
        }

        override suspend fun updateMessageType(id: Long, type: Int): Int {
            updatedId = id
            updatedType = type
            return 1
        }
    }

    @Test
    fun initialStateIsEmpty() {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)

        val state = viewModel!!.uiState.value
        assertEquals("", state.targetCall)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun refreshLoadsMessagesForCall() = runTest(testScheduler) {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)

        viewModel!!.refresh("BG1ABC")
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals("BG1ABC", state.targetCall)
        assertEquals(1, state.messages.size)
        assertEquals("Hello 705", state.messages[0].text)
        assertEquals(1, fakeSource.queryCount)
    }

    @Test
    fun deleteMessageRemovesItemAndRefreshes() = runTest(testScheduler) {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)

        viewModel!!.refresh("BG1ABC")
        advanceUntilIdle()
        assertEquals(1, viewModel!!.uiState.value.messages.size)

        viewModel!!.deleteMessage(1L, "BG1ABC")
        advanceUntilIdle()

        assertEquals(1L, fakeSource.deletedId)
        assertTrue(viewModel!!.uiState.value.messages.isEmpty())
    }

    @Test
    fun restartMessageUpdatesValuesAndInvokesCallback() = runTest(testScheduler) {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)
        val target = fakeSource.messages[0]

        var callbackCalled = false
        viewModel!!.restartMessage(target, "BG1ABC") {
            callbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals(1L, fakeSource.updatedId)
        assertNotNull(fakeSource.updatedValues)
    }

    @Test
    fun abortMessageUpdatesTypeAndInvokesCallback() = runTest(testScheduler) {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)
        val target = fakeSource.messages[0]

        var callbackCalled = false
        viewModel!!.abortMessage(target, "BG1ABC") {
            callbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals(1L, fakeSource.updatedId)
        assertEquals(StorageDatabase.Companion.Message.TYPE_OUT_ABORTED, fakeSource.updatedType)
    }

    @Test
    fun serviceEventBusMessageEventsTriggerAutomaticRefreshForTarget() = runTest(testScheduler) {
        val fakeSource = FakeMessageSource()
        viewModel = MessageChatViewModel(fakeSource, bus)

        viewModel!!.refresh("BG1ABC")
        advanceUntilIdle()
        assertEquals(1, fakeSource.queryCount)

        // Event for unrelated callsign does not refresh
        bus.postMessageEvent(
            AprsMessageEvent(
                destination = "N0CALL",
                sender = "BI1XYZ",
                body = "Other chat",
                isOutgoing = false,
            )
        )
        assertEquals(1, fakeSource.queryCount)

        // Event where destination is targetCall triggers refresh
        bus.postMessageEvent(
            AprsMessageEvent(
                destination = "BG1ABC",
                sender = "N0CALL",
                body = "To my target",
                isOutgoing = true,
            )
        )
        advanceUntilIdle()
        assertEquals(2, fakeSource.queryCount)

        // Event where sender is targetCall triggers refresh
        bus.postMessageEvent(
            AprsMessageEvent(
                destination = "N0CALL",
                sender = "BG1ABC",
                body = "From my target",
                isOutgoing = false,
            )
        )
        advanceUntilIdle()
        assertEquals(3, fakeSource.queryCount)
    }
}
