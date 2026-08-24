package org.aprsdroid.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.maplibre.android.MapLibre

class APRSdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        ServiceNotifier.instance.setupChannels(this)
    }
}
