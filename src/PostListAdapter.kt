package org.aprsdroid.app

import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.SimpleCursorAdapter
import android.widget.TextView

class PostListAdapter(context: Context) : SimpleCursorAdapter(
    context, R.layout.listitem, null,
    LIST_FROM, LIST_TO
) {

    companion object {
        val LIST_FROM = arrayOf("TSS", StorageDatabase.Companion.Post.STATUS, StorageDatabase.Companion.Post.MESSAGE)
        val LIST_TO = intArrayOf(R.id.listts, R.id.liststatus, R.id.listmessage)
    }

    init {
        viewBinder = PostViewBinder()
    }

    class PostViewBinder : ViewBinder {
        companion object {
            const val COLUMN_STATUS = 4
            val STATUS_COLORS = intArrayOf(
                0xff006d44.toInt(),
                0xff5b53a4.toInt(),
                0xffba1a1a.toInt(),
                0xff00677d.toInt(),
                0xff006d44.toInt()
            )
        }

        override fun setViewValue(view: View, cursor: Cursor, columnIndex: Int): Boolean {
            when (columnIndex) {
                COLUMN_STATUS -> {
                    val t = cursor.getInt(StorageDatabase.Companion.Post.COLUMN_TYPE)
                    val s = cursor.getString(COLUMN_STATUS)
                    val v = view as TextView
                    v.text = s
                    if (t >= 0 && t < STATUS_COLORS.size) {
                        v.setTextColor(STATUS_COLORS[t])
                    } else {
                        v.setTextColor(0xff00677d.toInt())
                    }
                    return true
                }
                StorageDatabase.Companion.Post.COLUMN_MESSAGE -> {
                    val t = cursor.getInt(StorageDatabase.Companion.Post.COLUMN_TYPE)
                    val m = cursor.getString(StorageDatabase.Companion.Post.COLUMN_MESSAGE)
                    val v = view as TextView

                    if (m != null && m.contains(">") && (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX)) {
                        val ssb = SpannableStringBuilder(m)
                        val colonIdx = m.indexOf(':')
                        val headerEnd = if (colonIdx > 0) colonIdx + 1 else m.length

                        ssb.setSpan(ForegroundColorSpan(0xff00677d.toInt()), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ssb.setSpan(StyleSpan(Typeface.BOLD), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                        if (headerEnd < m.length) {
                            ssb.setSpan(ForegroundColorSpan(0xff191c1e.toInt()), headerEnd, m.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        v.text = ssb
                        v.typeface = Typeface.MONOSPACE
                    } else {
                        v.text = m
                        when (t) {
                            StorageDatabase.Companion.Post.TYPE_ERROR -> v.setTextColor(0xffba1a1a.toInt())
                            StorageDatabase.Companion.Post.TYPE_INFO -> v.setTextColor(0xff40484c.toInt())
                            else -> v.setTextColor(0xff191c1e.toInt())
                        }
                        v.typeface = if (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX) {
                            Typeface.MONOSPACE
                        } else {
                            Typeface.DEFAULT
                        }
                    }
                    return true
                }
                else -> return false
            }
        }
    }
}
