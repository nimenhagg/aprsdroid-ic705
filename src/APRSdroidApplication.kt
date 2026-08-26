package org.aprsdroid.app

import android.app.Application
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.diagnostic.NetworkEventLogger

class APRSdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        NetworkEventLogger.start(this)
    }
}
