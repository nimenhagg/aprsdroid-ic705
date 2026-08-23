package org.aprsdroid.app

import android.annotation.SuppressLint
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.location.Location
import android.text.format.DateUtils
import android.view.View
import android.widget.FilterQueryProvider
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

class StationListAdapter(
    private val context: Context,
    val prefs: PrefsWrapper,
    val mycall: String,
    val targetcall: String,
    val mode: Int
) : SimpleCursorAdapter(
    context, R.layout.stationview, null,
    LIST_FROM, LIST_TO
) {

    companion object {
        val LIST_FROM = arrayOf(StorageDatabase.Companion.Station.CALL, StorageDatabase.Companion.Station.COMMENT, StorageDatabase.Companion.Station.QRG)
        val LIST_TO = intArrayOf(R.id.station_call, R.id.listmessage, R.id.station_qrg)

        const val SINGLE = 0
        const val NEIGHBORS = 1
        const val SSIDS = 2

        private val LETTERS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        fun getBearing(b: Double): String = LETTERS[(((b.toInt() + 22 + 720) % 360) / 45)]
    }

    var my_lat = 0
    var my_lon = 0
    val storage: StorageDatabase by lazy { StorageDatabase.open(context) }

    val locReceiver = LocationReceiver2(
        { loadCursor() },
        { replaceCursor(it) },
        { cancelCursor(it) }
    )

    @SuppressLint("WrongConstant")
    private fun registerReceiver() {
        ContextCompat.registerReceiver(context, locReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
    }

    init {
        if (mode == NEIGHBORS) {
            filterQueryProvider = getNeighborFilter()
        }
        reload()
        registerReceiver()
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val distage = view.findViewById<TextView>(R.id.station_distage)
        val call = cursor.getString(StorageDatabase.Companion.Station.COLUMN_CALL)
        val ts = cursor.getLong(StorageDatabase.Companion.Station.COLUMN_TS)
        val age = DateUtils.getRelativeTimeSpanString(context, ts)
        val lat = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_LAT)
        val lon = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_LON)
        val qrg = cursor.getString(StorageDatabase.Companion.Station.COLUMN_QRG)
        val symbol = cursor.getString(StorageDatabase.Companion.Station.COLUMN_SYMBOL)
        val dist = FloatArray(2)

        when (call) {
            mycall -> view.setBackgroundColor(0x2600677d)
            targetcall -> view.setBackgroundColor(0x265b53a4)
            else -> view.setBackgroundColor(0)
        }

        distage.setTextColor(0xff40484c.toInt())
        view.findViewById<TextView>(R.id.station_call).setTextColor(0xff00677d.toInt())
        val qrgView = view.findViewById<TextView>(R.id.station_qrg)
        qrgView.setTextColor(0xff006874.toInt())
        qrgView.visibility = if (!qrg.isNullOrEmpty()) View.VISIBLE else View.GONE

        val mcd = 1000000.0
        Location.distanceBetween(my_lat / mcd, my_lon / mcd, lat / mcd, lon / mcd, dist)
        distage.text = String.format(Locale.US, "%1.1f km %s\n%s", dist[0] / 1000.0, getBearing(dist[1].toDouble()), age)
        view.findViewById<SymbolView>(R.id.station_symbol).setSymbol(symbol ?: "/$")
        super.bindView(view, context, cursor)
    }

    fun getNeighborFilter(): FilterQueryProvider {
        return FilterQueryProvider { constraint ->
            if (!constraint.isNullOrEmpty()) {
                storage.getNeighborsLike("$constraint%", my_lat, my_lon, System.currentTimeMillis() - prefs.getShowAge(), "300")
            } else {
                storage.getNeighbors(mycall, my_lat, my_lon, System.currentTimeMillis() - prefs.getShowAge(), "300")
            }
        }
    }

    fun loadCursor(): Cursor {
        val cursor = storage.getStaPosition(mycall)
        if (cursor.count > 0) {
            cursor.moveToFirst()
            my_lat = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_LAT)
            my_lon = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_LON)
        }
        cursor.close()

        val c = when (mode) {
            SINGLE -> storage.getStaPosition(targetcall)
            NEIGHBORS -> storage.getNeighbors(mycall, my_lat, my_lon, System.currentTimeMillis() - prefs.getShowAge(), "300")
            SSIDS -> storage.getAllSsids(targetcall)
            else -> storage.getStaPosition(targetcall)
        }
        c.count
        return c
    }

    fun replaceCursor(c: Cursor) {
        val listAct = context as? ListActivity
        if (listAct == null || !listAct.listView.hasTextFilter()) {
            changeCursor(c)
        }
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
