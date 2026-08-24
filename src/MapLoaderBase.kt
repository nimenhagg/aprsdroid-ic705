package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import androidx.core.content.ContextCompat
import java.util.ArrayList

open class Station(
    val call: String,
    val origin: String?,
    val symbol: String?,
    val lat: Double,
    val lon: Double,
    val qrg: String?,
    val comment: String?,
    val speed: Int,
    val course: Int,
    val movelog: List<Point>?
)

abstract class MapLoaderBase : MapMenuHelper() {

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    val locReceiver = LocationReceiver2(
        { load_stations(it) },
        { load_finished(it) },
        { /* cancel */ }
    )

    override fun onDestroy() {
        try { unregisterReceiver(locReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    open fun newStation(
        call: String, origin: String?, symbol: String?,
        lat: Double, lon: Double,
        qrg: String?, comment: String?, speed: Int, course: Int,
        movelog: List<Point>?
    ): Station {
        return Station(call, origin, symbol, lat, lon, qrg, comment, speed, course, movelog)
    }

    abstract fun onStationUpdate(sl: ArrayList<Station>)

    @SuppressLint("WrongConstant")
    fun startLoading() {
        locReceiver.startTask(Intent())
        ContextCompat.registerReceiver(this, locReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun load_stations(i: Intent): ArrayList<Station> {
        val s = ArrayList<Station>()
        val ageTs = (System.currentTimeMillis() - prefs.getShowAge()).toString()
        val filter = if (showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
        val c = db.getStations(filter, arrayOf(ageTs, targetcall), null)
        c.moveToFirst()
        while (!c.isAfterLast) {
            val call = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_CALL)
            val lat = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LAT)
            val lon = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LON)
            val symbol = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_SYMBOL)
            val origin = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_ORIGIN)
            val qrg = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_QRG)
            val comment = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_COMMENT)
            val speed = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_SPEED)
            val cse = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_CSE)

            if (!call.isNullOrEmpty()) {
                s.add(newStation(call, origin, symbol, lat / 1000000.0, lon / 1000000.0, qrg, comment, speed, cse, null))
            }
            c.moveToNext()
        }
        c.close()
        return s
    }

    fun load_finished(s: ArrayList<Station>) {
        onStationUpdate(s)
        onStopLoading()
    }
}
