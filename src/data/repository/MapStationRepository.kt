package org.aprsdroid.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.map.MapStation
import org.aprsdroid.app.StorageDatabase
import java.util.ArrayList

class MapStationRepository(
    private val db: StorageDatabase,
    private val prefs: PrefsWrapper
) {
    suspend fun getStations(showObjects: Boolean, targetCall: String = ""): List<MapStation> = withContext(Dispatchers.IO) {
        val ageTs = (System.currentTimeMillis() - prefs.getShowAge()).toString()
        val filter = if (showObjects) {
            "TS > ? OR CALL=?"
        } else {
            "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
        }
        val cursor = db.getStations(filter, arrayOf(ageTs, targetCall), null)
        val stations = ArrayList<MapStation>(cursor.count)
        try {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast) {
                    val call = cursor.getString(StorageDatabase.Companion.Station.COLUMN_MAP_CALL)
                    if (!call.isNullOrEmpty()) {
                        stations.add(
                            MapStation(
                                call = call,
                                symbol = cursor.getString(StorageDatabase.Companion.Station.COLUMN_MAP_SYMBOL) ?: "/$",
                                lat = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LAT) / 1_000_000.0,
                                lon = cursor.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LON) / 1_000_000.0,
                                comment = cursor.getString(StorageDatabase.Companion.Station.COLUMN_MAP_COMMENT) ?: ""
                            )
                        )
                    }
                    cursor.moveToNext()
                }
            }
        } finally {
            cursor.close()
        }
        stations
    }
}
