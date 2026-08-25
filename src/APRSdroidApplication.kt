package org.aprsdroid.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class APRSdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
        ServiceNotifier.instance.setupChannels(this)
    }
}
