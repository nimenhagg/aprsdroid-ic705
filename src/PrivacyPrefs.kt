package org.aprsdroid.app

import android.os.Bundle
import android.preference.PreferenceActivity

class PrivacyPrefs : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        addPreferencesFromResource(R.xml.preferences_privacy)
    }
}
