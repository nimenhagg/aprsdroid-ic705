package org.aprsdroid.app.service

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import org.aprsdroid.app.AprsBackend
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.location.LocationSource

data class ForegroundServiceWork(
    val microphone: Boolean = false,
    val location: Boolean = false,
    val connectedDevice: Boolean = false,
    val specialUse: Boolean = false,
)

object ForegroundServiceTypeResolver {
    internal fun determineWork(
        backendKey: String,
        protocol: String,
        locationSource: String,
        kenwoodGps: Boolean,
    ): ForegroundServiceWork {
        val usesLocation = locationSource == "smartbeaconing" ||
            locationSource == "periodic" ||
            (protocol == "kenwood" && kenwoodGps)
        val usesMicrophone = backendKey == "afsk"
        val usesConnectedDevice = backendKey == "bluetooth"
        val usesSpecialUse = backendKey == "ic705" ||
            (!usesMicrophone && !usesLocation && !usesConnectedDevice)

        return ForegroundServiceWork(
            microphone = usesMicrophone,
            location = usesLocation,
            connectedDevice = usesConnectedDevice,
            specialUse = usesSpecialUse,
        )
    }

    fun fallbackType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    fun resolve(service: AprsService): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0

        val prefs = service.prefs
        val work = determineWork(
            backendKey = AprsBackend.selectedBackendKey(prefs),
            protocol = prefs.getProto(),
            locationSource = prefs.getString("loc_source", LocationSource.DEFAULT_CONNTYPE),
            kenwoodGps = prefs.getBoolean("kenwood.gps", false),
        )

        var serviceType = 0
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            work.microphone &&
            ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

        if (
            work.location &&
            (
                ContextCompat.checkSelfPermission(service, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(service, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }

        if (work.connectedDevice && connectedDevicePrerequisiteSatisfied(service)) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && work.specialUse) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        // Preserve a valid Android 14+ fallback if an expected runtime prerequisite
        // disappeared between the permission check and service startup.
        if (serviceType == 0) {
            serviceType = fallbackType()
        }
        return serviceType
    }

    private fun connectedDevicePrerequisiteSatisfied(service: AprsService): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(service, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
