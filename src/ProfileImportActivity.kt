package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import org.json.JSONObject

class ProfileImportActivity : Activity() {
    private val TAG = "APRSdroid.ProfileImport"
    private val db: StorageDatabase by lazy { StorageDatabase.open(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "created: $intent")
        importConfig()
    }

    private fun importConfig() {
        try {
            val dataUri = intent.data ?: throw IllegalArgumentException("Missing data URI")
            val configString = contentResolver.openInputStream(dataUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText().also { text ->
                    require(text.toByteArray(Charsets.UTF_8).size <= MAX_PROFILE_BYTES) { "Profile is too large" }
                }
            } ?: throw IllegalArgumentException("Cannot open stream for $dataUri")
            val config = JSONObject(configString)
            val preferences = PrefsWrapper.defaultSharedPreferences(this)

            preferences.edit {
                val keys = config.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    require(isImportableKey(key)) { "Unsupported profile key: $key" }
                    val value = config.get(key)
                    validateStoredType(preferences.all[key], value, key)
                    Log.d(TAG, "importing profile key: $key (${value.javaClass.simpleName})")

                    when (value) {
                        is String -> {
                            require(value.length <= MAX_STRING_LENGTH) { "Profile value is too long: $key" }
                            putString(key, value)
                        }
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Number -> putFloat(key, value.toFloat())
                        else -> throw IllegalArgumentException("Unsupported profile value type: $key")
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
            Log.w(TAG, "Profile import failed", e)
        }
        finish()
    }

    private fun validateStoredType(existing: Any?, incoming: Any, key: String) {
        if (existing == null) return
        val compatible = when (existing) {
            is String -> incoming is String
            is Boolean -> incoming is Boolean
            is Int -> incoming is Int
            is Long -> incoming is Long || incoming is Int
            is Float -> incoming is Number
            is Set<*> -> false
            else -> false
        }
        require(compatible) { "Profile value type does not match existing preference: $key" }
    }

    private fun isImportableKey(key: String): Boolean {
        if (key in BLOCKED_KEYS) return false
        if (key in CORE_PROFILE_KEYS) return true
        return PROFILE_PREFIXES.any(key::startsWith)
    }

    private companion object {
        const val MAX_PROFILE_BYTES = 256 * 1024
        const val MAX_STRING_LENGTH = 4096

        val BLOCKED_KEYS = setOf("service_running", "firstrun")

        val CORE_PROFILE_KEYS = setOf(
            "callsign", "ssid", "passcode", "digi_path", "frequency", "status",
            "symbol", "proto", "aprsis", "link", "loc_source", "keepscreen",
            "mapmode", "map_custom_url", "map_custom_subdomains", "activity",
            "show_objects", "show_satellite", "show_age", "conn_log",
        )

        val PROFILE_PREFIXES = listOf(
            "ic705.", "tcp.", "udp.", "http.", "afsk.", "kiss.", "tnc2.",
            "bluetooth.", "bt.", "usb.", "priv_", "pos_", "dgp_", "msg_",
            "notify_", "smartbeaconing.", "periodic.", "manual.", "map_",
        )
    }
}
