package org.aprsdroid.app.ui.screen

import android.content.Context
import android.location.Location
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory as GoogleCameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GoogleMapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import org.aprsdroid.app.MapMode
import org.aprsdroid.app.MapModes
import org.aprsdroid.app.MapTileType
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R
import org.aprsdroid.app.map.MapStation
import org.aprsdroid.app.map.StationFeature
import org.aprsdroid.app.map.StationImage
import org.aprsdroid.app.map.stationImages
import org.aprsdroid.app.UrlOpener
import org.aprsdroid.app.map.OnlineTileSources
import org.aprsdroid.app.map.TileUrlTemplate
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.maplibre.android.camera.CameraUpdateFactory as MapLibreCameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView as MapLibreMapView
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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

@Composable
fun EmbeddedMapScreen(
    prefs: PrefsWrapper,
    stations: List<MapStation>,
    dataLoading: Boolean,
    showObjects: Boolean,
    myLat: Int,
    myLon: Int,
    currentLocation: Location?,
    onShowObjectsChanged: (Boolean) -> Unit,
    onStationClick: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPackets: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearLogs: () -> Unit,
    onRequestCurrentLocation: () -> Unit
) {
    val context = LocalContext.current
    MapModes.initialize(context)

    var currentMode by remember { mutableStateOf(MapModes.defaultMapMode(context, prefs)) }
    val availableMapModes = remember(context) { MapModes.all_mapmodes.filter { it.isAvailable(context) } }
    var rendererLoading by remember(currentMode.tag) { mutableStateOf(true) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val actions = remember { EmbeddedMapActions() }

    MapScreen(
        title = currentMode.title ?: context.getString(R.string.app_map),
        isLoading = dataLoading || rendererLoading,
        showOsmAttribution = currentMode.tileType == MapTileType.OSM,
        onOsmAttributionClick = {
            UrlOpener.open(context, context.getString(R.string.map_osm_copyright_url))
        },
        availableMapModes = availableMapModes,
        currentMapMode = currentMode,
        showObjects = showObjects,
        onToggleShowObjects = {
            val newValue = prefs.toggleBoolean("show_objects", true)
            onShowObjectsChanged(newValue)
        },
        onSelectMapMode = { mode ->
            if (mode.tag != currentMode.tag) {
                actions.savePosition()
                MapModes.setDefault(prefs, mode.tag)
                rendererLoading = true
                currentMode = mode
            }
        },
        onMyLocationClick = onRequestCurrentLocation,
        onZoomIn = { actions.zoomIn() },
        onZoomOut = { actions.zoomOut() },
        onBackClick = onBack,
        onOpenLogs = onOpenPackets,
        onOpenSettings = onOpenSettings,
        onClearLogs = onClearLogs,
        onOpenAbout = { showAboutDialog = true },
        mapContent = {
            when (currentMode.tileType) {
                MapTileType.AMAP,
                MapTileType.OSM,
                MapTileType.CUSTOM -> MapLibreEmbeddedRenderer(
                    mode = currentMode,
                    prefs = prefs,
                    stations = stations,
                    myLat = myLat,
                    myLon = myLon,
                    currentLocation = currentLocation,
                    actions = actions,
                    onStationClick = onStationClick,
                    onLoadingChanged = { rendererLoading = it }
                )

                MapTileType.GOOGLE_NORMAL,
                MapTileType.GOOGLE_HYBRID -> GoogleEmbeddedRenderer(
                    mode = currentMode,
                    prefs = prefs,
                    stations = stations,
                    myLat = myLat,
                    myLon = myLon,
                    currentLocation = currentLocation,
                    actions = actions,
                    onStationClick = onStationClick,
                    onLoadingChanged = { rendererLoading = it }
                )
            }
        }
    )

    if (showAboutDialog) {
        AboutDialogContent(
            onDismiss = { showAboutDialog = false },
            onOpenGithub = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        "https://github.com/nimenhagg/aprsdroid-ic705".toUri()
                    )
                )
            }
        )
    }
}

private class EmbeddedMapActions {
    var zoomIn: () -> Unit = {}
    var zoomOut: () -> Unit = {}
    var savePosition: () -> Unit = {}
}

