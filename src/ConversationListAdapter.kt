package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.text.format.DateUtils
import android.view.View
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class ConversationListAdapter(
    private val context: Context,
    prefs: PrefsWrapper
) : SimpleCursorAdapter(
    context, R.layout.conversationview, null,
    LIST_FROM, LIST_TO
) {

    companion object {
        val LIST_FROM = arrayOf(StorageDatabase.Companion.Message.CALL, StorageDatabase.Companion.Message.TEXT)
        val LIST_TO = intArrayOf(R.id.call, R.id.message)
    }

    val storage: StorageDatabase by lazy { StorageDatabase.open(context) }

    val locReceiver = LocationReceiver2(
        { loadCursor() },
        { replaceCursor(it) },
        { cancelCursor(it) }
    )

    @SuppressLint("WrongConstant")
    private fun registerReceiver() {
        ContextCompat.registerReceiver(context, locReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_EXPORTED)
    }

    init {
        reload()
        registerReceiver()
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val ts = cursor.getLong(StorageDatabase.Companion.Message.COLUMN_TS)
        view.findViewById<TextView>(R.id.call).setTextColor(0xff00677d.toInt())
        view.findViewById<TextView>(R.id.message).setTextColor(0xff40484c.toInt())
        val age = DateUtils.getRelativeTimeSpanString(context, ts)
        view.findViewById<TextView>(R.id.ts).text = age
        super.bindView(view, context, cursor)
    }

    fun loadCursor(): Cursor {
        val c = storage.getConversations()
        c.count
        return c
    }

    fun replaceCursor(c: Cursor) {
        changeCursor(c)
        (context as? LoadingIndicator)?.onStopLoading()
    }

    fun cancelCursor(c: Cursor) {
        c.close()
    }

    fun reload() {
        locReceiver.startTask(Intent())
    }

    fun onDestroy() {
        try { context.unregisterReceiver(locReceiver) } catch (_: Exception) {}
        changeCursor(null)
    }
}
