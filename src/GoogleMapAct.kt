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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.googlemapview)

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
            googleMap.uiSettings.isZoomControlsEnabled = true
            visible_callsigns = googleMap.cameraPosition.zoom > CALLSIGN_ZOOM
            startLoading()
        }
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
