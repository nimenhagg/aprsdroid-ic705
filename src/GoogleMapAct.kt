package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.aprsdroid.app.ui.screen.MapScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.ArrayList

class GoogleMapAct : MapLoaderBase(),
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnInfoWindowClickListener,
    GoogleMap.OnCameraMoveListener {

    private lateinit var mapview: MapView
    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    var map: GoogleMap? = null
    val icons = mutableMapOf<String, BitmapDescriptor>()
    var visible_callsigns = true
    var first_load = true
    val CALLSIGN_ZOOM = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mapview = MapView(this).apply {
            onCreate(savedInstanceState)
            getMapAsync { googleMap ->
                Log.d(TAG, "Got Google map!")
                map = googleMap
                loadMapViewPosition()
                setMapMode(MapModes.defaultMapMode(this@GoogleMapAct, prefs))
                googleMap.setOnMarkerClickListener(this@GoogleMapAct)
                googleMap.setOnInfoWindowClickListener(this@GoogleMapAct)
                googleMap.setOnCameraMoveListener(this@GoogleMapAct)
                googleMap.uiSettings.isCompassEnabled = true
                googleMap.uiSettings.isZoomControlsEnabled = false
                visible_callsigns = googleMap.cameraPosition.zoom > CALLSIGN_ZOOM
                startLoading()
            }
        }

        setContent {
            AprsTheme {
                val showAboutDialog = remember { mutableStateOf(false) }
                MapScreen(
                    title = if (targetcall.isNotEmpty()) getString(R.string.map_track_call, targetcall) else getMapTitlePrefix(),
                    isLoading = isLoadingState.value,
                    showOsmAttribution = false,
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
                    onMyLocationClick = {
                        val mycall = prefs.getCallSsid()
                        val pos = storage.getStaPosition(mycall)
                        if (pos.count > 0 && pos.moveToFirst()) {
                            val latIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                            val lonIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LON)
                            if (latIdx >= 0 && lonIdx >= 0) {
                                val lat = pos.getInt(latIdx) / 1000000.0
                                val lon = pos.getInt(lonIdx) / 1000000.0
                                map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
                            }
                        }
                        pos.close()
                    },
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
                        StorageCleaner(this, storage) { onStopLoading() }.execute()
                    },
                    onOpenAbout = {
                        showAboutDialog.value = true
                    },
                    mapContent = {
                        AndroidView(
                            factory = { mapview },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
                if (showAboutDialog.value) {
                    AboutDialogContent(
                        onDismiss = { showAboutDialog.value = false },
                        onOpenGithub = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/nimenhagg/aprsdroid-ic705".toUri(),
                                ),
                            )
                        },
                    )
                }
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
        super.onPause()
        if (::mapview.isInitialized) mapview.onPause()
        map?.let {
            val target = it.cameraPosition.target
            saveMapViewPosition(target.latitude.toFloat(), target.longitude.toFloat(), it.cameraPosition.zoom)
        }
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
        locReceiver.startTask(Intent())
    }

    override fun changeZoom(delta: Int) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta.toFloat()))
    }

    override fun loadMapViewPosition(lat: Float, lon: Float, zoom: Float) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat.toDouble(), lon.toDouble()), zoom))
    }

    override fun setMapMode(mm: MapMode) {
        if (mm.viewClass != GoogleMapAct::class.java) {
            map?.cameraPosition?.let { camera ->
                saveMapViewPosition(
                    camera.target.latitude.toFloat(),
                    camera.target.longitude.toFloat(),
                    camera.zoom
                )
            }
            super.setMapMode(mm)
            return
        }

        val gMap = map ?: return

        when (mm.tileType) {
            MapTileType.GOOGLE_NORMAL -> {
                gMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
            MapTileType.GOOGLE_HYBRID -> {
                gMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            }
            MapTileType.AMAP,
            MapTileType.OSM,
            MapTileType.CUSTOM -> return
        }
    }

    override fun onStationUpdate(sl: ArrayList<Station>) {
        val gMap = map ?: return
        gMap.clear()

        for (sta in sl) {
            val pos = LatLng(sta.lat, sta.lon)
            val sym = sta.symbol ?: "/$"
            val bmp = MapModes.symbol2bitmap(sym, 48)
            val desc = BitmapDescriptorFactory.fromBitmap(bmp)
            val marker = gMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(sta.call)
                    .snippet(sta.comment ?: "")
                    .icon(desc)
                    .anchor(0.5f, 0.5f)
            )
            marker?.tag = sta.call
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val call = marker.tag as? String ?: marker.title
        if (!call.isNullOrEmpty()) {
            val mycall = prefs.getCallSsid()
            var myLat = 0
            var myLon = 0
            val pos = storage.getStaPosition(mycall)
            if (pos.count > 0 && pos.moveToFirst()) {
                val latIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIdx >= 0 && lonIdx >= 0) {
                    myLat = pos.getInt(latIdx)
                    myLon = pos.getInt(lonIdx)
                }
            }
            pos.close()
            org.aprsdroid.app.ui.component.StationBottomSheetHelper.show(this, call, storage, myLat, myLon)
            return true
        }
        return false
    }

    override fun onInfoWindowClick(marker: Marker) {
        val call = marker.tag as? String ?: marker.title
        if (!call.isNullOrEmpty()) {
            onMarkerClick(marker)
        }
    }

    override fun onCameraMove() {
        map?.let {
            val target = it.cameraPosition.target
            updateCoordinateInfo(target.latitude.toFloat(), target.longitude.toFloat())
        }
    }
}
