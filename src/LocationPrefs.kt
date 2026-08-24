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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.location.PeriodicGPS

class LocationPrefs : AppCompatActivity(), PermissionHelper {

    companion object {
        const val REQUEST_GPS = 101
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    override var pendingPermissionAction: Int? = null
    override var pendingPermissions: Set<String> = emptySet()
    override val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> handlePermissionResult(grants) }
    private val mapLocationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        Log.d("LocationPrefs", "map result=${result.resultCode} $data")
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            prefs.prefs.edit {
                putString("manual_lat", data.getFloatExtra("lat", 0.0f).toString())
                putString("manual_lon", data.getFloatExtra("lon", 0.0f).toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)
        setContentView(R.layout.activity_preference)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.preference_toolbar)?.let { toolbar ->
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preference_container, LocationPrefsFragment())
                .commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePermissionState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.dataString) {
            "gps2manual" -> {
                checkPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_GPS
                )
            }
            "chooseOnMap" -> {
                val mapmode = MapModes.defaultMapMode(this, prefs)
                mapLocationPicker.launch(
                    Intent(this, mapmode.viewClass).putExtra("info", R.string.p_source_from_map_save),
                )
            }
        }
    }

    override fun getActionName(action: Int): Int = R.string.p_source_get_last

    override fun onAllPermissionsGranted(action: Int) {
        val ls = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val l = ls?.let { locationManager ->
            PeriodicGPS.bestProvider(locationManager)?.let { provider ->
                try { locationManager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
        }

        if (l != null) {
            prefs.prefs.edit {
                putString("manual_lat", l.latitude.toString())
                putString("manual_lon", l.longitude.toString())
            }
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
            addPreferencesFromResource(R.xml.preferences_privacy)
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
