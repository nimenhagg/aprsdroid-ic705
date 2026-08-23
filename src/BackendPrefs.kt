package org.aprsdroid.app

import android.os.Bundle
import android.preference.PreferenceActivity

class BackendPrefs : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsWrapper(this)
        val prefRes = AprsBackend.prefxml_backend(prefs)
        if (prefRes != 0) {
            @Suppress("DEPRECATION")
            addPreferencesFromResource(prefRes)
        }
    }
}
