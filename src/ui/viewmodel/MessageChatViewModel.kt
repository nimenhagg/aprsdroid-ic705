package org.aprsdroid.app.ui.viewmodel

import android.content.ContentValues
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.AprsPacket
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.MessageSource
import org.aprsdroid.app.model.MessageItem
import org.aprsdroid.app.service.ServiceEventBus

data class MessageChatUiState(
    val targetCall: String = "",
    val messages: List<MessageItem> = emptyList(),
)

class MessageChatViewModel(
    private val repository: MessageSource,
    private val eventBus: ServiceEventBus = ServiceEventBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageChatUiState())
    val uiState: StateFlow<MessageChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            eventBus.messageEvents.collect { event ->
                val current = _uiState.value.targetCall
                if (current.isNotEmpty() &&
                    (AprsPacket.sameMessageCallsign(event.destination, current) ||
                     AprsPacket.sameMessageCallsign(event.sender, current))
                ) {
                    refresh(current)
                }
            }
        }
    }

    fun refresh(call: String) {
        if (call.isEmpty()) return
        viewModelScope.launch {
            val list = repository.getMessages(call)
            _uiState.update { it.copy(targetCall = call, messages = list) }
        }
    }

    fun deleteMessage(id: Long, call: String) {
        viewModelScope.launch {
            repository.deleteMessage(id)
            refresh(call)
        }
    }

    fun restartMessage(item: MessageItem, call: String, onRestartTriggered: () -> Unit) {
        viewModelScope.launch {
            val cv = ContentValues().apply {
                put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
                put(StorageDatabase.Companion.Message.RETRYCNT, 0)
                put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
            }
            repository.updateMessage(item.id, cv)
            onRestartTriggered()
            refresh(call)
        }
    }

    fun abortMessage(item: MessageItem, call: String, onAbortTriggered: () -> Unit) {
        viewModelScope.launch {
            repository.updateMessageType(item.id, StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
            onAbortTriggered()
            refresh(call)
        }
    }

    fun clearAll(call: String) {
        viewModelScope.launch {
            repository.deleteMessages(call)
            refresh(call)
        }
    }
}
