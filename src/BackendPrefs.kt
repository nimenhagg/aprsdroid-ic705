package org.aprsdroid.app

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity

class BackendPrefs : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener, PermissionHelper {

    companion object {
        const val BACKEND_PERMISSION = 1000
        const val REQUEST_GPS = 1010
    }

    private fun loadXml() {
        val prefs = PrefsWrapper(this)
        @Suppress("DEPRECATION")
        addPreferencesFromResource(R.xml.backend)
        val protoXml = AprsBackend.prefxml_proto(prefs)
        if (protoXml != 0) {
            @Suppress("DEPRECATION")
            addPreferencesFromResource(protoXml)
        }
        val additionalXml = AprsBackend.prefxml_backend(prefs)
        if (additionalXml != 0) {
            @Suppress("DEPRECATION")
            addPreferencesFromResource(additionalXml)
            hookPasscode()
            hookGpsPermission()
        }
        val perms = AprsBackend.defaultBackendPermissions(prefs)
        if (perms.isNotEmpty()) {
            checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
        }
    }

    private fun hookPasscode() {
        @Suppress("DEPRECATION")
        findPreference("passcode")?.setOnPreferenceClickListener {
            PasscodeDialog(this, false).show()
            true
        }
    }

    private fun hookGpsPermission() {
        @Suppress("DEPRECATION")
        findPreference("kenwood.gps")?.setOnPreferenceClickListener { pref ->
            val cb = pref as? CheckBoxPreference
            if (cb?.isChecked == true) {
                cb.isChecked = false
                checkPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_GPS
                )
            }
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadXml()
        @Suppress("DEPRECATION")
        preferenceScreen?.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        @Suppress("DEPRECATION")
        preferenceScreen?.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "proto" || key == "link" || key == "aprsis") {
            @Suppress("DEPRECATION")
            preferenceScreen = null
            loadXml()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getActionName(action: Int): Int = R.string.p_conn_kwd_gps

    override fun onAllPermissionsGranted(action: Int) {
        if (action == REQUEST_GPS) {
            @Suppress("DEPRECATION")
            (findPreference("kenwood.gps") as? CheckBoxPreference)?.isChecked = true
        }
    }

    override fun onPermissionsFailedCancel(action: Int) {}
}
