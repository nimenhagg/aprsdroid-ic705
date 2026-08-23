package org.aprsdroid.app

import android.os.Bundle
import android.preference.PreferenceActivity

class NotificationPrefs : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        addPreferencesFromResource(R.xml.preferences_notification)
    }
}
