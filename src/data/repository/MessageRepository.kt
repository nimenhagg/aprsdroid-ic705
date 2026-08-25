package org.aprsdroid.app.data.repository

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.model.MessageItem

class MessageRepository(private val db: StorageDatabase) {

    suspend fun getConversations(): List<ConversationItem> = withContext(Dispatchers.IO) {
        val cursor = db.getConversations()
        ConversationItem.fromCursor(cursor)
    }

    suspend fun getMessages(call: String): List<MessageItem> = withContext(Dispatchers.IO) {
        val cursor = db.getMessages(call)
        MessageItem.fromCursor(cursor)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        db.deleteMessage(id)
    }

    suspend fun deleteMessages(call: String) = withContext(Dispatchers.IO) {
        db.deleteMessages(call)
    }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
        db.deleteAllMessages()
    }

    suspend fun updateMessage(id: Long, values: ContentValues) = withContext(Dispatchers.IO) {
        db.updateMessage(id, values)
    }

    suspend fun updateMessageType(id: Long, type: Int) = withContext(Dispatchers.IO) {
        db.updateMessageType(id, type)
    }
}
