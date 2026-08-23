package org.aprsdroid.app.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.LogPostItem

class LogRecyclerAdapter(
    private val onItemClick: (LogPostItem) -> Unit,
    private val onItemLongClick: ((LogPostItem, View) -> Boolean)? = null
) : ListAdapter<LogPostItem, LogRecyclerAdapter.ViewHolder>(DiffCallback) {

    companion object {
        val STATUS_COLORS = intArrayOf(
            0xff006d44.toInt(), // POST / RX
            0xff5b53a4.toInt(), // TX
            0xffba1a1a.toInt(), // ERROR
            0xff00677d.toInt(), // INFO
            0xff006d44.toInt()
        )

        private object DiffCallback : DiffUtil.ItemCallback<LogPostItem>() {
            override fun areItemsTheSame(oldItem: LogPostItem, newItem: LogPostItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LogPostItem, newItem: LogPostItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tsView: TextView = itemView.findViewById(R.id.listts)
        val statusView: TextView = itemView.findViewById(R.id.liststatus)
        val messageView: TextView = itemView.findViewById(R.id.listmessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.listitem, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.tsView.text = item.tss

        holder.statusView.text = item.status ?: ""
        if (item.type in STATUS_COLORS.indices) {
            holder.statusView.setTextColor(STATUS_COLORS[item.type])
        } else {
            holder.statusView.setTextColor(0xff00677d.toInt())
        }

        val m = item.message
        val t = item.type

        if (m.contains(">") && (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX)) {
            val ssb = SpannableStringBuilder(m)
            val colonIdx = m.indexOf(':')
            val headerEnd = if (colonIdx > 0) colonIdx + 1 else m.length

            ssb.setSpan(ForegroundColorSpan(0xff00677d.toInt()), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (headerEnd < m.length) {
                ssb.setSpan(ForegroundColorSpan(0xff191c1e.toInt()), headerEnd, m.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            holder.messageView.text = ssb
            holder.messageView.typeface = Typeface.MONOSPACE
        } else {
            holder.messageView.text = m
            when (t) {
                StorageDatabase.Companion.Post.TYPE_ERROR -> holder.messageView.setTextColor(0xffba1a1a.toInt())
                StorageDatabase.Companion.Post.TYPE_INFO -> holder.messageView.setTextColor(0xff40484c.toInt())
                else -> holder.messageView.setTextColor(0xff191c1e.toInt())
            }
            holder.messageView.typeface = if (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX) {
                Typeface.MONOSPACE
            } else {
                Typeface.DEFAULT
            }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { v -> onItemLongClick?.invoke(item, v) ?: false }
    }
}
