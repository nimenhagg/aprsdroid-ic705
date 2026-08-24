package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

class ProfileImportActivity : Activity() {
    val TAG = "APRSdroid.ProfileImport"
    val db: StorageDatabase by lazy { StorageDatabase.open(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "created: " + intent)
        import_config()
    }

    fun import_config() {
        try {
            val dataUri = intent.data ?: throw IllegalArgumentException("Missing data URI")
            val configString = contentResolver.openInputStream(dataUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalArgumentException("Cannot open stream for $dataUri")
            val config = JSONObject(configString)
            PreferenceManager.getDefaultSharedPreferences(this).edit {
                val keys = config.keys()
                while (keys.hasNext()) {
                    val item = keys.next()
                    val value = config.get(item)
                    Log.d(TAG, "reading: " + item + " = " + value + "/" + value.javaClass.simpleName)

                    when (value) {
                        is String -> putString(item, value)
                        is Boolean -> putBoolean(item, value)
                        is Int -> putInt(item, value)
                        is Number -> putFloat(item, value.toFloat())
                    }
                }
            }
            val msg = getString(R.string.profile_import_done, dataUri.path)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            db.addPost(System.currentTimeMillis(), StorageDatabase.Companion.Post.TYPE_INFO, getString(R.string.profile_import_activity), msg)
            startActivity(Intent(this, LogActivity::class.java))
        } catch (e: Exception) {
            val errmsg = getString(R.string.profile_import_error, e.message)
            Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
            db.addPost(System.currentTimeMillis(), StorageDatabase.Companion.Post.TYPE_ERROR, getString(R.string.profile_import_activity), errmsg)
            e.printStackTrace()
        }
        finish()
    }
}
