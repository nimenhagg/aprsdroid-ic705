package org.aprsdroid.app.adapter

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.R
import org.aprsdroid.app.model.ConversationItem

class ConversationRecyclerAdapter(
    private val context: Context,
    private val onItemClick: (ConversationItem) -> Unit,
    private val onItemLongClick: (ConversationItem, View) -> Boolean
) : ListAdapter<ConversationItem, ConversationRecyclerAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private object DiffCallback : DiffUtil.ItemCallback<ConversationItem>() {
            override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
                return oldItem.call == newItem.call
            }

            override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val callView: TextView = itemView.findViewById(R.id.call)
        val tsView: TextView = itemView.findViewById(R.id.ts)
        val messageView: TextView = itemView.findViewById(R.id.message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.conversationview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.callView.text = item.call
        holder.callView.setTextColor(0xff00677d.toInt())

        holder.messageView.text = item.lastMessage
        holder.messageView.setTextColor(0xff40484c.toInt())

        val age = DateUtils.getRelativeTimeSpanString(context, item.ts)
        holder.tsView.text = age

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { v -> onItemLongClick(item, v) }
    }
}
