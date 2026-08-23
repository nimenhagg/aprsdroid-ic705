package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.view.MenuItem
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.GoogleMap

enum class MapTileType {
    AMAP,
    OSM,
    GOOGLE_NORMAL,
    GOOGLE_HYBRID,
    CUSTOM,
}

open class MapMode(
    val tag: String,
    val menu_id: Int,
    val title: String?,
    val viewClass: Class<*>,
    val tileType: MapTileType = MapTileType.AMAP
) {
    open fun isAvailable(ctx: Context): Boolean = true
}

class GoogleMapMode(
    tag: String,
    menu_id: Int,
    title: String?,
    val mapType: Int,
    tileType: MapTileType
) : MapMode(tag, menu_id, title, GoogleMapAct::class.java, tileType) {
    override fun isAvailable(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

class TileMapMode(
    tag: String,
    menu_id: Int,
    title: String?,
    tileType: MapTileType
) : MapMode(tag, menu_id, title, MapAct::class.java, tileType)

class MapsforgeOnlineMode(
    tag: String,
    menu_id: Int,
    title: String?,
    val foo: String
) : MapMode(tag, menu_id, title, MapAct::class.java, MapTileType.OSM)

class MapsforgeFileMode(
    tag: String,
    menu_id: Int,
    title: String?,
    val file: String
) : MapMode(tag, menu_id, title, MapAct::class.java, MapTileType.OSM)

object MapModes {
    val all_mapmodes = mutableListOf<MapMode>()

    private var alliconsbitmap: Bitmap? = null
    private var symbolSize = 0
    private val zerorect = Rect(0, 0, 0, 0)

    fun initialize(ctx: Context) {
        if (alliconsbitmap == null) {
            val bmp = BitmapFactory.decodeResource(ctx.resources, R.drawable.allicons)
            alliconsbitmap = bmp
            symbolSize = bmp.width / 16
        }
        if (all_mapmodes.isNotEmpty()) return

        all_mapmodes.add(TileMapMode("amap", R.id.amap, ctx.getString(R.string.map_amap), MapTileType.AMAP))
        all_mapmodes.add(TileMapMode("osm", R.id.osm, ctx.getString(R.string.map_osm), MapTileType.OSM))
        all_mapmodes.add(GoogleMapMode("google", R.id.normal, ctx.getString(R.string.map_google), GoogleMap.MAP_TYPE_NORMAL, MapTileType.GOOGLE_NORMAL))
        all_mapmodes.add(GoogleMapMode("satellite", R.id.satellite, ctx.getString(R.string.map_satellite), GoogleMap.MAP_TYPE_HYBRID, MapTileType.GOOGLE_HYBRID))
        all_mapmodes.add(TileMapMode("custom", R.id.custom_tile, ctx.getString(R.string.map_custom_tile), MapTileType.CUSTOM))
    }

    fun reloadOfflineMaps(ctx: Context) {}

    fun defaultMapMode(ctx: Context, prefs: PrefsWrapper): MapMode {
        initialize(ctx)
        val tag = prefs.getString("mapmode", "amap")
        var default: MapMode? = null
        for (mode in all_mapmodes) {
            if (default == null && mode.isAvailable(ctx)) default = mode
            if (mode.tag == tag && mode.isAvailable(ctx)) return mode
        }
        return default ?: all_mapmodes[0]
    }

    fun startMap(ctx: Context, prefs: PrefsWrapper, targetcall: String?) {
        val mm = defaultMapMode(ctx, prefs)
        val intent = Intent(ctx, mm.viewClass)
        if (!targetcall.isNullOrEmpty()) {
            intent.data = Uri.parse(targetcall)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        ctx.startActivity(intent)
    }

    fun setDefault(prefs: PrefsWrapper, tag: String) {
        prefs.set("mapmode", tag)
    }

    fun fromMenuItem(mi: MenuItem): MapMode? {
        for (mode in all_mapmodes) {
            if (mode.menu_id == mi.itemId) return mode
        }
        return null
    }

    fun symbol2rect(index: Int, page: Int): Rect {
        if (index < 0 || index >= 6 * 16) return zerorect
        val y = (index / 16 + 6 * page) * symbolSize
        val x = (index % 16) * symbolSize
        return Rect(x, y, x + symbolSize, y + symbolSize)
    }

    fun symbol2rect(symbol: String): Rect {
        val page = if (symbol.isNotEmpty() && symbol[0] == '/') 0 else 1
        val index = if (symbol.length > 1) symbol[1].code - 33 else 0
        return symbol2rect(index, page)
    }

    fun symbolIsOverlayed(symbol: String): Boolean {
        return symbol.isNotEmpty() && symbol[0] != '/' && symbol[0] != '\\'
    }

    fun symbol2bitmap(symbol: String, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.TRANSPARENT)
        val c = Canvas(b)
        val rect = Rect(0, 0, size, size)
        alliconsbitmap?.let { bmp ->
            c.drawBitmap(bmp, symbol2rect(symbol), rect, null)
            if (symbolIsOverlayed(symbol)) {
                c.drawBitmap(bmp, symbol2rect(symbol[0].code - 33, 2), rect, null)
            }
        }
        return b
    }
}
