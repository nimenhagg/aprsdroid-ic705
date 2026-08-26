package org.aprsdroid.app.model

import android.database.Cursor
import org.aprsdroid.app.StorageDatabase

data class StationItem(
    val id: Long,
    val call: String,
    val comment: String?,
    val qrg: String?,
    val symbol: String,
    val lat: Int,
    val lon: Int,
    val ts: Long,
    val flags: Int = 0
) {
    val isFmo: Boolean
        get() = flags and StorageDatabase.Companion.Station.FLAG_FMO != 0
    companion object {
        fun fromCursor(cursor: Cursor): List<StationItem> {
            val list = ArrayList<StationItem>(cursor.count)
            val idIdx = cursor.getColumnIndex("_id")
            val callIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.CALL)
            val commentIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.COMMENT)
            val qrgIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.QRG)
            val symbolIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.SYMBOL)
            val latIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.LAT)
            val lonIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.LON)
            val tsIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.TS)
            val flagsIdx = cursor.getColumnIndex(StorageDatabase.Companion.Station.FLAGS)

            while (cursor.moveToNext()) {
                list.add(
                    StationItem(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        call = if (callIdx >= 0) cursor.getString(callIdx) ?: "" else "",
                        comment = if (commentIdx >= 0) cursor.getString(commentIdx) else null,
                        qrg = if (qrgIdx >= 0) cursor.getString(qrgIdx) else null,
                        symbol = if (symbolIdx >= 0) cursor.getString(symbolIdx) ?: "/$" else "/$",
                        lat = if (latIdx >= 0) cursor.getInt(latIdx) else 0,
                        lon = if (lonIdx >= 0) cursor.getInt(lonIdx) else 0,
                        ts = if (tsIdx >= 0) cursor.getLong(tsIdx) else 0L,
                        flags = if (flagsIdx >= 0) cursor.getInt(flagsIdx) else 0
                    )
                )
            }
            cursor.close()
            return list
        }
    }
}

data class LogPostItem(
    val id: Long,
    val ts: Long,
    val tss: String,
    val type: Int,
    val status: String?,
    val message: String
) {
    companion object {
        fun fromCursor(cursor: Cursor): List<LogPostItem> {
            val list = ArrayList<LogPostItem>(cursor.count)
            val idIdx = cursor.getColumnIndex("_id")
            val tsIdx = cursor.getColumnIndex(StorageDatabase.Companion.Post.TS)
            val tssIdx = cursor.getColumnIndex("TSS")
            val typeIdx = cursor.getColumnIndex(StorageDatabase.Companion.Post.TYPE)
            val statusIdx = 4 // status string column in getPosts query
            val msgIdx = cursor.getColumnIndex(StorageDatabase.Companion.Post.MESSAGE)

            while (cursor.moveToNext()) {
                list.add(
                    LogPostItem(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        ts = if (tsIdx >= 0) cursor.getLong(tsIdx) else 0L,
                        tss = if (tssIdx >= 0) cursor.getString(tssIdx) ?: "" else "",
                        type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0,
                        status = if (cursor.columnCount > statusIdx) cursor.getString(statusIdx) else null,
                        message = if (msgIdx >= 0) cursor.getString(msgIdx) ?: "" else ""
                    )
                )
            }
            cursor.close()
            return list
        }
    }
}

data class ConversationItem(
    val id: Long,
    val call: String,
    val lastMessage: String,
    val ts: Long
) {
    companion object {
        fun fromCursor(cursor: Cursor): List<ConversationItem> {
            val list = ArrayList<ConversationItem>(cursor.count)
            val idIdx = cursor.getColumnIndex("_id")
            val callIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.CALL)
            val textIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.TEXT)
            val tsIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.TS)

            while (cursor.moveToNext()) {
                list.add(
                    ConversationItem(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        call = if (callIdx >= 0) cursor.getString(callIdx) ?: "" else "",
                        lastMessage = if (textIdx >= 0) cursor.getString(textIdx) ?: "" else "",
                        ts = if (tsIdx >= 0) cursor.getLong(tsIdx) else 0L
                    )
                )
            }
            cursor.close()
            return list
        }
    }
}

data class MessageItem(
    val id: Long,
    val call: String,
    val type: Int,
    val text: String,
    val ts: Long,
    val tss: String,
    val retryCnt: Int,
    val msgId: String?
) {
    companion object {
        fun fromCursor(cursor: Cursor): List<MessageItem> {
            val list = ArrayList<MessageItem>(cursor.count)
            val idIdx = cursor.getColumnIndex("_id")
            val callIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.CALL)
            val typeIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.TYPE)
            val textIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.TEXT)
            val tsIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.TS)
            val tssIdx = cursor.getColumnIndex("TSS")
            val retryIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.RETRYCNT)
            val msgIdIdx = cursor.getColumnIndex(StorageDatabase.Companion.Message.MSGID)

            while (cursor.moveToNext()) {
                list.add(
                    MessageItem(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        call = if (callIdx >= 0) cursor.getString(callIdx) ?: "" else "",
                        type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0,
                        text = if (textIdx >= 0) cursor.getString(textIdx) ?: "" else "",
                        ts = if (tsIdx >= 0) cursor.getLong(tsIdx) else 0L,
                        tss = if (tssIdx >= 0) cursor.getString(tssIdx) ?: "" else "",
                        retryCnt = if (retryIdx >= 0) cursor.getInt(retryIdx) else 0,
                        msgId = if (msgIdIdx >= 0) cursor.getString(msgIdIdx) else null
                    )
                )
            }
            cursor.close()
            return list
        }
    }
}
