package org.aprsdroid.app

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class BackendPrefs : AppCompatActivity(), PermissionHelper {

    companion object {
        const val BACKEND_PERMISSION = 1000
        const val REQUEST_GPS = 1010
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, BackendPrefsFragment())
                .commit()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getActionName(action: Int): Int = R.string.p_conn_kwd_gps

    override fun onAllPermissionsGranted(action: Int) {
        if (action == REQUEST_GPS) {
            val fragment = supportFragmentManager.findFragmentById(android.R.id.content) as? BackendPrefsFragment
            fragment?.findPreference<CheckBoxPreference>("kenwood.gps")?.isChecked = true
        }
    }

    override fun onPermissionsFailedCancel(action: Int) {}

    class BackendPrefsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

        private fun loadXml() {
            preferenceScreen = null
            val prefs = PrefsWrapper(requireContext())
            setPreferencesFromResource(R.xml.backend, null)
            val protoXml = AprsBackend.prefxml_proto(prefs)
            if (protoXml != 0) {
                addPreferencesFromResource(protoXml)
            }
            val additionalXml = AprsBackend.prefxml_backend(prefs)
            if (additionalXml != 0) {
                addPreferencesFromResource(additionalXml)
                hookPasscode()
                hookGpsPermission()
            }
            val perms = AprsBackend.defaultBackendPermissions(prefs)
            if (perms.isNotEmpty()) {
                (activity as? BackendPrefs)?.checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
            }
        }

        private fun hookPasscode() {
            findPreference<Preference>("passcode")?.setOnPreferenceClickListener {
                PasscodeDialog(requireActivity(), false).show()
                true
            }
        }

        private fun hookGpsPermission() {
            findPreference<Preference>("kenwood.gps")?.setOnPreferenceClickListener { pref ->
                val cb = pref as? CheckBoxPreference
                if (cb?.isChecked == true) {
                    cb.isChecked = false
                    (activity as? BackendPrefs)?.checkPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_GPS
                    )
                }
                true
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
            if (key == "proto" || key == "link" || key == "aprsis") {
                loadXml()
            }
        }
    }
}
