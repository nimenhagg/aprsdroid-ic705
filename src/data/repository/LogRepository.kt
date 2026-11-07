package org.aprsdroid.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.LogPostItem

interface LogSource {
    suspend fun getLogs(filter: String? = null): List<LogPostItem>
}

class LogRepository(private val db: StorageDatabase) : LogSource {

    override suspend fun getLogs(filter: String?): List<LogPostItem> = withContext(Dispatchers.IO) {
        val cursor = db.getPosts(filter)
        LogPostItem.fromCursor(cursor)
    }
}
