package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import de.duenndns.RingtonePreference

class NotificationPrefs : AppCompatActivity() {

    companion object {
        const val REQ_RINGTONE = 2001
    }

    var currentRingtoneKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_RINGTONE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            currentRingtoneKey?.let { key ->
                val str = uri?.toString() ?: ""
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(key, str)
                    .apply()
                val fragment = supportFragmentManager.findFragmentById(android.R.id.content) as? NotificationPrefsFragment
                fragment?.findPreference<Preference>(key)?.let { pref ->
                    val cur = pref.summary
                    pref.summary = cur
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    class NotificationPrefsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_notification, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference is RingtonePreference) {
                val act = activity as? NotificationPrefs ?: return super.onPreferenceTreeClick(preference)
                act.currentRingtoneKey = preference.key
                val currentUriStr = preference.sharedPreferences?.getString(preference.key, null)
                val currentUri = if (currentUriStr != null && currentUriStr.isNotEmpty()) Uri.parse(currentUriStr) else null
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                }
                @Suppress("DEPRECATION")
                act.startActivityForResult(intent, REQ_RINGTONE)
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}
