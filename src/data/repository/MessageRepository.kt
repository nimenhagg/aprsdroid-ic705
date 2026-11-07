package org.aprsdroid.app.data.repository

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.model.MessageItem

interface MessageSource {
    suspend fun getConversations(): List<ConversationItem>
    suspend fun getMessages(call: String): List<MessageItem>
    suspend fun deleteMessage(id: Long)
    suspend fun deleteMessages(call: String)
    suspend fun deleteAllMessages()
    suspend fun updateMessage(id: Long, values: ContentValues): Int
    suspend fun updateMessageType(id: Long, type: Int): Int
}

class MessageRepository(private val db: StorageDatabase) : MessageSource {

    override suspend fun getConversations(): List<ConversationItem> = withContext(Dispatchers.IO) {
        val cursor = db.getConversations()
        ConversationItem.fromCursor(cursor)
    }

    override suspend fun getMessages(call: String): List<MessageItem> = withContext(Dispatchers.IO) {
        val cursor = db.getMessages(call)
        MessageItem.fromCursor(cursor)
    }

    override suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        db.deleteMessage(id)
    }

    override suspend fun deleteMessages(call: String) = withContext(Dispatchers.IO) {
        db.deleteMessages(call)
    }

    override suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
        db.deleteAllMessages()
    }

    override suspend fun updateMessage(id: Long, values: ContentValues): Int = withContext(Dispatchers.IO) {
        db.updateMessage(id, values)
    }

    override suspend fun updateMessageType(id: Long, type: Int): Int = withContext(Dispatchers.IO) {
        db.updateMessageType(id, type)
    }
}
