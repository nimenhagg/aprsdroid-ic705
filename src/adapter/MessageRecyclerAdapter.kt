package org.aprsdroid.app.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.MessageItem

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
                return oldItem == newItem && oldItem.type == newItem.type && oldItem.retryCnt == newItem.retryCnt
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
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
        val surfaceContainerLow = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceContainerLow)
        val surfaceContainerHigh = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val outlineVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant)
        val onSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val errorColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError)

        holder.tsView.text = item.tss
        holder.tsView.setTextColor(onSurfaceVariant)

        holder.messageView.text = item.text
        holder.messageView.setTextColor(onSurface)

        val msgtype = item.type
        val isOutgoing = (msgtype != StorageDatabase.Companion.Message.TYPE_INCOMING)

        if (isOutgoing) {
            holder.card.setCardBackgroundColor(surfaceContainerHigh)
        } else {
            holder.card.setCardBackgroundColor(surfaceContainerLow)
        }
        holder.card.strokeColor = outlineVariant
        holder.card.strokeWidth = (context.resources.displayMetrics.density * 1f).toInt()

        val (statusText, statusColor) = when (msgtype) {
            StorageDatabase.Companion.Message.TYPE_INCOMING -> {
                "📥 来自 $targetcall" to secondaryColor
            }
            StorageDatabase.Companion.Message.TYPE_OUT_NEW -> {
                val retryStr = if (item.retryCnt > 0) " (重试 ${item.retryCnt}/$NUM_OF_RETRIES)" else " (待发 0/$NUM_OF_RETRIES)"
                "⏳ 发送中$retryStr" to tertiaryColor
            }
            StorageDatabase.Companion.Message.TYPE_OUT_ACKED -> {
                "✓✓ 已送达 (ACK)" to primaryColor
            }
            StorageDatabase.Companion.Message.TYPE_OUT_REJECTED -> {
                "✕ 对方拒绝 (REJ)" to errorColor
            }
            StorageDatabase.Companion.Message.TYPE_OUT_ABORTED -> {
                "⊘ 发送失败/已中止" to errorColor
            }
            else -> {
                mycall to onSurfaceVariant
            }
        }

        holder.statusView.text = statusText
        holder.statusView.setTextColor(statusColor)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { v -> onItemLongClick(item, v) }
    }
}
