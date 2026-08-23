package org.aprsdroid.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class APRSdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        ServiceNotifier.instance.setupChannels(this)
    }
}
