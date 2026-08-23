package org.aprsdroid.app

import android.os.Bundle
import android.preference.PreferenceActivity
import org.aprsdroid.app.location.LocationSource

class LocationPrefs : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsWrapper(this)
        val prefRes = LocationSource.instanciatePrefsAct(prefs)
        if (prefRes != 0) {
            @Suppress("DEPRECATION")
            addPreferencesFromResource(prefRes)
        }
    }
}
