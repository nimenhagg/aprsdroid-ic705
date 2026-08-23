package org.aprsdroid.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.location.PeriodicGPS

class LocationPrefs : AppCompatActivity(), PermissionHelper {

    companion object {
        const val REQUEST_GPS = 101
        const val REQUEST_MAP = 102
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LocationPrefsFragment())
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        when (intent?.dataString) {
            "gps2manual" -> {
                checkPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_GPS
                )
            }
            "chooseOnMap" -> {
                val mapmode = MapModes.defaultMapMode(this, prefs)
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this, mapmode.viewClass).putExtra("info", R.string.p_source_from_map_save),
                    REQUEST_MAP
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        Log.d("LocationPrefs", "onActResult: request=$reqCode result=$resultCode $data")
        if (resultCode == Activity.RESULT_OK && reqCode == REQUEST_MAP && data != null) {
            prefs.prefs.edit()
                .putString("manual_lat", data.getFloatExtra("lat", 0.0f).toString())
                .putString("manual_lon", data.getFloatExtra("lon", 0.0f).toString())
                .apply()
        } else {
            super.onActivityResult(reqCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getActionName(action: Int): Int = R.string.p_source_get_last

    override fun onAllPermissionsGranted(action: Int) {
        val ls = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val bestProv = ls?.let { PeriodicGPS.bestProvider(it) }
        val l = if (bestProv != null && ls != null) {
            try { ls.getLastKnownLocation(bestProv) } catch (_: SecurityException) { null }
        } else null

        if (l != null) {
            prefs.prefs.edit()
                .putString("manual_lat", l.latitude.toString())
                .putString("manual_lon", l.longitude.toString())
                .apply()
        } else {
            Toast.makeText(this, getString(R.string.map_track_unknown, prefs.getCallsign()), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPermissionsFailedCancel(action: Int) {}

    class LocationPrefsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

        private fun loadXml() {
            preferenceScreen = null
            val prefs = PrefsWrapper(requireContext())
            setPreferencesFromResource(R.xml.location, null)
            val prefRes = LocationSource.instanciatePrefsAct(prefs)
            if (prefRes != 0) {
                addPreferencesFromResource(prefRes)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            loadXml()
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen?.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen?.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == "loc_source" || key == "manual_lat" || key == "manual_lon") {
                loadXml()
            }
        }
    }
}
