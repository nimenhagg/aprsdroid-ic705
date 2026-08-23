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
        setContentView(R.layout.activity_preference)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.preference_toolbar)?.let { toolbar ->
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preference_container, BackendPrefsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
                findPreference<Preference>("ic705_diagnostic_launch")?.setOnPreferenceClickListener {
                    val intent = android.content.Intent(requireContext(), org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity::class.java)
                    startActivity(intent)
                    true
                }
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

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val intent = preference.intent
            if (intent != null) {
                startActivity(intent)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == "proto" || key == "link" || key == "aprsis") {
                loadXml()
            }
        }
    }
}
