package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
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
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preference_container, PrefsFragment())
                .commit()
        }
    }

    fun resolveContentUri(uri: Uri): String {
        val parts = (uri.path ?: "").replace("/document/", "").split(":", limit = 2)
        val storage = if (parts.isNotEmpty()) parts[0] else ""
        val path = if (parts.size > 1) parts[1] else ""
        return if (storage == "primary") {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory().toString() + "/" + path
        } else {
            "/storage/" + storage + "/" + path
        }
    }

    fun parseFilePickerResult(data: Intent, prefName: String, errorId: Int) {
        val dataUri = data.data
        val file: String? = when (dataUri?.scheme) {
            "file" -> dataUri.path
            "content" -> {
                if ("com.android.externalstorage.documents" == dataUri.authority) {
                    resolveContentUri(dataUri)
                } else {
                    val fixupUri = Uri.parse(
                        (data.dataString ?: "").replace(
                            "content://com.android.providers.downloads.documents/document",
                            "content://downloads/public_downloads"
                        )
                    )
                    val cursor = contentResolver.query(fixupUri, null, null, null, null)
                    cursor?.moveToFirst()
                    val idx = cursor?.getColumnIndex("_data") ?: -1
                    val result = if (idx != -1) cursor?.getString(idx) else null
                    cursor?.close()
                    result
                }
            }
            else -> null
        }

        if (file != null) {
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putString(prefName, file).apply()
            Toast.makeText(this, file, Toast.LENGTH_SHORT).show()
            finish()
            startActivity(intent)
        } else {
            val errmsg = getString(errorId, data.dataString)
            Toast.makeText(this, errmsg, Toast.LENGTH_SHORT).show()
            db.addPost(System.currentTimeMillis(), StorageDatabase.Companion.Post.TYPE_ERROR, getString(R.string.post_error), errmsg)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (reqCode) {
                123456 -> {
                    val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    data.data?.let { contentResolver.takePersistableUriPermission(it, takeFlags) }
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("mapfile", data.dataString).apply()
                    finish()
                    startActivity(intent)
                }
                123457 -> parseFilePickerResult(data, "themefile", R.string.themefile_error)
                123458 -> {
                    data.setClass(this, ProfileImportActivity::class.java)
                    startActivity(data)
                }
                else -> super.onActivityResult(reqCode, resultCode, data)
            }
        } else {
            super.onActivityResult(reqCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_prefs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.profile_load -> {
                val getFile = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
                @Suppress("DEPRECATION")
                startActivityForResult(Intent.createChooser(getFile, getString(R.string.profile_import_activity)), 123458)
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
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.intent != null) {
                startActivity(preference.intent)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onResume() {
            super.onResume()
            val act = activity as? PrefsAct ?: return
            findPreference<Preference>("p_connsetup")?.summary = act.prefs.getBackendName()
            findPreference<Preference>("p_location")?.summary = act.prefs.getLocationSourceName()
            findPreference<Preference>("p_symbol")?.summary = getString(R.string.p_symbol_summary) + ": " + act.prefs.getString("symbol", "/$")

            findPreference<Preference>("mapfile")?.setOnPreferenceClickListener {
                val getFile = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
                @Suppress("DEPRECATION")
                act.startActivityForResult(Intent.createChooser(getFile, getString(R.string.p_mapfile_choose)), 123456)
                true
            }
            findPreference<Preference>("themefile")?.setOnPreferenceClickListener {
                val getFile = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
                @Suppress("DEPRECATION")
                act.startActivityForResult(Intent.createChooser(getFile, getString(R.string.p_themefile_choose)), 123457)
                true
            }
        }
    }
}
