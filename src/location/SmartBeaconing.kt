package org.aprsdroid.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SmartBeaconing(
    private val service: AprsService,
    private val prefs: PrefsWrapper
) : LocationSource(), LocationListener {

    companion object {
        const val TAG = "APRSdroid.SmartBeaconing"
    }

    private var lastLoc: Location? = null
    private val locMan = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var started = false
    private var isSingleShot = false

    @SuppressLint("MissingPermission")
    override fun start(singleShot: Boolean): String {
        isSingleShot = singleShot
        if (singleShot) lastLoc = null
        if (!started) {
            try {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
                started = true
            } catch (e: Exception) {
                service.postAbort(service.getString(R.string.service_sm_no_gps) + "\n" + e.message)
            }
        }
        return service.getString(R.string.p_source_smart)
    }

    override fun stop() {
        if (started) {
            try { locMan.removeUpdates(this) } catch (_: Exception) {}
            started = false
        }
    }

    fun smartBeaconSpeedRate(speed: Float): Int {
        val fastSpeed = prefs.getStringInt("sb.fastspeed", 100) / 3.6f
        val fastRate = prefs.getStringInt("sb.fastrate", 60)
        val slowSpeed = prefs.getStringInt("sb.slowspeed", 5) / 3.6f
        val slowRate = prefs.getStringInt("sb.slowrate", 1200)

        return when {
            speed <= slowSpeed -> slowRate
            speed >= fastSpeed -> fastRate
            else -> (fastRate + (slowRate - fastRate) * (fastSpeed - speed) / (fastSpeed - slowSpeed)).toInt()
        }
    }

    fun getBearingAngle(alpha: Float, beta: Float): Float {
        val delta = abs(alpha - beta) % 360f
        return if (delta <= 180f) delta else (360f - delta)
    }

    fun getSpeed(location: Location): Float {
        val prev = lastLoc ?: return location.speed
        val dist = location.distanceTo(prev)
        val tDiff = location.time - prev.time
        val calcSpeed = if (tDiff > 0) dist * 1000f / tDiff else 0f
        return max(max(calcSpeed, location.speed), prev.speed)
    }

    fun smartBeaconCornerPeg(location: Location): Boolean {
        val prev = lastLoc ?: return false
        val turnTime = prefs.getStringInt("sb.turntime", 15)
        val turnMin = prefs.getStringInt("sb.turnmin", 10)
        val turnSlope = prefs.getStringInt("sb.turnslope", 240).toDouble()

        val speed = location.speed
        val tDiff = location.time - prev.time
        val turn = getBearingAngle(location.bearing, prev.bearing)

        if (!location.hasBearing() || speed == 0f) return false
        if (!prev.hasBearing()) return (tDiff / 1000L >= turnTime)

        val threshold = turnMin + turnSlope / (speed * 2.23693629)
        Log.d(TAG, String.format(Locale.US, "smartBeaconCornerPeg: %1.0f < %1.0f %d/%d", turn, threshold, tDiff / 1000L, turnTime))
        return (tDiff / 1000L >= turnTime && turn > threshold)
    }

    fun smartBeaconCheck(location: Location): Boolean {
        val prev = lastLoc ?: return true
        if (smartBeaconCornerPeg(location)) return true

        val dist = location.distanceTo(prev)
        val tDiff = location.time - prev.time
        val speed = getSpeed(location)
        val speedRate = smartBeaconSpeedRate(speed)

        Log.d(TAG, String.format(Locale.US, "smartBeaconCheck: %1.0fm, %1.2fm/s -> %d/%ds - %s", dist, speed, tDiff / 1000L, speedRate, (tDiff / 1000L >= speedRate)))
        return (tDiff / 1000L >= speedRate)
    }

    override fun onLocationChanged(location: Location) {
        if (isSingleShot || smartBeaconCheck(location)) {
            isSingleShot = false
            postLocation(location)
        }
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "onProviderDisabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            Toast.makeText(service, R.string.service_sm_no_gps, Toast.LENGTH_LONG).show()
        }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "onProviderEnabled: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "onStatusChanged: $provider")
    }

    fun postLocation(location: Location) {
        lastLoc = location
        service.postLocation(location)
    }
}
