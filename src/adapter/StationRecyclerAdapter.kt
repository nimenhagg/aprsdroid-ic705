package org.aprsdroid.app.adapter

import android.content.Context
import android.location.Location
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.aprsdroid.app.R
import org.aprsdroid.app.SymbolView
import org.aprsdroid.app.model.StationItem
import java.util.Locale

class StationRecyclerAdapter(
    private val context: Context,
    private val mycall: String,
    private val targetcall: String,
    private val onItemClick: (StationItem) -> Unit,
    private val onItemLongClick: ((StationItem, View) -> Boolean)? = null
) : ListAdapter<StationItem, StationRecyclerAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val LETTERS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        fun getBearing(b: Double): String = LETTERS[(((b.toInt() + 22 + 720) % 360) / 45)]

        private object DiffCallback : DiffUtil.ItemCallback<StationItem>() {
            override fun areItemsTheSame(oldItem: StationItem, newItem: StationItem): Boolean {
                return oldItem.id == newItem.id || oldItem.call == newItem.call
            }

            override fun areContentsTheSame(oldItem: StationItem, newItem: StationItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    var myLat: Int = 0
    var myLon: Int = 0

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val symbolView: SymbolView = itemView.findViewById(R.id.station_symbol)
        val callView: TextView = itemView.findViewById(R.id.station_call)
        val distAgeView: TextView = itemView.findViewById(R.id.station_distage)
        val commentView: TextView = itemView.findViewById(R.id.listmessage)
        val qrgView: TextView = itemView.findViewById(R.id.station_qrg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.stationview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        val primaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val primaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
        val secondaryColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSecondary)
        val secondaryContainer = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSecondaryContainer)
        val onSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)

        when (item.call) {
            mycall -> holder.itemView.setBackgroundColor((primaryContainer and 0x00ffffff) or 0x33000000)
            targetcall -> holder.itemView.setBackgroundColor((secondaryContainer and 0x00ffffff) or 0x33000000)
            else -> holder.itemView.setBackgroundColor(0)
        }

        holder.callView.text = item.call
        holder.callView.setTextColor(primaryColor)

        holder.commentView.text = item.comment ?: ""
        holder.commentView.setTextColor(onSurface)

        if (!item.qrg.isNullOrEmpty()) {
            holder.qrgView.visibility = View.VISIBLE
            holder.qrgView.text = item.qrg
            holder.qrgView.setTextColor(secondaryColor)
        } else {
            holder.qrgView.visibility = View.GONE
        }

        val age = DateUtils.getRelativeTimeSpanString(context, item.ts)
        val dist = FloatArray(2)
        val mcd = 1000000.0
        Location.distanceBetween(myLat / mcd, myLon / mcd, item.lat / mcd, item.lon / mcd, dist)
        holder.distAgeView.text = String.format(Locale.US, "%1.1f km %s\n%s", dist[0] / 1000.0, getBearing(dist[1].toDouble()), age)
        holder.distAgeView.setTextColor(onSurfaceVariant)

        holder.symbolView.setSymbol(item.symbol)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { v -> onItemLongClick?.invoke(item, v) ?: false }
    }
}
