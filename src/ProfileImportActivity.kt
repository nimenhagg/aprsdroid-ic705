package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import org.json.JSONObject

class ProfileImportActivity : Activity() {
    private val TAG = "APRSdroid.ProfileImport"
    private val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val importExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "profile-import").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "created: $intent")
        loadConfigForConfirmation()
    }

    override fun onDestroy() {
        importExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadConfigForConfirmation() {
        val dataUri = intent.data
        if (dataUri == null) {
            showImportError(IllegalArgumentException("Missing data URI"))
            return
        }

        importExecutor.execute {
            val result = runCatching { parseConfig(dataUri) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = ::showImportConfirmation,
                    onFailure = ::showImportError,
                )
            }
        }
    }

    private fun parseConfig(dataUri: Uri): ParsedProfile {
        val configString = contentResolver.openInputStream(dataUri)?.use(::readProfile)
            ?: throw IllegalArgumentException("Cannot open stream for $dataUri")
        val config = JSONObject(configString)
        val preferences = PrefsWrapper.defaultSharedPreferences(this)
        val values = linkedMapOf<String, Any>()

        val keys = config.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(isImportableKey(key)) { "Unsupported profile key: $key" }
            val value = config.get(key)
            require(value !== JSONObject.NULL) { "Null profile value is not supported: $key" }
            ProfileImportSchema.validateType(key, preferences.all[key], value)

            if (value is String) {
                require(value.length <= MAX_STRING_LENGTH) { "Profile value is too long: $key" }
            }
            require(
                value is String ||
                    value is Boolean ||
                    value is Int ||
                    value is Long ||
                    value is Number
            ) { "Unsupported profile value type: $key" }

            values[key] = value
        }

        return ParsedProfile(dataUri, values)
    }

    private fun readProfile(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_PROFILE_BYTES) { "Profile is too large" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun showImportConfirmation(profile: ParsedProfile) {
        val location = profile.dataUri.path ?: profile.dataUri.toString()
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_import_confirm_title)
            .setMessage(
                getString(
                    R.string.profile_import_confirm_message,
                    profile.values.size,
                    location,
                ),
            )
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(android.R.string.ok) { _, _ -> applyConfig(profile) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun applyConfig(profile: ParsedProfile) {
        try {
            val preferences = PrefsWrapper.defaultSharedPreferences(this)
            preferences.edit {
                for ((key, value) in profile.values) {
                    Log.d(TAG, "importing profile key: $key (${value.javaClass.simpleName})")
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Number -> putFloat(key, value.toFloat())
                        else -> throw IllegalArgumentException("Unsupported profile value type: $key")
                    }
                }
            }

            val msg = getString(R.string.profile_import_done, profile.dataUri.path)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            db.addPost(
                System.currentTimeMillis(),
                StorageDatabase.Companion.Post.TYPE_INFO,
                getString(R.string.profile_import_activity),
                msg,
            )
            startActivity(Intent(this, LogActivity::class.java))
            finish()
        } catch (error: Exception) {
            showImportError(error)
        }
    }

    private fun showImportError(error: Throwable) {
        val errmsg = getString(R.string.profile_import_error, error.message)
        Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
        db.addPost(
            System.currentTimeMillis(),
            StorageDatabase.Companion.Post.TYPE_ERROR,
            getString(R.string.profile_import_activity),
            errmsg,
        )
        Log.w(TAG, "Profile import failed", error)
        finish()
    }

    private fun isImportableKey(key: String): Boolean {
        if (key in BLOCKED_KEYS) return false
        if (key in CORE_PROFILE_KEYS) return true
        return PROFILE_PREFIXES.any(key::startsWith)
    }

    private data class ParsedProfile(
        val dataUri: Uri,
        val values: Map<String, Any>,
    )

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
