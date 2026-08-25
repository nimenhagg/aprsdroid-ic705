package org.aprsdroid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aprsdroid.app.data.repository.MessageRepository
import org.aprsdroid.app.model.ConversationItem

data class ConversationsUiState(
    val conversations: List<ConversationItem> = emptyList()
)

class ConversationsViewModel(
    private val repository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val list = repository.getConversations()
            _uiState.update { it.copy(conversations = list) }
        }
    }

    fun deleteConversation(call: String) {
        viewModelScope.launch {
            repository.deleteMessages(call)
            refresh()
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.deleteAllMessages()
            refresh()
        }
    }
}