@Composable
private fun MapLibreEmbeddedRenderer(
    mode: MapMode,
    prefs: PrefsWrapper,
    stations: List<MapStation>,
    myLat: Int,
    myLon: Int,
    currentLocation: Location?,
    actions: EmbeddedMapActions,
    onStationClick: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val latestStationClick = rememberUpdatedState(onStationClick)
    val latestStations = rememberUpdatedState(stations)
    val mapView = remember(context) {
        MapLibreMapView(context).apply { onCreate(null) }
    }
    BindMapLifecycle(
        onStart = mapView::onStart,
        onResume = mapView::onResume,
        onPause = mapView::onPause,
        onStop = mapView::onStop,
        onDestroy = mapView::onDestroy
    )

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }
    var readyStyle by remember { mutableStateOf<Style?>(null) }
    val stationCache = remember(readyStyle) { MapLibreStationCache() }

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { error ->
            Log.e("APRSdroid.EmbeddedMap", "MapLibre failed to load map: $error")
            onLoadingChanged(false)
        }
        mapView.addOnRenderErrorListener {
            Log.e("APRSdroid.EmbeddedMap", "MapLibre renderer error")
        }
        mapView.getMapAsync { mapLibreMap ->
            mapLibreMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isTiltGesturesEnabled = false
            }
            mapLibreMap.addOnMapClickListener { point ->
                handleMapLibreTap(context, mapLibreMap, point, latestStationClick.value)
            }
            map = mapLibreMap
        }
    }

    LaunchedEffect(map, stations) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!cameraInitialized) {
            val initial = initialPosition(prefs, stations)
            mapLibreMap.moveCamera(
                MapLibreCameraUpdateFactory.newLatLngZoom(
                    MapLibreLatLng(initial.lat, initial.lon),
                    initial.zoom.toDouble()
                )
            )
            cameraInitialized = true
        }
    }

    DisposableEffect(map, mode.tag) {
        var active = true
        readyStyle = null
        map?.let { mapLibreMap ->
            onLoadingChanged(true)
            applyMapLibreRasterStyle(context, mapLibreMap, mode, prefs) { style ->
                if (active) {
                    readyStyle = style
                    onLoadingChanged(false)
                }
            }
        }
        onDispose { active = false }
    }

    LaunchedEffect(readyStyle) {
        val style = readyStyle ?: return@LaunchedEffect
        snapshotFlow { latestStations.value }.conflate().collect { snapshot ->
            try {
                updateMapLibreStations(context, style, snapshot, stationCache)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("APRSdroid.EmbeddedMap", "Station layer update failed", error)
            }
        }
    }

    actions.zoomIn = { map?.animateCamera(MapLibreCameraUpdateFactory.zoomBy(1.0)) }
    actions.zoomOut = { map?.animateCamera(MapLibreCameraUpdateFactory.zoomBy(-1.0)) }
    LaunchedEffect(map, currentLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        map?.animateCamera(
            MapLibreCameraUpdateFactory.newLatLng(
                MapLibreLatLng(location.latitude, location.longitude)
            )
        )
    }

    actions.savePosition = {
        val camera = map?.cameraPosition
        val target = camera?.target
        if (camera != null && target != null) {
            savePosition(prefs, target.latitude, target.longitude, camera.zoom.toFloat())
        }
    }

    DisposableEffect(mapView) {
        onDispose { actions.savePosition() }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun GoogleEmbeddedRenderer(
    mode: MapMode,
    prefs: PrefsWrapper,
    stations: List<MapStation>,
    myLat: Int,
    myLon: Int,
    currentLocation: Location?,
    actions: EmbeddedMapActions,
    onStationClick: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val latestStationClick = rememberUpdatedState(onStationClick)
    val mapView = remember(context) {
        GoogleMapView(context).apply { onCreate(null) }
    }
    BindMapLifecycle(
        onStart = mapView::onStart,
        onResume = mapView::onResume,
        onPause = mapView::onPause,
        onStop = mapView::onStop,
        onDestroy = mapView::onDestroy
    )

    var map by remember { mutableStateOf<GoogleMap?>(null) }
    val markers = remember(map) { mutableMapOf<String, Marker>() }
    val renderedStations = remember(map) { mutableMapOf<String, MapStation>() }
    val symbolIcons = remember(map) { mutableMapOf<String, BitmapDescriptor>() }
    var cameraInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { googleMap ->
            googleMap.uiSettings.isCompassEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.setOnMarkerClickListener { marker ->
                val call = marker.tag as? String ?: marker.title
                if (!call.isNullOrBlank()) {
                    latestStationClick.value(call)
                    true
                } else {
                    false
                }
            }
            googleMap.setOnInfoWindowClickListener { marker ->
                val call = marker.tag as? String ?: marker.title
                if (!call.isNullOrBlank()) latestStationClick.value(call)
            }
            googleMap.setOnCameraIdleListener {
                val camera = googleMap.cameraPosition
                savePosition(
                    prefs,
                    camera.target.latitude,
                    camera.target.longitude,
                    camera.zoom
                )
            }
            map = googleMap
        }
    }

    LaunchedEffect(map, stations) {
        val googleMap = map ?: return@LaunchedEffect
        if (!cameraInitialized) {
            val initial = initialPosition(prefs, stations)
            googleMap.moveCamera(
                GoogleCameraUpdateFactory.newLatLngZoom(
                    GoogleLatLng(initial.lat, initial.lon),
                    initial.zoom
                )
            )
            cameraInitialized = true
        }
    }

    LaunchedEffect(map, mode.tag) {
        val googleMap = map ?: return@LaunchedEffect
        onLoadingChanged(true)
        googleMap.mapType = when (mode.tileType) {
            MapTileType.GOOGLE_HYBRID -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
        onLoadingChanged(false)
    }

    LaunchedEffect(map, stations) {
        val googleMap = map ?: return@LaunchedEffect
        val calls = stations.mapTo(hashSetOf()) { it.call }
        (markers.keys - calls).forEach { call ->
            markers.remove(call)?.remove()
            renderedStations.remove(call)
        }
        stations.forEachIndexed { index, station ->
            val previous = renderedStations[station.call]
            if (station != previous) {
                val marker = markers[station.call]
                if (marker == null) {
                    val icon = symbolIcons.getOrPut(station.symbol) {
                        BitmapDescriptorFactory.fromBitmap(MapModes.symbol2bitmap(station.symbol, 48))
                    }
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(GoogleLatLng(station.lat, station.lon))
                            .title(station.call)
                            .snippet(station.comment)
                            .icon(icon)
                            .anchor(0.5f, 0.5f)
                    )?.let { added ->
                        added.tag = station.call
                        markers[station.call] = added
                        renderedStations[station.call] = station
                    }
                } else {
                    if (previous?.lat != station.lat || previous?.lon != station.lon) {
                        marker.position = GoogleLatLng(station.lat, station.lon)
                    }
                    if (previous?.symbol != station.symbol) {
                        marker.setIcon(symbolIcons.getOrPut(station.symbol) {
                            BitmapDescriptorFactory.fromBitmap(MapModes.symbol2bitmap(station.symbol, 48))
                        })
                    }
                    if (previous?.comment != station.comment) marker.snippet = station.comment
                    renderedStations[station.call] = station
                }
            }
            if (index % 32 == 31) yield()
        }
        symbolIcons.keys.retainAll(stations.mapTo(hashSetOf()) { it.symbol })
    }

    actions.zoomIn = { map?.animateCamera(GoogleCameraUpdateFactory.zoomBy(1f)) }
    actions.zoomOut = { map?.animateCamera(GoogleCameraUpdateFactory.zoomBy(-1f)) }
    LaunchedEffect(map, currentLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        map?.animateCamera(
            GoogleCameraUpdateFactory.newLatLng(
                GoogleLatLng(location.latitude, location.longitude)
            )
        )
    }

    actions.savePosition = {
        map?.cameraPosition?.let { camera ->
            savePosition(prefs, camera.target.latitude, camera.target.longitude, camera.zoom)
        }
    }

    DisposableEffect(mapView) {
        onDispose { actions.savePosition() }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BindMapLifecycle(
    onStart: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDestroy: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var started = false
        var resumed = false
        var destroyed = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    onStop()
                    started = false
                }
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) {
                    if (resumed) {
                        onPause()
                        resumed = false
                    }
                    if (started) {
                        onStop()
                        started = false
                    }
                    onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                if (resumed) onPause()
                if (started) onStop()
                onDestroy()
            }
        }
    }
}

