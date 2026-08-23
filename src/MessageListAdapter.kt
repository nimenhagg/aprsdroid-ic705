package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.view.View
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

class MessageListAdapter(
    private val context: Context,
    prefs: PrefsWrapper,
    private val mycall: String,
    private val targetcall: String
) : SimpleCursorAdapter(
    context, R.layout.listitem, null,
    LIST_FROM, LIST_TO
) {

    companion object {
        val LIST_FROM = arrayOf("TSS", StorageDatabase.Companion.Message.TEXT)
        val LIST_TO = intArrayOf(R.id.listts, R.id.listmessage)

        // Status text colors: incoming (teal), new (indigo), acked (forest green), rejected (red), aborted (amber)
        val STATUS_COLORS = intArrayOf(
            0,
            0xff00677d.toInt(),
            0xff5b53a4.toInt(),
            0xff006d44.toInt(),
            0xffba1a1a.toInt(),
            0xff8c4f00.toInt()
        )
        const val NUM_OF_RETRIES = 7
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
        val msgtype = cursor.getInt(StorageDatabase.Companion.Message.COLUMN_TYPE)
        val retrycnt = cursor.getInt(StorageDatabase.Companion.Message.COLUMN_RETRYCNT)

        val msgView = view.findViewById<TextView>(R.id.listmessage)
        msgView.setTextColor(0xff191c1e.toInt())

        val statusview = view.findViewById<TextView>(R.id.liststatus)
        if (msgtype >= 0 && msgtype < STATUS_COLORS.size) {
            statusview.setTextColor(STATUS_COLORS[msgtype])
        } else {
            statusview.setTextColor(0xff00677d.toInt())
        }

        super.bindView(view, context, cursor)

        val status = when (msgtype) {
            StorageDatabase.Companion.Message.TYPE_INCOMING -> targetcall
            StorageDatabase.Companion.Message.TYPE_OUT_NEW -> String.format(Locale.US, "%s %d/%d", mycall, retrycnt, NUM_OF_RETRIES)
            StorageDatabase.Companion.Message.TYPE_OUT_ACKED -> mycall
            StorageDatabase.Companion.Message.TYPE_OUT_REJECTED -> "$mycall ${context.getString(R.string.msg_type_rejected)}"
            StorageDatabase.Companion.Message.TYPE_OUT_ABORTED -> "$mycall ${context.getString(R.string.msg_type_aborted)}"
            else -> mycall
        }
        statusview.text = status
    }

    fun loadCursor(): Cursor {
        val c = storage.getMessages(targetcall)
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
