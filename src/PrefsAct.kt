package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrefsAct : AppCompatActivity() {
    private val profileDocumentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            startActivity(
                Intent(this, ProfileImportActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    fun exportPrefs() {
        val filename = String.format("profile-%s.aprs", SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()))
        val directory = UIHelper.getExportDirectory(this)
        val file = File(directory, filename)
        try {
            directory.mkdirs()
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val json = JSONObject(sp.all)
            val fo = PrintWriter(file)
            fo.println(json.toString(2))
            fo.close()

            UIHelper.shareFile(this, file, filename)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
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
                .replace(R.id.preference_container, PrefsFragment())
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_prefs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.profile_load -> {
                profileDocumentPicker.launch(arrayOf("*/*"))
                true
            }
            R.id.profile_export -> {
                exportPrefs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class PrefsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            if (!BuildConfig.GOOGLE_MAPS_ENABLED) {
                val mapModePref = findPreference<androidx.preference.ListPreference>("mapmode")
                val entries = resources.getTextArray(R.array.p_map_source_e)
                val values = resources.getTextArray(R.array.p_map_source_ev)
                val retained = entries.zip(values).filterNot { (_, value) ->
                    value == "google" || value == "satellite"
                }
                mapModePref?.entries = retained.map { it.first }.toTypedArray()
                mapModePref?.entryValues = retained.map { it.second }.toTypedArray()
                if (mapModePref?.value == "google" || mapModePref?.value == "satellite") {
                    mapModePref.value = "osm"
                }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.intent != null) {
                startActivity(preference.intent)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }

        private fun updateMapPreferenceVisibilities(mapMode: String?) {
            val isCustom = (mapMode == "custom")

            findPreference<Preference>("map_custom_url")?.isVisible = isCustom
            findPreference<Preference>("map_custom_subdomains")?.isVisible = isCustom
        }

        override fun onResume() {
            super.onResume()
            val act = activity as? PrefsAct ?: return
            findPreference<Preference>("p_connsetup")?.summary = act.prefs.getBackendName()
            findPreference<Preference>("p_location")?.summary = act.prefs.getLocationSourceName()
            findPreference<Preference>("p_symbol")?.summary = getString(R.string.p_symbol_summary) + ": " + act.prefs.getString("symbol", "/$")

            val mapModePref = findPreference<androidx.preference.ListPreference>("mapmode")
            val currentMapMode = mapModePref?.value ?: act.prefs.getString("mapmode", "amap")
            updateMapPreferenceVisibilities(currentMapMode)

            mapModePref?.setOnPreferenceChangeListener { _, newValue ->
                updateMapPreferenceVisibilities(newValue as? String)
                true
            }

            val customUrlPref = findPreference<androidx.preference.EditTextPreference>("map_custom_url")
            if (customUrlPref?.text?.contains("autonavi.com") == true) {
                customUrlPref.text = ""
                act.prefs.set("map_custom_url", "")
            }
            val subdomainsPref = findPreference<androidx.preference.EditTextPreference>("map_custom_subdomains")
            if (subdomainsPref?.text == "1234" && customUrlPref?.text.isNullOrEmpty()) {
                subdomainsPref.text = ""
                act.prefs.set("map_custom_subdomains", "")
            }

        }
    }
}
