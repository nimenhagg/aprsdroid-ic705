package org.aprsdroid.app.location

import android.Manifest
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper

abstract class LocationSource {
    abstract fun start(singleShot: Boolean): String
    abstract fun stop()

    companion object {
        const val DEFAULT_CONNTYPE = "smartbeaconing"

        @JvmStatic
        fun DEFAULT_CONNTYPE(): String = DEFAULT_CONNTYPE

        @JvmStatic
        fun instanciateLocation(service: AprsService, prefs: PrefsWrapper): LocationSource {
            return when (prefs.getString("loc_source", DEFAULT_CONNTYPE)) {
                "smartbeaconing" -> SmartBeaconing(service, prefs)
                "periodic" -> PeriodicGPS(service, prefs)
                "manual" -> FixedPosition(service, prefs)
                else -> SmartBeaconing(service, prefs)
            }
        }

        @JvmStatic
        fun getPermissions(prefs: PrefsWrapper): Set<String> {
            return when (prefs.getString("loc_source", DEFAULT_CONNTYPE)) {
                "smartbeaconing", "periodic" -> setOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                "manual" -> emptySet()
                else -> emptySet()
            }
        }
    }
}
