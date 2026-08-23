package org.aprsdroid.app.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
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

        val primaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val secondaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSecondary)
        val tertiaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorTertiary)
        val onSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val errorColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError)

        holder.tsView.text = item.tss
        holder.tsView.setTextColor(onSurfaceVariant)

        holder.messageView.text = item.text
        holder.messageView.setTextColor(onSurface)

        val msgtype = item.type
        when (msgtype) {
            StorageDatabase.Companion.Message.TYPE_INCOMING ->
                holder.statusView.setTextColor(primaryColor)
            StorageDatabase.Companion.Message.TYPE_OUT_NEW ->
                holder.statusView.setTextColor(tertiaryColor)
            StorageDatabase.Companion.Message.TYPE_OUT_ACKED ->
                holder.statusView.setTextColor(secondaryColor)
            StorageDatabase.Companion.Message.TYPE_OUT_REJECTED ->
                holder.statusView.setTextColor(errorColor)
            StorageDatabase.Companion.Message.TYPE_OUT_ABORTED ->
                holder.statusView.setTextColor(onSurfaceVariant)
            else ->
                holder.statusView.setTextColor(primaryColor)
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
