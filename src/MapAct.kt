package org.aprsdroid.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.graphics.createBitmap
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import org.aprsdroid.app.map.OnlineTileSources
import org.aprsdroid.app.map.TileUrlTemplate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GMS-independent map screen backed by MapLibre Native.
 *
 * The surrounding toolbar and controls intentionally remain shared with the legacy map screen.
 * MapLibre only replaces the rendering surface for AMap, OSM, and custom raster tiles.
 */
class MapAct : MapLoaderBase() {

    override val TAG = "APRSdroid.MapLibre"

    private val mapview: MapView by lazy { findViewById(R.id.mapview) }
    private val loading: View by lazy { findViewById(R.id.loading) }
    private val osmAttribution: TextView by lazy { findViewById(R.id.osm_attribution) }
    private var map: MapLibreMap? = null
    private var pendingStations = arrayListOf<Station>()
    private val activeImageIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mapview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        osmAttribution.setOnClickListener {
            UrlOpener.open(this, getString(R.string.map_osm_copyright_url))
        }

        mapview.onCreate(savedInstanceState)
        mapview.addOnDidFailLoadingMapListener { error ->
            Log.e(TAG, "MapLibre failed to load the map: $error")
            onStopLoading()
        }
        mapview.addOnRenderErrorListener {
            Log.e(TAG, "MapLibre renderer reported an error")
        }
        mapview.getMapAsync { maplibreMap ->
            map = maplibreMap
            maplibreMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isTiltGesturesEnabled = false
            }
            maplibreMap.addOnCameraMoveListener {
                val target = maplibreMap.cameraPosition.target ?: return@addOnCameraMoveListener
                updateCoordinateInfo(target.latitude.toFloat(), target.longitude.toFloat())
            }
            maplibreMap.addOnMapClickListener { point -> onMapTap(point) }

