package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.util.Scanner

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
            val isStream = contentResolver.openInputStream(dataUri)
                ?: throw IllegalArgumentException("Cannot open stream for " + dataUri)
            val scanner = Scanner(isStream).useDelimiter("\\A")
            val configString = scanner.next()
            val config = JSONObject(configString)
            val prefsedit = PreferenceManager.getDefaultSharedPreferences(this).edit()

            val keys = config.keys()
            while (keys.hasNext()) {
                val item = keys.next()
                val value = config.get(item)
                Log.d(TAG, "reading: " + item + " = " + value + "/" + value.javaClass.simpleName)

                when (value) {
                    is String -> prefsedit.putString(item, value)
                    is Boolean -> prefsedit.putBoolean(item, value)
                    is Int -> prefsedit.putInt(item, value)
                    is Number -> prefsedit.putFloat(item, value.toFloat())
                }
            }
            prefsedit.apply()
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