private fun applyMapLibreRasterStyle(
    context: Context,
    map: MapLibreMap,
    mode: MapMode,
    prefs: PrefsWrapper,
    onStyleReady: (Style) -> Unit
) {
    val config = when (mode.tileType) {
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
            context.getString(R.string.map_osm_attribution)
        )
        MapTileType.CUSTOM -> TileConfiguration(
            prefs.getString("map_custom_url", OnlineTileSources.AMAP_TILE_URL).trim()
                .ifEmpty { OnlineTileSources.AMAP_TILE_URL },
            prefs.getString("map_custom_subdomains", OnlineTileSources.AMAP_SUBDOMAINS).trim(),
            19f,
            null
        )
        MapTileType.GOOGLE_NORMAL,
        MapTileType.GOOGLE_HYBRID -> return
    }

    val tileUrls = TileUrlTemplate.expand(config.urlPattern, config.subdomains)
    if (tileUrls.isEmpty()) return

    val tileSet = TileSet("2.2.0", *tileUrls).apply {
        setMinZoom(0f)
        setMaxZoom(config.maxZoom)
        attribution = config.attribution
    }
    val emptyStations = FeatureCollection.fromFeatures(emptyArray<Feature>())
    val iconLayer = SymbolLayer(STATION_ICON_LAYER_ID, STATION_SOURCE_ID).withProperties(
        iconImage(Expression.get(PROPERTY_ICON)),
        iconAllowOverlap(true),
        iconIgnorePlacement(true)
    ).apply { setMaxZoom(CALLSIGN_ZOOM) }
    val labelLayer = SymbolLayer(STATION_LABEL_LAYER_ID, STATION_SOURCE_ID).withProperties(
        iconImage(Expression.get(PROPERTY_LABELED_ICON)),
        iconAllowOverlap(true),
        iconIgnorePlacement(true)
    ).apply { setMinZoom(CALLSIGN_ZOOM) }

    map.setStyle(
        Style.Builder()
            .withLayer(BackgroundLayer(BACKGROUND_LAYER_ID).withProperties(backgroundColor(Color.WHITE)))
            .withSource(RasterSource(RASTER_SOURCE_ID, tileSet, TILE_SIZE))
            .withLayer(RasterLayer(RASTER_LAYER_ID, RASTER_SOURCE_ID))
            .withSource(GeoJsonSource(STATION_SOURCE_ID, emptyStations))
            .withLayer(iconLayer)
            .withLayer(labelLayer)
    ) { style ->
        onStyleReady(style)
    }
}

