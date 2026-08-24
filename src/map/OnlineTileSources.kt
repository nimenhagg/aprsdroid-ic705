package org.aprsdroid.app.map

/** Online raster tile templates consumed directly by MapLibre Native. */
object OnlineTileSources {
    const val AMAP_TILE_URL =
        "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}"
    const val AMAP_SUBDOMAINS = "1234"

    const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val OSM_SUBDOMAINS = ""
}
