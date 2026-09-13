package org.aprsdroid.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.StationItem

data class HubData(
    val myLat: Int,
    val myLon: Int,
    val stations: List<StationItem>
)

class StationRepository(private val db: StorageDatabase) {

    suspend fun getHubData(myCall: String, maxAgeMs: Long, limit: String = "300", includeStations: Boolean = true, searchQuery: String = ""): HubData = withContext(Dispatchers.IO) {
        var myLat = 0
        var myLon = 0
        val posCursor = db.getStaPosition(myCall)
        if (posCursor.count > 0 && posCursor.moveToFirst()) {
            val latIdx = posCursor.getColumnIndex(StorageDatabase.Companion.Station.LAT)
            val lonIdx = posCursor.getColumnIndex(StorageDatabase.Companion.Station.LON)
            if (latIdx >= 0) myLat = posCursor.getInt(latIdx)
            if (lonIdx >= 0) myLon = posCursor.getInt(lonIdx)
        }
        posCursor.close()

        val items = if (includeStations) {
            val cursor = db.getNeighbors(myCall, myLat, myLon, System.currentTimeMillis() - maxAgeMs, limit, searchQuery)
            StationItem.fromCursor(cursor)
        } else emptyList()
        HubData(myLat, myLon, items)
    }
}