            loadInitialMapPosition()
            val currentMode = MapModes.defaultMapMode(this, prefs)
            if (currentMode.viewClass != MapAct::class.java) {
                saveMapPosition()
                switchMapActivity(currentMode.viewClass)
                return@getMapAsync
            }
            applyRasterStyle(currentMode)
            onStartLoading()
            startLoading()
        }

        findViewById<View>(R.id.btn_zoom_in).setOnClickListener { changeZoom(1) }
        findViewById<View>(R.id.btn_zoom_out).setOnClickListener { changeZoom(-1) }
        findViewById<View>(R.id.btn_my_location).setOnClickListener { moveToMyLocation() }
    }

    override fun onStart() {
        super.onStart()
        mapview.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapview.onResume()
    }

    override fun onPause() {
        saveMapPosition()
        mapview.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapview.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapview.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapview.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapview.onSaveInstanceState(outState)
    }

    override fun onStartLoading() {
        loading.visibility = View.VISIBLE
    }

    override fun onStopLoading() {
        loading.visibility = View.GONE
    }

    override fun reloadMap() {
        onStartLoading()
        locReceiver.startTask(Intent())
    }

    override fun changeZoom(delta: Int) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta.toDouble()))
    }

    override fun loadMapViewPosition(lat: Float, lon: Float, zoom: Float) {
        map?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lat.toDouble(), lon.toDouble()),
                zoom.toDouble()
            )
        )
    }

    override fun setMapMode(mm: MapMode) {
        if (mm.viewClass != MapAct::class.java) {
            saveMapPosition()
            super.setMapMode(mm)
            return
        }
        applyRasterStyle(mm)
    }

    override fun onStationUpdate(sl: ArrayList<Station>) {
        pendingStations = ArrayList(sl)
        map?.style?.let { updateStationLayer(it, pendingStations) }
    }

    private fun applyRasterStyle(mode: MapMode) {
        val maplibreMap = map ?: return
        osmAttribution.visibility = if (mode.tileType == MapTileType.OSM) View.VISIBLE else View.GONE
        val (urlPattern, subdomains, maxZoom, attribution) = when (mode.tileType) {
            MapTileType.AMAP -> TileConfiguration(
                OnlineTileSources.AMAP_TILE_URL,
                OnlineTileSources.AMAP_SUBDOMAINS,
                18f,
                "AutoNavi"
            )
            MapTileType.OSM -> TileConfiguration(
                OnlineTileSources.OSM_TILE_URL,
                OnlineTileSources.OSM_SUBDOMAINS,
                19f,
                getString(R.string.map_osm_attribution)
            )
            MapTileType.CUSTOM -> TileConfiguration(
                prefs.getString("map_custom_url", OnlineTileSources.AMAP_TILE_URL)
                    .trim()
                    .ifEmpty { OnlineTileSources.AMAP_TILE_URL },
                prefs.getString("map_custom_subdomains", OnlineTileSources.AMAP_SUBDOMAINS).trim(),
                19f,
                null
            )
            MapTileType.GOOGLE_NORMAL,
            MapTileType.GOOGLE_HYBRID -> return
        }

        val tileUrls = TileUrlTemplate.expand(urlPattern, subdomains)
        if (tileUrls.isEmpty()) return
        onStartLoading()
        val tileSet = TileSet("2.2.0", *tileUrls).apply {
            setMinZoom(0f)
            setMaxZoom(maxZoom)
            this.attribution = attribution
        }
        val emptyStations = FeatureCollection.fromFeatures(emptyArray())
        val stationIconLayer = SymbolLayer(STATION_ICON_LAYER_ID, STATION_SOURCE_ID).withProperties(
            iconImage(Expression.get(PROPERTY_ICON)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        ).apply {
            setMaxZoom(CALLSIGN_ZOOM)
        }
        val stationLabelLayer = SymbolLayer(STATION_LABEL_LAYER_ID, STATION_SOURCE_ID).withProperties(
            iconImage(Expression.get(PROPERTY_LABELED_ICON)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        ).apply {
            setMinZoom(CALLSIGN_ZOOM)
        }

        activeImageIds.clear()
        maplibreMap.setStyle(
            Style.Builder()
                .withLayer(
                    BackgroundLayer(BACKGROUND_LAYER_ID).withProperties(backgroundColor(Color.WHITE))
                )
                .withSource(RasterSource(RASTER_SOURCE_ID, tileSet, TILE_SIZE))
                .withLayer(RasterLayer(RASTER_LAYER_ID, RASTER_SOURCE_ID))
                .withSource(GeoJsonSource(STATION_SOURCE_ID, emptyStations))
                .withLayer(stationIconLayer)
                .withLayer(stationLabelLayer)
        ) { style ->
            updateStationLayer(style, pendingStations)
        }
    }

    private fun updateStationLayer(style: Style, stations: List<Station>) {
        try {
            activeImageIds.forEach { imageId ->
                if (style.getImage(imageId) != null) style.removeImage(imageId)
            }
            activeImageIds.clear()

            val iconSize = (24f * resources.displayMetrics.density).roundToInt().coerceAtLeast(24)
            val symbolImages = mutableMapOf<String, String>()
            val features = stations.mapIndexed { index, station ->
                val symbol = station.symbol ?: "/$"
                val iconId = symbolImages.getOrPut(symbol) {
                    val id = "aprs-symbol-${encodeImageId(symbol)}"
                    val icon = MapModes.symbol2bitmap(symbol, iconSize).apply {
                        density = resources.displayMetrics.densityDpi
                    }
                    style.addImage(id, icon)
                    activeImageIds.add(id)
                    id
                }
                val labeledIconId = "aprs-station-$index-${encodeImageId(station.call)}"
                style.addImage(labeledIconId, createLabeledStationBitmap(station.call, symbol, iconSize))
                activeImageIds.add(labeledIconId)

                val properties = JsonObject().apply {
                    addProperty(PROPERTY_CALL, station.call)
                    addProperty(PROPERTY_ICON, iconId)
                    addProperty(PROPERTY_LABELED_ICON, labeledIconId)
                }
                Feature.fromGeometry(Point.fromLngLat(station.lon, station.lat), properties)
            }

            style.getSourceAs<GeoJsonSource>(STATION_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
            onStopLoading()
        } catch (e: Exception) {
            Log.e(TAG, "updateStationLayer error", e)
            onStopLoading()
        }
    }

    private fun createLabeledStationBitmap(call: String, symbol: String, iconSize: Int): Bitmap {
        val density = resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = iconSize * 7f / 8f
            typeface = Typeface.MONOSPACE
        }
        val strokePaint = Paint(textPaint).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = max(1f, iconSize / 8f)
        }
        val metrics = textPaint.fontMetrics
        val labelHeight = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        val horizontalPadding = (4f * density).roundToInt()
        val width = max(iconSize, ceil(strokePaint.measureText(call)).toInt() + horizontalPadding * 2)
        val bitmap = createBitmap(width, iconSize + labelHeight * 2).apply {
            this.density = resources.displayMetrics.densityDpi
        }
        val canvas = Canvas(bitmap)
        val symbolBitmap = MapModes.symbol2bitmap(symbol, iconSize)
        canvas.drawBitmap(symbolBitmap, ((width - iconSize) / 2f), labelHeight.toFloat(), null)
        val baseline = labelHeight + iconSize - metrics.ascent
        val centerX = width / 2f
        canvas.drawText(call, centerX, baseline, strokePaint)
        canvas.drawText(call, centerX, baseline, textPaint)
        return bitmap
    }

    private fun onMapTap(point: LatLng): Boolean {
        if (isCoordinateChooser) return false
        val maplibreMap = map ?: return false
        val screenPoint = maplibreMap.projection.toScreenLocation(point)
        val radius = 36f * resources.displayMetrics.density
        val area = RectF(
            screenPoint.x - radius,
            screenPoint.y - radius,
            screenPoint.x + radius,
            screenPoint.y + radius
        )
        val calls = maplibreMap.queryRenderedFeatures(
            area,
            STATION_ICON_LAYER_ID,
            STATION_LABEL_LAYER_ID
        ).mapNotNull { feature ->
            if (feature.hasProperty(PROPERTY_CALL)) feature.getStringProperty(PROPERTY_CALL) else null
        }.distinct()

        return when {
            calls.isEmpty() -> false
            calls.size == 1 -> {
                showStationDetails(calls.first())
                true
            }
            else -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.map_select)
                    .setItems(calls.toTypedArray()) { _, index -> showStationDetails(calls[index]) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
    }

    private fun showStationDetails(call: String) {
        var myLat = 0
        var myLon = 0
        val position = db.getStaPosition(prefs.getCallSsid())
        if (position.count > 0 && position.moveToFirst()) {
            val latIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LAT)
            val lonIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LON)
            if (latIndex >= 0 && lonIndex >= 0) {
                myLat = position.getInt(latIndex)
                myLon = position.getInt(lonIndex)
            }
        }
        position.close()
        org.aprsdroid.app.ui.component.StationBottomSheetHelper.show(this, call, db, myLat, myLon)
    }

    private fun moveToMyLocation() {
        val position = db.getStaPosition(prefs.getCallSsid())
        if (position.count > 0 && position.moveToFirst()) {
            val latIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LAT)
            val lonIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LON)
            if (latIndex >= 0 && lonIndex >= 0) {
                val latitude = position.getInt(latIndex) / 1_000_000.0
                val longitude = position.getInt(lonIndex) / 1_000_000.0
                map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
            }
        }
        position.close()
    }

    private fun loadInitialMapPosition() {
        var latitude = prefs.prefs.getFloat("map_lat", 0f)
        var longitude = prefs.prefs.getFloat("map_lon", 0f)
        val zoom = prefs.prefs.getFloat("map_zoom", 12f)
        if (latitude == 0f && longitude == 0f) {
            try {
                val latest = db.getStations(null, null, "1", "TS DESC")
                if (latest.moveToFirst()) {
                    latitude = latest.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LAT) / 1_000_000f
                    longitude = latest.getInt(StorageDatabase.Companion.Station.COLUMN_MAP_LON) / 1_000_000f
                }
                latest.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial map position: $e", e)
            }
            if (latitude == 0f && longitude == 0f) {
                latitude = 39.9042f
                longitude = 116.4074f
            }
        }
        loadMapViewPosition(latitude, longitude, zoom)
    }

    private fun saveMapPosition() {
        val camera = map?.cameraPosition ?: return
        val target = camera.target ?: return
        saveMapViewPosition(
            target.latitude.toFloat(),
            target.longitude.toFloat(),
            camera.zoom.toFloat()
        )
    }

    private fun encodeImageId(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private data class TileConfiguration(
        val urlPattern: String,
        val subdomains: String,
        val maxZoom: Float,
        val attribution: String?
    )

    private companion object {
        const val TILE_SIZE = 256
        const val CALLSIGN_ZOOM = 10f
        const val BACKGROUND_LAYER_ID = "aprs-background"
        const val RASTER_SOURCE_ID = "aprs-raster-source"
        const val RASTER_LAYER_ID = "aprs-raster-layer"
        const val STATION_SOURCE_ID = "aprs-stations-source"
        const val STATION_ICON_LAYER_ID = "aprs-stations-icons"
        const val STATION_LABEL_LAYER_ID = "aprs-stations-labels"
        const val PROPERTY_CALL = "call"
        const val PROPERTY_ICON = "icon"
        const val PROPERTY_LABELED_ICON = "labeled-icon"
    }
}
