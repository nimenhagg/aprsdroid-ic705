package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import de.duenndns.RingtonePreference

class NotificationPrefs : AppCompatActivity() {

    companion object {
        private const val STATE_RINGTONE_KEY = "ringtone_key"
    }

    var currentRingtoneKey: String? = null
    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            currentRingtoneKey = null
            return@registerForActivityResult
        }
        val uri: Uri? = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        currentRingtoneKey?.let { key ->
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit { putString(key, uri?.toString().orEmpty()) }
            val fragment = supportFragmentManager
                .findFragmentById(R.id.preference_container) as? NotificationPrefsFragment
            fragment?.findPreference<RingtonePreference>(key)?.refreshSummary()
        }
        currentRingtoneKey = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentRingtoneKey = savedInstanceState?.getString(STATE_RINGTONE_KEY)
        setContentView(R.layout.activity_preference)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.preference_toolbar)?.let { toolbar ->
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preference_container, NotificationPrefsFragment())
                .commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_RINGTONE_KEY, currentRingtoneKey)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun launchRingtonePicker(key: String, intent: Intent) {
        currentRingtoneKey = key
        ringtonePicker.launch(intent)
    }
    class NotificationPrefsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_notification, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference is RingtonePreference) {
                val act = activity as? NotificationPrefs ?: return super.onPreferenceTreeClick(preference)
                val currentUriStr = preference.sharedPreferences?.getString(preference.key, null)
                val currentUri = currentUriStr?.takeIf(String::isNotEmpty)?.toUri()
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                }
                act.launchRingtonePicker(preference.key, intent)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}
