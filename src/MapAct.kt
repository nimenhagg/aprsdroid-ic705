package org.aprsdroid.app

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import org.mapsforge.v3.android.maps.MapActivity
import org.mapsforge.v3.android.maps.MapView
import org.mapsforge.v3.android.maps.Projection
import org.mapsforge.v3.android.maps.overlay.ItemizedOverlay
import org.mapsforge.v3.android.maps.overlay.OverlayItem
import org.mapsforge.v3.core.GeoPoint
import java.util.ArrayList

class OSMStation(
    val movelog: List<GeoPoint>?,
    val pt: GeoPoint,
    val call: String,
    val origin: String?,
    val symbol: String
) : OverlayItem(pt, call, origin) {

    fun inArea(bl: GeoPoint, tr: GeoPoint): Boolean {
        val latOk = bl.latitudeE6 <= pt.latitudeE6 && pt.latitudeE6 <= tr.latitudeE6
        val lonOk = if (bl.longitudeE6 <= tr.longitudeE6) {
            bl.longitudeE6 <= pt.longitudeE6 && pt.longitudeE6 <= tr.longitudeE6
        } else {
            bl.longitudeE6 <= pt.longitudeE6 || pt.longitudeE6 <= tr.longitudeE6
        }
        return latOk && lonOk
    }
}

class StationOverlay(
    icons: Drawable,
    val context: MapAct,
    val db: StorageDatabase
) : ItemizedOverlay<OSMStation>(icons) {

    companion object {
        const val TAG = "APRSdroid.StaOverlay"
    }

    var stations: ArrayList<OSMStation> = ArrayList()
    val iconbitmap = (icons as BitmapDrawable).bitmap
    val symbolSize = iconbitmap.width / 16
    val drawSize = (context.resources.displayMetrics.density * 24).toInt()

    init {
        icons.setBounds(0, 0, symbolSize, symbolSize)
        populate()
    }

    override fun size(): Int = stations.size
    override fun createItem(idx: Int): OSMStation = stations[idx]

    fun symbol2rect(index: Int, page: Int): Rect {
        if (index < 0 || index >= 6 * 16) return Rect(0, 0, symbolSize, symbolSize)
        val altOffset = page * symbolSize * 6
        val y = (index / 16) * symbolSize + altOffset
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

    override fun drawOverlayBitmap(c: Canvas, dp: Point?, proj: Projection, zoom: Byte) {
        if (!context.mapview.mapPosition.isValid) return
        val fontSize = drawSize * 7 / 8
        val textPaint = Paint().apply {
            color = 0xff000000.toInt()
            textAlign = Paint.Align.CENTER
            textSize = fontSize.toFloat()
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val strokePaint = Paint(textPaint).apply {
            color = 0xffc8ffc8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = drawSize.toFloat() / 12.0f
            setShadowLayer(10f, 0f, 0f, 0x80c8ffc8.toInt())
        }

        val p = Point()
        val width = c.width
        val height = c.height
        val ss = drawSize / 2
        for (s in stations) {
            proj.toPixels(s.pt, p)
            if (p.x >= 0 && p.y >= 0 && p.x < width && p.y < height) {
                val srcRect = symbol2rect(s.symbol)
                val destRect = Rect(p.x - ss, p.y - ss, p.x + ss, p.y + ss)
                if (zoom >= 10) {
                    c.drawText(s.call, p.x.toFloat(), (p.y + ss + fontSize).toFloat(), strokePaint)
                    c.drawText(s.call, p.x.toFloat(), (p.y + ss + fontSize).toFloat(), textPaint)
                }
                c.drawBitmap(iconbitmap, srcRect, destRect, null)
                if (symbolIsOverlayed(s.symbol)) {
                    c.drawBitmap(iconbitmap, symbol2rect(s.symbol[0].code - 33, 2), destRect, null)
                }
            }
        }
    }

    override fun onTap(gp: GeoPoint, mv: MapView): Boolean {
        val proj = mv.projection
        val p = proj.toPixels(gp, null)
        val botleft = proj.fromPixels(p.x - 16, p.y + 16)
        val topright = proj.fromPixels(p.x + 16, p.y - 16)
        val list = stations.filter { it.inArea(botleft, topright) }.map { it.call }
        return when {
            list.isEmpty() -> false
            list.size == 1 -> {
                UIHelper.openCallsignDetails(context, list[0])
                true
            }
            else -> {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.map_select)
                    .setItems(list.toTypedArray()) { _, item ->
                        UIHelper.openCallsignDetails(context, list[item])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
    }

    fun load_stations(i: Intent): ArrayList<OSMStation> {
        val s = ArrayList<OSMStation>()
        val ageTs = (System.currentTimeMillis() - context.prefs.getShowAge()).toString()
        val filter = if (context.showObjects) "TS > ? OR CALL=?" else "(ORIGIN IS NULL AND TS > ?) OR CALL=?"
        val c = db.getStations(filter, arrayOf(ageTs, context.targetcall), null)
        c.moveToFirst()
        while (!c.isAfterLast) {
            val call = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_CALL)
            val lat = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LAT)
            val lon = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LON)
            val symbol = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_SYMBOL)
            val origin = c.getString(StorageDatabase.Companion.Station.COLUMN_MAP_ORIGIN)
            val p = GeoPoint(lat, lon)
            s.add(OSMStation(null, p, call, origin, symbol ?: "/$"))
            c.moveToNext()
        }
        c.close()
        return s
    }

    fun replace_stations(s: ArrayList<OSMStation>) {
        stations = s
        populate()
        context.onStopLoading()
    }
}

class MapAct : MapActivity(), LoadingIndicator {

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    var targetcall: String = ""
    var showObjects: Boolean = false

    val mapview: MapView by lazy { findViewById(R.id.mapview) }
    val allicons: Drawable by lazy { resources.getDrawable(R.drawable.allicons) }
    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    val staoverlay: StationOverlay by lazy { StationOverlay(allicons, this, db) }
    val loading: View by lazy { findViewById(R.id.loading) }

    val locReceiver = LocationReceiver2(
        { staoverlay.load_stations(it) },
        { staoverlay.replace_stations(it) },
        { /* cancel */ }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mapview)
        mapview.setBuiltInZoomControls(true)
        mapview.overlays.add(staoverlay)
        mapview.setTextScale(resources.displayMetrics.density)
        startLoading()
    }

    fun startLoading() {
        locReceiver.startTask(Intent())
        ContextCompat.registerReceiver(this, locReceiver, android.content.IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        try { unregisterReceiver(locReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onStartLoading() {
        loading.visibility = View.VISIBLE
    }

    override fun onStopLoading() {
        loading.visibility = View.GONE
    }
}