private class MapLibreStationCache {
    val images = linkedSetOf<StationImage>()
    var features: List<StationFeature>? = null
    var previousImages: Set<StationImage> = emptySet()
}

private fun StationImage.imageId(): String =
    if (call == null) "aprs-symbol-${encodeImageId(symbol)}"
    else "aprs-station-${encodeImageId(call)}-${encodeImageId(symbol)}"

private suspend fun updateMapLibreStations(
    context: Context,
    style: Style,
    stations: List<MapStation>,
    cache: MapLibreStationCache
) {
    val snapshot = stations.map { it.feature }
    // Comment-only changes do not affect MapLibre's symbol layer.
    if (snapshot == cache.features) return
    val wantedImages = stationImages(snapshot)
    val missingImages = wantedImages - cache.images
    val iconSize = (24f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(24)
    // Canvas/text rasterization and GeoJSON serialization never occupy the UI thread.
    val (images, geoJson) = withContext(Dispatchers.Default) {
        val images = missingImages.associateWith { key ->
            ensureActive()
            if (key.call == null) {
                MapModes.symbol2bitmap(key.symbol, iconSize).apply {
                    density = context.resources.displayMetrics.densityDpi
                }
            } else {
                createLabeledStationBitmap(context, key.call, key.symbol, iconSize)
            }
        }
        val features = snapshot.map { station ->
            ensureActive()
            val properties = JsonObject().apply {
                addProperty(PROPERTY_CALL, station.call)
                addProperty(PROPERTY_ICON, StationImage(null, station.symbol).imageId())
                addProperty(PROPERTY_LABELED_ICON, StationImage(station.call, station.symbol).imageId())
            }
            Feature.fromGeometry(Point.fromLngLat(station.lon, station.lat), properties)
        }
        images to FeatureCollection.fromFeatures(features).toJson()
    }
    images.entries.forEachIndexed { index, (key, bitmap) ->
        coroutineContext.ensureActive()
        style.addImage(key.imageId(), bitmap)
        cache.images.add(key)
        // Initial loads can contain many images; leave input/render callbacks time to run.
        if (index % 8 == 7) yield()
    }
    style.getSourceAs<GeoJsonSource>(STATION_SOURCE_ID)?.setGeoJson(geoJson)
    // Retain one previous generation while the native source update is processed asynchronously.
    // Stable keys also mean an insertion cannot invalidate every later callsign image.
    val retiredImages = cache.images - wantedImages -
        (if (snapshot.isEmpty()) emptySet() else cache.previousImages)
    retiredImages.forEach { style.removeImage(it.imageId()) }
    cache.images.removeAll(retiredImages)
    cache.previousImages = wantedImages
    cache.features = snapshot
}

private fun handleMapLibreTap(
    context: Context,
    map: MapLibreMap,
    point: MapLibreLatLng,
    onStationClick: (String) -> Unit
): Boolean {
    val screenPoint = map.projection.toScreenLocation(point)
    val touchSlop = (24f * context.resources.displayMetrics.density).roundToInt()
    val rect = RectF(
        screenPoint.x - touchSlop,
        screenPoint.y - touchSlop,
        screenPoint.x + touchSlop,
        screenPoint.y + touchSlop
    )
    val calls = map.queryRenderedFeatures(rect, STATION_ICON_LAYER_ID, STATION_LABEL_LAYER_ID)
        .mapNotNull { it.getStringProperty(PROPERTY_CALL) }
        .distinct()

    return when (calls.size) {
        0 -> false
        1 -> {
            onStationClick(calls.first())
            true
        }
        else -> {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.map_select)
                .setItems(calls.toTypedArray()) { _, index -> onStationClick(calls[index]) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }
}

private fun createLabeledStationBitmap(
    context: Context,
    call: String,
    symbol: String,
    iconSize: Int
): Bitmap {
    val density = context.resources.displayMetrics.density
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
        this.density = context.resources.displayMetrics.densityDpi
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
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)
    canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)
    canvas.drawText(call, backgroundRect.left + padding, backgroundRect.top + padding + textBaseline, textPaint)
    return bitmap
}

