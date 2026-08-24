package org.aprsdroid.app

import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.material.appbar.MaterialToolbar
import org.aprsdroid.app.map.OnlineTileProvider
import java.util.ArrayList

class GoogleMapAct : MapLoaderBase(),
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnInfoWindowClickListener,
    GoogleMap.OnCameraMoveListener {

    val loading: View by lazy { findViewById(R.id.loading) }
    val mapview: MapView by lazy { findViewById(R.id.mapview) }
    var map: GoogleMap? = null
    val icons = mutableMapOf<String, BitmapDescriptor>()
    var visible_callsigns = true
    var first_load = true
    val CALLSIGN_ZOOM = 8

    private var currentTileOverlay: TileOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.googlemapview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        mapview.onCreate(savedInstanceState)
        mapview.getMapAsync { googleMap ->
            Log.d(TAG, "Got map!")
            map = googleMap
            loadMapViewPosition()
            setMapMode(MapModes.defaultMapMode(this, prefs))
            googleMap.setOnMarkerClickListener(this)
            googleMap.setOnInfoWindowClickListener(this)
            googleMap.setOnCameraMoveListener(this)
            googleMap.uiSettings.isCompassEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = false
            visible_callsigns = googleMap.cameraPosition.zoom > CALLSIGN_ZOOM
            startLoading()
        }

        findViewById<View>(R.id.btn_zoom_in)?.setOnClickListener {
            changeZoom(1)
        }
        findViewById<View>(R.id.btn_zoom_out)?.setOnClickListener {
            changeZoom(-1)
        }
        findViewById<View>(R.id.btn_my_location)?.setOnClickListener {
            val mycall = prefs.getCallSsid()
            val pos = storage.getStaPosition(mycall)
            if (pos.count > 0 && pos.moveToFirst()) {
                val latIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIdx = pos.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIdx >= 0 && lonIdx >= 0) {
                    val lat = pos.getInt(latIdx) / 1000000.0
                    val lon = pos.getInt(lonIdx) / 1000000.0
                    map?.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(com.google.android.gms.maps.model.LatLng(lat, lon)))
                }
            }
            pos.close()
        }
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
        super.onPause()
        mapview.onPause()
        map?.let {
            val target = it.cameraPosition.target
            saveMapViewPosition(target.latitude.toFloat(), target.longitude.toFloat(), it.cameraPosition.zoom)
        }
    }

    override fun onStop() {
        super.onStop()
        mapview.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapview.onDestroy()
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
        locReceiver.startTask(android.content.Intent())
    }

    override fun changeZoom(delta: Int) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta.toFloat()))
    }

    override fun loadMapViewPosition(lat: Float, lon: Float, zoom: Float) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat.toDouble(), lon.toDouble()), zoom))
    }

    override fun setMapMode(mm: MapMode) {
        val gMap = map ?: return
        currentTileOverlay?.remove()
        currentTileOverlay = null

        when (mm.tileType) {
            MapTileType.GOOGLE_NORMAL -> {
                gMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
            MapTileType.GOOGLE_HYBRID -> {
                gMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            }
            MapTileType.AMAP -> {
                gMap.mapType = GoogleMap.MAP_TYPE_NONE
                val provider = OnlineTileProvider.createAmap()
                currentTileOverlay = gMap.addTileOverlay(TileOverlayOptions().tileProvider(provider).fadeIn(false))
            }
            MapTileType.OSM -> {
                gMap.mapType = GoogleMap.MAP_TYPE_NONE
                val provider = OnlineTileProvider.createOsm()
                currentTileOverlay = gMap.addTileOverlay(TileOverlayOptions().tileProvider(provider).fadeIn(false))
            }
            MapTileType.CUSTOM -> {
                gMap.mapType = GoogleMap.MAP_TYPE_NONE
                val customUrl = prefs.getString("map_custom_url", OnlineTileProvider.AMAP_TILE_URL).trim()
                val customSubdomains = prefs.getString("map_custom_subdomains", "1234").trim()
                val effectiveUrl = customUrl.ifEmpty { OnlineTileProvider.AMAP_TILE_URL }
                val provider = OnlineTileProvider.createCustom(effectiveUrl, customSubdomains)
                currentTileOverlay = gMap.addTileOverlay(TileOverlayOptions().tileProvider(provider).fadeIn(false))
            }
        }
    }

    override fun onStationUpdate(sl: ArrayList<Station>) {
        val gMap = map ?: return
        gMap.clear()
        // Re-add tile overlay if active since clear() clears all overlays and markers
        val currentMode = MapModes.defaultMapMode(this, prefs)
        if (currentMode.tileType != MapTileType.GOOGLE_NORMAL && currentMode.tileType != MapTileType.GOOGLE_HYBRID) {
            setMapMode(currentMode)
        }

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
            marker.showInfoWindow()
        }
        return true
    }

    override fun onInfoWindowClick(marker: Marker) {
        val call = marker.tag as? String ?: marker.title
        if (!call.isNullOrEmpty()) {
            UIHelper.openCallsignDetails(this, call)
        }
    }

    override fun onCameraMove() {
        map?.let {
            val target = it.cameraPosition.target
            updateCoordinateInfo(target.latitude.toFloat(), target.longitude.toFloat())
        }
    }
}
