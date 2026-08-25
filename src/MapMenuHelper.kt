package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.size

abstract class MapMenuHelper : AppCompatActivity(), View.OnClickListener, LoadingIndicator {
    open val TAG = "APRSdroid.MapMenu"

    var menu_id: Int = R.id.map
    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    var targetcall: String = ""
    var showObjects: Boolean = false

    val isCoordinateChooser: Boolean by lazy { callingActivity != null }
    val crosshair: ImageView by lazy { findViewById(R.id.crosshair) }
    val infoText: TextView by lazy { findViewById(R.id.info) }
    val accept: Button by lazy { findViewById(R.id.accept) }
    val handler = Handler(Looper.getMainLooper())
    val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetcall = getTargetCall()
        showObjects = prefs.getShowObjects()
        setResult(Activity.RESULT_CANCELED)
    }

    override fun onResume() {
        super.onResume()
        if (isCoordinateChooser) {
            crosshair.visibility = View.VISIBLE
            infoText.visibility = View.VISIBLE
            accept.visibility = View.VISIBLE
            val infoRes = intent.getIntExtra("info", 0)
            if (infoRes != 0) accept.text = getString(infoRes)
            accept.setOnClickListener(this)
            accept.isEnabled = false
        } else {
            crosshair.visibility = View.INVISIBLE
            infoText.visibility = View.INVISIBLE
            accept.visibility = View.INVISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            menuInflater.inflate(R.menu.options_map, menu)
            menuInflater.inflate(R.menu.context_call, menu)
            menuInflater.inflate(R.menu.options_activities, menu)
            menuInflater.inflate(R.menu.options, menu)
            menu.findItem(R.id.map)?.isVisible = false
            if (isCoordinateChooser) {
                for (idx in 0 until menu.size) {
                    menu[idx].isVisible = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating options menu", e)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        try {
            super.onPrepareOptionsMenu(menu)
            val tracking = targetcall.isNotEmpty()
            Log.d(TAG, "preparing menu for " + targetcall)
            if (isCoordinateChooser) return true

            menu.findItem(R.id.objects)?.isChecked = prefs.getShowObjects()
            menu.setGroupVisible(R.id.menu_context_call, tracking)
            menu.setGroupVisible(R.id.menu_options_activities, !tracking)
            menu.setGroupVisible(R.id.menu_options, !tracking)
            menu.findItem(R.id.startstopbtn)?.isVisible = false
            menu.findItem(R.id.singlebtn)?.isVisible = false
            menu.findItem(R.id.about)?.isVisible = false

            val modesmenu = menu.findItem(R.id.overlays)?.subMenu
            if (modesmenu != null) {
                val defaultMode = MapModes.defaultMapMode(this, prefs)
                val configuredModeIds = MapModes.all_mapmodes.mapTo(mutableSetOf()) { it.menu_id }
                modesmenu.findItem(R.id.normal)?.isVisible = R.id.normal in configuredModeIds
                modesmenu.findItem(R.id.satellite)?.isVisible = R.id.satellite in configuredModeIds
                for (mode in MapModes.all_mapmodes) {
                    val item = modesmenu.findItem(mode.menu_id) ?: modesmenu.add(R.id.mapmodes, mode.menu_id, 0, mode.title)
                    item.isCheckable = true
                    if (mode == defaultMode) item.isChecked = true
                    item.isEnabled = mode.isAvailable(this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing options menu", e)
        }
        return true
    }

    override fun onClick(view: View) {
        if (view.id == R.id.accept) {
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
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
        title = getMapTitlePrefix() + ": " + targetcall
        invalidateOptionsMenu()
    }

    fun stopFollowStation() {
        targetcall = ""
        title = getMapTitlePrefix()
        invalidateOptionsMenu()
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

    fun onMapModeItem(mi: MenuItem, mm: MapMode): Boolean {
        MapModes.setDefault(prefs, mm.tag)
        setMapMode(mm)
        mi.isChecked = true
        return true
    }

    override fun onOptionsItemSelected(mi: MenuItem): Boolean {
        val mapmode = MapModes.fromMenuItem(mi)
        if (mapmode != null) {
            return onMapModeItem(mi, mapmode)
        }
        return when (mi.itemId) {
            R.id.objects -> {
                val newState = prefs.toggleBoolean("show_objects", true)
                mi.isChecked = newState
                showObjects = newState
                reloadMap()
                true
            }
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
                true
            }
            R.id.hub -> {
                startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.log -> {
                startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.conversations -> {
                startActivity(Intent(this, ConversationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.export -> {
                onStartLoading()
                LogExporter(this, StorageDatabase.open(this), null) { onStopLoading() }.execute()
                true
            }
            R.id.clear -> {
                onStartLoading()
                StorageCleaner(this, StorageDatabase.open(this)) { onStopLoading() }.execute()
                true
            }
            else -> {
                if (targetcall.isNotEmpty()) {
                    when (mi.itemId) {
                        R.id.details -> {
                            UIHelper.openCallsignDetails(this, targetcall)
                            true
                        }
                        R.id.message -> {
                            UIHelper.openMessageChat(this, targetcall)
                            true
                        }
                        else -> super.onOptionsItemSelected(mi)
                    }
                } else {
                    super.onOptionsItemSelected(mi)
                }
            }
        }
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
        infoText.text = getString(R.string.map_coordinates_two_lines, coords.first, coords.second)
        accept.isEnabled = true
    }
}
