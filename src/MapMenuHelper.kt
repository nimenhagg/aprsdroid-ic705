package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.core.net.toUri

abstract class MapMenuHelper : ComponentActivity(), LoadingIndicator {
    open val TAG = "APRSdroid.MapMenu"

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    var targetcall: String = ""
    var showObjects: Boolean = false

    val isCoordinateChooser: Boolean by lazy { callingActivity != null }
    val isLoadingState = mutableStateOf(false)
    val coordinateInfoState = mutableStateOf("")
    val showOsmAttributionState = mutableStateOf(false)
    val currentMapModeState = mutableStateOf<MapMode?>(null)
    val showObjectsState = mutableStateOf(true)

    val handler = Handler(Looper.getMainLooper())
    val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetcall = getTargetCall()
        showObjects = prefs.getShowObjects()
        showObjectsState.value = showObjects
        currentMapModeState.value = MapModes.defaultMapMode(this, prefs)
        setResult(Activity.RESULT_CANCELED)
    }

    fun getTargetCall(): String {
        return intent?.dataString
            ?: intent?.getStringExtra("call")
            ?: intent?.getStringExtra("targetcall")
            ?: ""
    }

    fun getMapTitlePrefix(): String {
        val mode = MapModes.defaultMapMode(this, prefs)
        return mode.title ?: getString(R.string.app_map)
    }

    fun startFollowStation(call: String) {
        targetcall = call
    }

    fun stopFollowStation() {
        targetcall = ""
    }

    fun switchMapActivity(cls: Class<*>) {
        if (cls != this::class.java) {
            val intent = Intent(this, cls)
            if (targetcall.isNotEmpty()) {
                intent.data = targetcall.toUri()
            }
            startActivity(intent)
            finish()
        }
    }

    open fun setMapMode(mm: MapMode) {
        switchMapActivity(mm.viewClass)
    }

    override fun onStartLoading() {
        isLoadingState.value = true
    }

    override fun onStopLoading() {
        isLoadingState.value = false
    }

    abstract fun reloadMap()
    abstract fun changeZoom(delta: Int)
    abstract fun loadMapViewPosition(lat: Float, lon: Float, zoom: Float)

    fun saveMapViewPosition(lat: Float, lon: Float, zoom: Float) {
        prefs.prefs.edit {
            putFloat("map_lat", lat)
            putFloat("map_lon", lon)
            putFloat("map_zoom", zoom)
        }
    }

    fun loadMapViewPosition() {
        val lat = prefs.prefs.getFloat("map_lat", 39.9042f)
        val lon = prefs.prefs.getFloat("map_lon", 116.4074f)
        val zoom = prefs.prefs.getFloat("map_zoom", 12.0f)
        loadMapViewPosition(lat, lon, zoom)
    }

    fun updateCoordinateInfo(lat: Float, lon: Float) {
        resultIntent.putExtra("lat", lat).putExtra("lon", lon)
        val coords = AprsPacket.formatCoordinates(lat, lon)
        coordinateInfoState.value = getString(R.string.map_coordinates_two_lines, coords.first, coords.second)
    }
}
