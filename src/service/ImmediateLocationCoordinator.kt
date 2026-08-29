package org.aprsdroid.app.service

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.aprsdroid.app.location.FixedPosition
import org.aprsdroid.app.location.LocationSource

/**
 * Handles the immediate-location path used by AprsService single-shot actions.
 *
 * Packet formatting/transmission deliberately stays outside this class. The
 * coordinator only chooses/acquires a Location and reports it through onLocation.
 */
internal class ImmediateLocationCoordinator(
    private val locationManagerProvider: () -> LocationManager?,
    private val handler: Handler,
    private val onLocation: (Location) -> Unit,
    private val logTag: String,
    private val mainLooper: Looper = Looper.getMainLooper(),
) {
    fun trigger(locationSource: LocationSource) {
        try {
            if (locationSource is FixedPosition) {
                // Preserve the existing manual-position behavior and side effects.
                locationSource.start(true)
                return
            }

            val locationManager = locationManagerProvider()
            val bestLocation = newestByTimestamp(readCachedLocations(locationManager)) { it.time }
            if (bestLocation != null) {
                Log.i(logTag, "triggerImmediateLocation: posting best known location: $bestLocation")
                onLocation(bestLocation)
                return
            }

            Log.w(logTag, "triggerImmediateLocation: no cached location, requesting immediate update")
            if (locationManager != null) {
                requestSingleUpdate(locationManager)
            }
        } catch (e: Throwable) {
            Log.e(logTag, "triggerImmediateLocation error: $e")
        }
    }

    private fun readCachedLocations(locationManager: LocationManager?): List<Location> {
        if (locationManager == null) return emptyList()

        val cached = ArrayList<Location>(CACHED_PROVIDERS.size)
        for (provider in CACHED_PROVIDERS) {
            try {
                locationManager.getLastKnownLocation(provider)?.let(cached::add)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return cached
    }

    private fun requestSingleUpdate(locationManager: LocationManager) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.i(logTag, "triggerImmediateLocation singleListener got location: $location")
                try {
                    locationManager.removeUpdates(this)
                } catch (_: Exception) {
                }
                onLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            override fun onProviderEnabled(provider: String) = Unit
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    listener,
                    mainLooper,
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    mainLooper,
                )
            }
            handler.postDelayed(
                {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                },
                SINGLE_UPDATE_TIMEOUT_MS,
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val SINGLE_UPDATE_TIMEOUT_MS = 15_000L

        val CACHED_PROVIDERS = arrayOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}
