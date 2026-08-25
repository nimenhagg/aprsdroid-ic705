package org.aprsdroid.app

import android.app.Activity
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
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity
import org.aprsdroid.app.map.OnlineTileSources
import org.aprsdroid.app.map.TileUrlTemplate
import org.aprsdroid.app.ui.screen.MapScreen
import org.aprsdroid.app.ui.theme.AprsTheme
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
 * GMS-independent map screen backed by MapLibre Native with Compose Material 3 UI overlay.
 */
class MapAct : MapLoaderBase() {

    override val TAG = "APRSdroid.MapLibre"

    private lateinit var mapview: MapView
    private var map: MapLibreMap? = null
    private var pendingStations = arrayListOf<Station>()
    private val activeImageIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mapview = MapView(this).apply {
            onCreate(savedInstanceState)
            addOnDidFailLoadingMapListener { error ->
                Log.e(TAG, "MapLibre failed to load the map: $error")
                onStopLoading()
            }
            addOnRenderErrorListener {
                Log.e(TAG, "MapLibre renderer reported an error")
            }
            getMapAsync { maplibreMap ->
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
                val currentMode = MapModes.defaultMapMode(this@MapAct, prefs)
                currentMapModeState.value = currentMode
                if (currentMode.viewClass != MapAct::class.java) {
                    saveMapPosition()
                    switchMapActivity(currentMode.viewClass)
                    return@getMapAsync
                }
                applyRasterStyle(currentMode)
                onStartLoading()
                startLoading()
            }
        }

        setContent {
            AprsTheme {
                MapScreen(
                    title = if (targetcall.isNotEmpty()) getString(R.string.map_track_call, targetcall) else getMapTitlePrefix(),
                    isLoading = isLoadingState.value,
                    showOsmAttribution = showOsmAttributionState.value,
                    onOsmAttributionClick = {
                        UrlOpener.open(this, getString(R.string.map_osm_copyright_url))
                    },
                    isCoordinateChooser = isCoordinateChooser,
                    coordinateInfo = coordinateInfoState.value,
                    onAcceptCoordinate = {
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    availableMapModes = MapModes.all_mapmodes.filter { it.isAvailable(this) },
                    currentMapMode = currentMapModeState.value,
                    showObjects = showObjectsState.value,
                    onToggleShowObjects = {
                        val newState = prefs.toggleBoolean("show_objects", true)
                        showObjectsState.value = newState
                        showObjects = newState
                        reloadMap()
                    },
                    onSelectMapMode = { mode ->
                        MapModes.setDefault(prefs, mode.tag)
                        currentMapModeState.value = mode
                        setMapMode(mode)
                    },
                    onMyLocationClick = { moveToMyLocation() },
                    onZoomIn = { changeZoom(1) },
                    onZoomOut = { changeZoom(-1) },
                    onBackClick = { finish() },
                    onOpenLogs = {
                        startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, PrefsAct::class.java))
                    },
                    onClearLogs = {
                        onStartLoading()
                        StorageCleaner(this, db) { onStopLoading() }.execute()
                    },
                    onOpenAbout = {
                        AboutDialog(this).show()
                    },
                    mapContent = {
                        AndroidView(
                            factory = { mapview },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapview.isInitialized) mapview.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::mapview.isInitialized) mapview.onResume()
    }

    override fun onPause() {
        saveMapPosition()
        if (::mapview.isInitialized) mapview.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::mapview.isInitialized) mapview.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::mapview.isInitialized) mapview.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapview.isInitialized) mapview.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapview.isInitialized) mapview.onSaveInstanceState(outState)
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
        showOsmAttributionState.value = (mode.tileType == MapTileType.OSM)
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
                        this.density = resources.displayMetrics.densityDpi
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
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = ceil(textPaint.measureText(call)).toInt()
        val textHeight = ceil(textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).toInt()
        val textBaseline = -textPaint.fontMetrics.ascent
        val padding = (3f * density).roundToInt()
        val cornerRadius = 3f * density
        val spacing = (2f * density).roundToInt()

        val symbolBitmap = MapModes.symbol2bitmap(symbol, iconSize)
        val bitmapWidth = max(iconSize, textWidth + padding * 2)
        val bitmapHeight = iconSize + spacing + textHeight + padding * 2

        val bitmap = createBitmap(bitmapWidth, bitmapHeight).apply {
            this.density = resources.displayMetrics.densityDpi
        }
        val canvas = Canvas(bitmap)

        val symbolLeft = ((bitmapWidth - iconSize) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(symbolBitmap, symbolLeft, 0f, null)

        val backgroundRect = RectF(
            ((bitmapWidth - (textWidth + padding * 2)) / 2f).coerceAtLeast(0f),
            iconSize.toFloat() + spacing,
            ((bitmapWidth + (textWidth + padding * 2)) / 2f).coerceAtMost(bitmapWidth.toFloat()),
            bitmapHeight.toFloat()
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)

        val textLeft = backgroundRect.left + padding
        val textTop = backgroundRect.top + padding + textBaseline
        canvas.drawText(call, textLeft, textTop, textPaint)
        return bitmap
    }

    private fun onMapTap(point: LatLng): Boolean {
        val maplibreMap = map ?: return false
        val screenPoint = maplibreMap.projection.toScreenLocation(point)
        val touchSlop = (24f * resources.displayMetrics.density).roundToInt()
        val rect = RectF(
            screenPoint.x - touchSlop,
            screenPoint.y - touchSlop,
            screenPoint.x + touchSlop,
            screenPoint.y + touchSlop
        )
        val features = maplibreMap.queryRenderedFeatures(rect, STATION_ICON_LAYER_ID, STATION_LABEL_LAYER_ID)
        val calls = features.mapNotNull { it.getStringProperty(PROPERTY_CALL) }.distinct()
        return when (calls.size) {
            0 -> false
            1 -> {
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
                    val latIdx = latest.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                    val lonIdx = latest.getColumnIndex(StorageDatabase.Companion.Station.LON)
                    if (latIdx >= 0 && lonIdx >= 0) {
                        latitude = latest.getInt(latIdx) / 1_000_000f
                        longitude = latest.getInt(lonIdx) / 1_000_000f
                    }
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
