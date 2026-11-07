package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.data.repository.MessageSource
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.service.ServiceEventBus

data class ConversationsUiState(
    val conversations: List<ConversationItem> = emptyList()
)

class ConversationsViewModel(
    private val repository: MessageSource,
    externalScope: CoroutineScope? = null,
    private val eventBus: ServiceEventBus = ServiceEventBus,
) : ViewModel() {

    private val scope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            eventBus.messageEvents.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        scope.launch {
            val list = repository.getConversations()
            _uiState.update { it.copy(conversations = list) }
        }
    }

    fun deleteConversation(call: String) {
        scope.launch {
            repository.deleteMessages(call)
            refresh()
        }
    }

    fun clearAllConversations() {
        scope.launch {
            repository.deleteAllMessages()
            refresh()
        }
    }
}