private data class InitialPosition(val lat: Double, val lon: Double, val zoom: Float)

private fun initialPosition(prefs: PrefsWrapper, stations: List<MapStation>): InitialPosition {
    val savedLat = prefs.prefs.getFloat("map_lat", 0f)
    val savedLon = prefs.prefs.getFloat("map_lon", 0f)
    val zoom = prefs.prefs.getFloat("map_zoom", 12f)
    if (savedLat != 0f || savedLon != 0f) {
        return InitialPosition(savedLat.toDouble(), savedLon.toDouble(), zoom)
    }
    val first = stations.firstOrNull()
    return if (first != null) {
        InitialPosition(first.lat, first.lon, zoom)
    } else {
        InitialPosition(39.9042, 116.4074, zoom)
    }
}

private fun savePosition(prefs: PrefsWrapper, lat: Double, lon: Double, zoom: Float) {
    prefs.prefs.edit {
        putFloat("map_lat", lat.toFloat())
        putFloat("map_lon", lon.toFloat())
        putFloat("map_zoom", zoom)
    }
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

private const val TILE_SIZE = 256
private const val CALLSIGN_ZOOM = 10f
private const val BACKGROUND_LAYER_ID = "aprs-embedded-background"
private const val RASTER_SOURCE_ID = "aprs-embedded-raster-source"
private const val RASTER_LAYER_ID = "aprs-embedded-raster-layer"
private const val STATION_SOURCE_ID = "aprs-embedded-stations-source"
private const val STATION_ICON_LAYER_ID = "aprs-embedded-stations-icons"
private const val STATION_LABEL_LAYER_ID = "aprs-embedded-stations-labels"
private const val PROPERTY_CALL = "call"
private const val PROPERTY_ICON = "icon"
private const val PROPERTY_LABELED_ICON = "labeled-icon"
