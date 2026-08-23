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
import com.google.android.material.color.MaterialColors
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.LogPostItem

class LogRecyclerAdapter(
    private val onItemClick: (LogPostItem) -> Unit,
    private val onItemLongClick: ((LogPostItem, View) -> Boolean)? = null
) : ListAdapter<LogPostItem, LogRecyclerAdapter.ViewHolder>(DiffCallback) {

    companion object {
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

        val primaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val secondaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSecondary)
        val tertiaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorTertiary)
        val onSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val errorColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError)

        holder.tsView.text = item.tss
        holder.tsView.setTextColor(onSurfaceVariant)

        holder.statusView.text = item.status ?: ""
        when (item.type) {
            StorageDatabase.Companion.Post.TYPE_POST, StorageDatabase.Companion.Post.TYPE_INCMG ->
                holder.statusView.setTextColor(secondaryColor)
            StorageDatabase.Companion.Post.TYPE_TX ->
                holder.statusView.setTextColor(tertiaryColor)
            StorageDatabase.Companion.Post.TYPE_ERROR ->
                holder.statusView.setTextColor(errorColor)
            StorageDatabase.Companion.Post.TYPE_INFO ->
                holder.statusView.setTextColor(primaryColor)
            else ->
                holder.statusView.setTextColor(primaryColor)
        }

        val m = item.message
        val t = item.type

        if (m.contains(">") && (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX)) {
            val ssb = SpannableStringBuilder(m)
            val colonIdx = m.indexOf(':')
            val headerEnd = if (colonIdx > 0) colonIdx + 1 else m.length

            ssb.setSpan(ForegroundColorSpan(primaryColor), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (headerEnd < m.length) {
                ssb.setSpan(ForegroundColorSpan(onSurface), headerEnd, m.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            holder.messageView.text = ssb
            holder.messageView.typeface = Typeface.MONOSPACE
        } else {
            holder.messageView.text = m
            when (t) {
                StorageDatabase.Companion.Post.TYPE_ERROR -> holder.messageView.setTextColor(errorColor)
                StorageDatabase.Companion.Post.TYPE_INFO -> holder.messageView.setTextColor(onSurfaceVariant)
                else -> holder.messageView.setTextColor(onSurface)
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
