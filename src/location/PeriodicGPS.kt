package org.aprsdroid.app.location

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R

class PeriodicGPS(
    private val service: AprsService,
    private val prefs: PrefsWrapper
) : LocationSource(), LocationListener {

    companion object {
        const val TAG = "APRSdroid.PeriodicGPS"
        const val ALARM_ACTION = "org.aprsdroid.app.PeriodicGPS.ALARM"
        const val GPS_SEARCH_TIME = 90L

        @JvmStatic
        fun bestProvider(locMan: LocationManager?): String? {
            if (locMan == null) return null
            val providers = locMan.getProviders(true)
            return when {
                providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                providers.contains(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
                else -> null
            }
        }
    }

    private val intent = Intent(ALARM_ACTION).setPackage(service.packageName)
    private val pendingIntent = PendingIntent.getBroadcast(
        service, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            Log.d(TAG, "onReceive: $i")
            postRefresh()
        }
    }

    private val locMan = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val manager = service.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var isSingleShot = false
    private var started = false
    private var gpsStartedTime = 0L

    @SuppressLint("MissingPermission", "WrongConstant")
    override fun start(singleShot: Boolean): String {
        isSingleShot = singleShot
        ContextCompat.registerReceiver(
            service, receiver,
            IntentFilter(ALARM_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (!started) {
            startGPS()
        }
        return service.getString(R.string.p_source_periodic)
    }

    override fun stop() {
        manager.cancel(pendingIntent)
        try { service.unregisterReceiver(receiver) } catch (_: Exception) {}
        stopGPS()
    }

    @SuppressLint("MissingPermission")
    private fun startGPS() {
        val provider = bestProvider(locMan)
        if (provider != null) {
            try {
                locMan.requestLocationUpdates(provider, 0L, 0f, this)
                started = true
                gpsStartedTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "requestLocationUpdates failed: $e")
                service.postAbort(service.getString(R.string.service_sm_no_gps) + "\n" + e.message)
            }
        } else {
            service.postAbort(service.getString(R.string.service_sm_no_gps))
        }
    }

    private fun stopGPS() {
        if (started) {
            try { locMan.removeUpdates(this) } catch (_: Exception) {}
            started = false
        }
    }

    private fun postRefresh() {
        val updInt = prefs.getStringInt("interval", 10)
        Log.d(TAG, "postRefresh(): $updInt min")
        startGPS()
    }

    override fun onLocationChanged(location: Location) {
        val minAcc = prefs.getStringInt("priv_minacc", 100)
        val maxWait = prefs.getStringInt("priv_maxwait", 30)
        val elapsed = (System.currentTimeMillis() - gpsStartedTime) / 1000L

        if (location.accuracy <= minAcc || elapsed >= maxWait || isSingleShot) {
            stopGPS()
            service.postLocation(location)

            if (!isSingleShot) {
                val updInt = prefs.getStringInt("interval", 10)
                manager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + updInt * 60L * 1000L,
                    pendingIntent
                )
            }
        }
    }

    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
