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
import org.json.JSONArray
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
        val values = linkedMapOf<String, Any>()

        val keys = config.keys()
        while (keys.hasNext()) {
            val key = keys.next()

            if (ProfileImportSchema.isBlockedKey(key)) {
                Log.d(TAG, "skipping blocked profile key: $key")
                continue
            }
            val expectedType = ProfileImportSchema.typeFor(key)
            if (expectedType == null) {
                Log.d(TAG, "skipping unknown profile key: $key")
                continue
            }

            val rawValue = config.get(key)
            require(rawValue !== JSONObject.NULL) { "Null profile value is not supported: $key" }
            val value = when (expectedType) {
                ProfilePreferenceType.STRING_SET -> jsonStringSet(key, rawValue)
                else -> rawValue
            }

            ProfileImportSchema.validateType(key, value)
            validateValueBounds(key, value)
            values[key] = value
        }

        return ParsedProfile(dataUri, values)
    }

    private fun jsonStringSet(key: String, value: Any): Set<String> {
        require(value is JSONArray) { "Profile value type does not match preference schema: $key" }
        require(value.length() <= MAX_STRING_SET_ENTRIES) { "Profile set has too many entries: $key" }

        val result = linkedSetOf<String>()
        for (index in 0 until value.length()) {
            val item = value.get(index)
            require(item is String) { "Profile set contains a non-string value: $key" }
            require(item.length <= MAX_STRING_LENGTH) { "Profile value is too long: $key" }
            result += item
        }
        return result
    }

    private fun validateValueBounds(key: String, value: Any) {
        when (value) {
            is String -> require(value.length <= MAX_STRING_LENGTH) {
                "Profile value is too long: $key"
            }
            is Set<*> -> require(value.size <= MAX_STRING_SET_ENTRIES) {
                "Profile set has too many entries: $key"
            }
        }
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
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            putStringSet(key, value as Set<String>)
                        }
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

    private data class ParsedProfile(
        val dataUri: Uri,
        val values: Map<String, Any>,
    )

    private companion object {
        const val MAX_PROFILE_BYTES = 256 * 1024
        const val MAX_STRING_LENGTH = 4096
        const val MAX_STRING_SET_ENTRIES = 100
    }
}
