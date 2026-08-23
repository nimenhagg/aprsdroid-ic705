package org.aprsdroid.app.location

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R

class FixedPosition(
    private val service: AprsService,
    private val prefs: PrefsWrapper
) : LocationSource() {

    companion object {
        const val TAG = "APRSdroid.FixedPosition"
        const val ALARM_ACTION = "org.aprsdroid.app.FixedPosition.ALARM"
    }

    private val intent = Intent(ALARM_ACTION).setPackage(service.packageName)
    private val pendingIntent = PendingIntent.getBroadcast(
        service, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            Log.d(TAG, "onReceive: $i")
            postPosition()
            postRefresh()
        }
    }

    private val manager = service.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private var alreadyRunning = false

    @SuppressLint("WrongConstant")
    override fun start(singleShot: Boolean): String {
        stop()
        ContextCompat.registerReceiver(
            service, receiver,
            IntentFilter(ALARM_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        val periodic = prefs.getBoolean("periodicposition", true)
        Log.d(TAG, "start: periodic=$periodic single=$singleShot")

        if (singleShot || alreadyRunning || periodic) {
            postPosition()
        }

        alreadyRunning = true
        if (periodic && !singleShot) {
            postRefresh()
        }

        return service.getString(R.string.p_source_manual)
    }

    override fun stop() {
        manager.cancel(pendingIntent)
        if (alreadyRunning) {
            try { service.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
        alreadyRunning = false
    }

    fun postRefresh() {
        val updInt = prefs.getStringInt("interval", 10)
        Log.d(TAG, "postRefresh(): $updInt min")
        manager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + updInt * 60L * 1000L,
            pendingIntent
        )
    }

    fun postPosition() {
        val location = Location("manual")
        location.latitude = prefs.getStringFloat("manual_lat", 0f).toDouble()
        location.longitude = prefs.getStringFloat("manual_lon", 0f).toDouble()
        service.postLocation(location)
    }
}
