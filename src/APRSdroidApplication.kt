package org.aprsdroid.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import okhttp3.OkHttpClient
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.diagnostic.NetworkEventLogger
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class APRSdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        NetworkEventLogger.start(this)
        MapLibre.getInstance(this)
        val releaseVersion = BuildConfig.VERSION_NAME.substringBefore(' ')
        val mapUserAgent = "APRSdroid-IC705/$releaseVersion MapLibre/${BuildConfig.MAPLIBRE_VERSION} " +
            "(+https://github.com/nimenhagg/aprsdroid-ic705)"
        val mapHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", mapUserAgent)
                    .build()
                chain.proceed(request)
            }
            .build()
        HttpRequestUtil.setOkHttpClient(mapHttpClient)
        DynamicColors.applyToActivitiesIfAvailable(this)

        Thread(
            {
                try {
                    ServiceNotifier.instance.setupChannels(applicationContext)
                } catch (_: Exception) {
                    // ServiceNotifier.start() will synchronously retry before a foreground notification is posted.
                }
            },
            "notification-channels-init",
        ).apply {
            isDaemon = true
            start()
        }
    }
}
