package org.aprsdroid.app.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.MessageItem
import java.util.Locale

class MessageRecyclerAdapter(
    private val context: Context,
    private val mycall: String,
    private val targetcall: String,
    private val onItemClick: (MessageItem) -> Unit,
    private val onItemLongClick: (MessageItem, View) -> Boolean
) : ListAdapter<MessageItem, MessageRecyclerAdapter.ViewHolder>(DiffCallback) {

    companion object {
        val STATUS_COLORS = intArrayOf(
            0,
            0xff00677d.toInt(), // incoming (teal)
            0xff5b53a4.toInt(), // new (indigo)
            0xff006d44.toInt(), // acked (forest green)
            0xffba1a1a.toInt(), // rejected (red)
            0xff8c4f00.toInt()  // aborted (amber)
        )
        const val NUM_OF_RETRIES = 7

        private object DiffCallback : DiffUtil.ItemCallback<MessageItem>() {
            override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
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
        holder.messageView.text = item.text
        holder.messageView.setTextColor(0xff191c1e.toInt())

        val msgtype = item.type
        if (msgtype in STATUS_COLORS.indices) {
            holder.statusView.setTextColor(STATUS_COLORS[msgtype])
        } else {
            holder.statusView.setTextColor(0xff00677d.toInt())
        }

        val status = when (msgtype) {
            StorageDatabase.Companion.Message.TYPE_INCOMING -> targetcall
            StorageDatabase.Companion.Message.TYPE_OUT_NEW -> String.format(Locale.US, "%s %d/%d", mycall, item.retryCnt, NUM_OF_RETRIES)
            StorageDatabase.Companion.Message.TYPE_OUT_ACKED -> mycall
            StorageDatabase.Companion.Message.TYPE_OUT_REJECTED -> "$mycall ${context.getString(R.string.msg_type_rejected)}"
            StorageDatabase.Companion.Message.TYPE_OUT_ABORTED -> "$mycall ${context.getString(R.string.msg_type_aborted)}"
            else -> mycall
        }
        holder.statusView.text = status

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { v -> onItemLongClick(item, v) }
    }
}
