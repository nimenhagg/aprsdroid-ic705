package org.aprsdroid.app

import android.graphics.Point
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aprsdroid.app.service.ServiceEventBus

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

abstract class MapLoaderBase(
    protected val eventBus: ServiceEventBus
) : MapMenuHelper() {

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    private var packetCollector: Job? = null

    override fun onDestroy() {
        packetCollector?.cancel()
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

    fun startLoading() {
        packetCollector?.cancel()
        packetCollector = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadCurrentStations()
                eventBus.packetEvents.conflate().collect {
                    loadCurrentStations()
                }
            }
        }
    }

    protected fun reloadStations() {
        lifecycleScope.launch {
            loadCurrentStations()
        }
    }

    private suspend fun loadCurrentStations() {
        onStartLoading()
        try {
            val stations = withContext(Dispatchers.IO) { load_stations() }
            load_finished(stations)
        } finally {
            onStopLoading()
        }
    }

    fun load_stations(): ArrayList<Station> {
        val s = ArrayList<Station>()
        val ageTs = (System.currentTimeMillis() - prefs.getShowAge()).toString()
        val filter = if (showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
        val c = db.getStations(filter, arrayOf(ageTs, targetcall), null)
        try {
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
        } finally {
            c.close()
        }
        return s
    }

    fun load_finished(s: ArrayList<Station>) {
        onStationUpdate(s)
    }
}
