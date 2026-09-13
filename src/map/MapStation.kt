package org.aprsdroid.app.map

/** Immutable map input: database row identity and non-rendered APRS fields are irrelevant. */
data class MapStation(
    val call: String,
    val symbol: String,
    val lat: Double,
    val lon: Double,
    val comment: String
) {
    val feature: StationFeature get() = StationFeature(call, symbol, lat, lon)
}

data class StationFeature(val call: String, val symbol: String, val lat: Double, val lon: Double)

/** A stable image key must not depend on query order or position. */
data class StationImage(val call: String?, val symbol: String)

internal fun stationImages(stations: List<StationFeature>): Set<StationImage> = buildSet {
    stations.forEach { station ->
        add(StationImage(null, station.symbol))
        add(StationImage(station.call, station.symbol))
    }
}
