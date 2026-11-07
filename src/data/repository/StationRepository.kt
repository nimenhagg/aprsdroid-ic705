package org.aprsdroid.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.model.StationItem

data class HubData(
    val myLat: Int,
    val myLon: Int,
    val stations: List<StationItem>,
)

data class StationDetailsData(
    val myLat: Int,
    val myLon: Int,
    val stationItem: StationItem?,
    val ssidList: List<StationItem>,
    val postList: List<LogPostItem>,
)

interface StationDetailsSource {
    suspend fun getStationDetails(
        targetCall: String,
        myCall: String,
        postLimit: String = "300",
    ): StationDetailsData
}

interface HubStationSource {
    suspend fun getHubData(
        myCall: String,
        maxAgeMs: Long,
        limit: String = "300",
        includeStations: Boolean = true,
        searchQuery: String = "",
    ): HubData
}

class StationRepository(private val db: StorageDatabase) : StationDetailsSource, HubStationSource {

    override suspend fun getHubData(
        myCall: String,
        maxAgeMs: Long,
        limit: String,
        includeStations: Boolean,
        searchQuery: String,
    ): HubData = withContext(Dispatchers.IO) {
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
        } else {
            emptyList()
        }
        HubData(myLat, myLon, items)
    }

    override suspend fun getStationDetails(
        targetCall: String,
        myCall: String,
        postLimit: String,
    ): StationDetailsData = withContext(Dispatchers.IO) {
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

        val ssidCursor = db.getAllSsids(targetCall)
        val ssidItems = StationItem.fromCursor(ssidCursor)

        val postCursor = db.getStaPosts(targetCall, postLimit)
        val postItems = LogPostItem.fromCursor(postCursor)

        val targetItem = ssidItems.find { it.call.equals(targetCall, ignoreCase = true) }
            ?: ssidItems.firstOrNull()

        StationDetailsData(
            myLat = myLat,
            myLon = myLon,
            stationItem = targetItem,
            ssidList = ssidItems,
            postList = postItems,
        )
    }
}
