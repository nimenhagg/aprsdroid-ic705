package org.aprsdroid.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.LogPostItem

class LogRepository(private val db: StorageDatabase) {

    suspend fun getLogs(filter: String? = null): List<LogPostItem> = withContext(Dispatchers.IO) {
        val cursor = db.getPosts(filter)
        LogPostItem.fromCursor(cursor)
    }
}
