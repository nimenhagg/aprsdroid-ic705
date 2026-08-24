package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            color = 0xffffffff.toInt()
            style = Paint.Style.STROKE
            strokeWidth = drawSize.toFloat() / 8.0f
            setShadowLayer(6f, 0f, 0f, 0x80000000.toInt())
        }

        val p = Point()
        val width = c.width
        val height = c.height
        val ss = drawSize / 2
        for (s in stations) {
            proj.toPixels(s.pt, p)
            if (p.x >= -ss && p.y >= -ss && p.x < width + ss && p.y < height + ss) {
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
        val radius = (36 * context.resources.displayMetrics.density).toInt()
        val botleft = proj.fromPixels(p.x - radius, p.y + radius)
        val topright = proj.fromPixels(p.x + radius, p.y - radius)
        val list = stations.filter { it.inArea(botleft, topright) }.map { it.call }
        val mycall = context.prefs.getCallSsid()
        var myLat = 0
        var myLon = 0
        val pos = db.getStaPosition(mycall)
        if (pos.count > 0 && pos.moveToFirst()) {
            val latIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LAT)
            val lonIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LON)
            if (latIdx >= 0 && lonIdx >= 0) {
                myLat = pos.getInt(latIdx)
                myLon = pos.getInt(lonIdx)
            }
        }
        pos.close()

        return when {
            list.isEmpty() -> false
            list.size == 1 -> {
                org.aprsdroid.app.ui.component.StationBottomSheetHelper.show(context, list[0], db, myLat, myLon)
                true
            }
            else -> {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.map_select)
                    .setItems(list.toTypedArray()) { _, item ->
                        org.aprsdroid.app.ui.component.StationBottomSheetHelper.show(context, list[item], db, myLat, myLon)
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
    val allicons: Drawable by lazy { requireNotNull(ContextCompat.getDrawable(this, R.drawable.allicons)) }
    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    val staoverlay: StationOverlay by lazy { StationOverlay(allicons, this, db) }
    val loading: View by lazy { findViewById(R.id.loading) }
    lateinit var toolbar: MaterialToolbar

    val locReceiver = LocationReceiver2(
        { staoverlay.load_stations(it) },
        { staoverlay.replace_stations(it) },
        { /* cancel */ }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mapview)

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.options_map)
        toolbar.inflateMenu(R.menu.options_activities)
        toolbar.inflateMenu(R.menu.options)

        toolbar.setOnMenuItemClickListener { item ->
            handleMenuItem(item)
        }

        // Hide crosshair and coordinate selection buttons when not in coordinate chooser mode
        val isChooser = callingActivity != null
        findViewById<View>(R.id.crosshair)?.visibility = if (isChooser) View.VISIBLE else View.GONE
        findViewById<View>(R.id.accept)?.visibility = if (isChooser) View.VISIBLE else View.GONE

        mapview.setBuiltInZoomControls(false)
        mapview.overlays.add(staoverlay)
        mapview.setTextScale(resources.displayMetrics.density)

        findViewById<View>(R.id.btn_zoom_in)?.setOnClickListener {
            mapview.controller.zoomIn()
        }
        findViewById<View>(R.id.btn_zoom_out)?.setOnClickListener {
            mapview.controller.zoomOut()
        }
        findViewById<View>(R.id.btn_my_location)?.setOnClickListener {
            val mycall = prefs.getCallSsid()
            val pos = db.getStaPosition(mycall)
            if (pos.count > 0 && pos.moveToFirst()) {
                val latIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIdx >= 0 && lonIdx >= 0) {
                    val lat = pos.getInt(latIdx)
                    val lon = pos.getInt(lonIdx)
                    mapview.controller.setCenter(GeoPoint(lat, lon))
                }
            }
            pos.close()
        }

        targetcall = intent.getStringExtra("call") ?: ""
        showObjects = prefs.getBoolean("show_objects", false)

        applyCurrentMapMode()
        mapview.post {
            loadMapPosition()
            mapview.redrawTiles()
        }
        startLoading()
    }

    private fun applyCurrentMapMode() {
        val mode = MapModes.defaultMapMode(this, prefs)
        toolbar.title = getString(R.string.app_map)

        when (mode.tileType) {
            MapTileType.AMAP -> {
                mapview.setMapGenerator(AMapTileDownloader())
            }
            MapTileType.OSM -> {
                mapview.setMapGenerator(OsmTileDownloader())
            }
            MapTileType.CUSTOM -> {
                val customUrl = prefs.getString("map_custom_url", "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}")
                val customSub = prefs.getString("map_custom_subdomains", "1234")
                mapview.setMapGenerator(CustomTileDownloader(customUrl, customSub))
            }
            MapTileType.GOOGLE_NORMAL,
            MapTileType.GOOGLE_HYBRID -> {
                // Google modes use GoogleMapAct; keep a deterministic fallback if routed here.
                mapview.setMapGenerator(AMapTileDownloader())
            }
        }
    }

    private fun loadMapPosition() {
        var lat = prefs.prefs.getFloat("map_lat", 0f)
        var lon = prefs.prefs.getFloat("map_lon", 0f)
        val zoom = prefs.prefs.getFloat("map_zoom", 12f).toInt()

        if (lat == 0f && lon == 0f) {
            val c = db.getStations(null, null, "TS DESC LIMIT 1")
            if (c.moveToFirst()) {
                val latE6 = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LAT)
                val lonE6 = c.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LON)
                lat = (latE6 / 1e6).toFloat()
                lon = (lonE6 / 1e6).toFloat()
            } else {
                lat = 39.9042f
                lon = 116.4074f
            }
            c.close()
        }

        mapview.controller.setCenter(GeoPoint((lat * 1e6).toInt(), (lon * 1e6).toInt()))
        mapview.controller.setZoom(zoom)
    }

    private fun saveMapPosition() {
        val pos = mapview.mapPosition
        if (pos != null && pos.isValid) {
            val gp = pos.mapCenter
            prefs.prefs.edit {
                putFloat("map_lat", (gp.latitudeE6 / 1e6).toFloat())
                putFloat("map_lon", (gp.longitudeE6 / 1e6).toFloat())
                putFloat("map_zoom", pos.zoomLevel.toFloat())
            }
        }
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.amap -> switchMode("amap")
            R.id.osm -> switchMode("osm")
            R.id.custom_tile -> switchMode("custom")
            R.id.normal -> switchGoogleMap("google")
            R.id.satellite -> switchGoogleMap("satellite")
            R.id.objects -> {
                showObjects = prefs.toggleBoolean("show_objects", true)
                item.isChecked = showObjects
                startLoading()
            }
            R.id.hub -> {
                startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                finish()
            }
            R.id.log -> {
                startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                finish()
            }
            R.id.conversations -> {
                startActivity(Intent(this, ConversationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                finish()
            }
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
            }
            R.id.export -> {
                onStartLoading()
                LogExporter(this, StorageDatabase.open(this), null) { onStopLoading() }.execute()
            }
            R.id.clear -> {
                onStartLoading()
                StorageCleaner(this, StorageDatabase.open(this)) { onStopLoading() }.execute()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun switchMode(tag: String) {
        prefs.prefs.edit { putString("mapmode", tag) }
        applyCurrentMapMode()
        mapview.redrawTiles()
    }

    private fun switchGoogleMap(tag: String) {
        prefs.prefs.edit { putString("mapmode", tag) }
        saveMapPosition()
        startActivity(Intent(this, GoogleMapAct::class.java).putExtras(intent))
        finish()
    }

    fun startLoading() {
        locReceiver.startTask(Intent())
        ContextCompat.registerReceiver(this, locReceiver, android.content.IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        saveMapPosition()
        super.onPause()
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
