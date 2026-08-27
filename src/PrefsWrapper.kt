package org.aprsdroid.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.location.PeriodicGPS

class PrefsWrapper(@JvmField val context: Context) {
    companion object {
        @JvmStatic
        fun defaultSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE,
            )
        }
    }

    @JvmField
    val prefs: SharedPreferences = defaultSharedPreferences(context)

    init {
        val currentStatus = prefs.getString("status", null)
        if (
            currentStatus == null ||
            currentStatus == "https://aprsdroid.org/" ||
            currentStatus == "https://aprsdroid.org" ||
            currentStatus == "APRSDroid Mod"
        ) {
            prefs.edit { putString("status", "APRSdroid Mod") }
        }
        val currentTcpServer = prefs.getString("tcp.server", null)
        if (
            currentTcpServer == null ||
            currentTcpServer == "euro.aprs2.net" ||
            currentTcpServer == "rotate.aprs.net" ||
            currentTcpServer == "rotate.aprs2.net"
        ) {
            prefs.edit { putString("tcp.server", "china.aprs2.net") }
        }
    }

    fun getString(key: String, defValue: String?): String {
        return prefs.getString(key, defValue) ?: (defValue ?: "")
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    fun getStringInt(key: String, defValue: Int): Int {
        return try {
            prefs.getString(key, null)?.trim()?.toInt() ?: defValue
        } catch (_: Throwable) {
            defValue
        }
    }

    fun getStringFloat(key: String, defValue: Float): Float {
        return try {
            prefs.getString(key, null)?.trim()?.toFloat() ?: defValue
        } catch (_: Throwable) {
            defValue
        }
    }

    fun getCallsign(): String {
        return (prefs.getString("callsign", "") ?: "").trim().uppercase(Locale.US)
    }

    fun getPasscode(): String {
        val p = prefs.getString("passcode", "") ?: ""
        return if (p.isEmpty()) "-1" else p
    }

    fun getSsid(): String = getString("ssid", "10")

    fun getCallSsid(): String = AprsPacket.formatCallSsid(getCallsign(), getSsid())

    fun toggleBoolean(name: String, default: Boolean): Boolean {
        val newVal = !prefs.getBoolean(name, default)
        Log.d("toggleBoolean", "$name=$newVal")
        return setBoolean(name, newVal)
    }

    fun setBoolean(name: String, newVal: Boolean): Boolean {
        prefs.edit { putBoolean(name, newVal) }
        return newVal
    }

    fun set(name: String, newVal: String): String {
        prefs.edit { putString(name, newVal) }
        return newVal
    }

    fun set(name: String, newVal: Boolean): Boolean {
        return setBoolean(name, newVal)
    }

    fun getShowObjects(): Boolean = prefs.getBoolean("show_objects", true)
    fun getShowSatellite(): Boolean = prefs.getBoolean("show_satellite", false)
    fun getSendBatteryAprsIs(): Boolean = prefs.getBoolean("send_battery_aprsis", false)
    fun getStationTapAction(): String = getString("station_tap_action", "message")

    fun getShowAge(): Long = getStringInt("show_age", 30) * 60L * 1000L

    fun getListItemIndex(pref: String, default: String, values: Int): Int {
        val prefVal = getString(pref, default)
        val array = context.resources.getStringArray(values)
        Log.d("getLII", prefVal)
        Log.d("getLII", "values: " + array.joinToString(" "))
        return array.indexOf(prefVal)
    }

    fun getListItemName(pref: String, default: String, values: Int, names: Int): String {
        val id = getListItemIndex(pref, default, values)
        Log.d("getLIN", "id is $id")
        return if (id < 0) {
            "<not in list>"
        } else {
            context.resources.getStringArray(names)[id]
        }
    }

    fun getLocationSourceName(): String {
        return when (getString("loc_source", LocationSource.DEFAULT_CONNTYPE())) {
            "smartbeaconing" -> context.getString(R.string.setting_location_source_smart_option)
            "periodic" -> context.getString(R.string.setting_location_source_periodic_option)
            "manual" -> context.getString(R.string.setting_location_source_manual_option)
            else -> context.getString(R.string.setting_location_source)
        }
    }

    fun getBackendName(): String {
        val proto = getString("proto", AprsBackend.DEFAULT_PROTO())
        val protoName = when (proto) {
            "aprsis" -> context.getString(R.string.setting_proto_aprsis)
            "ic705" -> context.getString(R.string.setting_proto_ic705)
            "afsk" -> context.getString(R.string.setting_proto_afsk)
            "kiss" -> context.getString(R.string.setting_proto_kiss)
            "kenwood" -> context.getString(R.string.setting_proto_kenwood)
            "tnc2" -> context.getString(R.string.setting_proto_tnc2)
            else -> proto
        }
        return when (AprsBackend.defaultProtoInfo(this).link()) {
            "aprsis" -> {
                val linkName = when (getString("aprsis", AprsBackend.DEFAULT_CONNTYPE())) {
                    "tcp" -> context.getString(R.string.setting_aprsis_tcp)
                    "udp" -> context.getString(R.string.setting_aprsis_udp)
                    "http" -> context.getString(R.string.setting_aprsis_http)
                    else -> getString("aprsis", AprsBackend.DEFAULT_CONNTYPE())
                }
                "$protoName · $linkName"
            }
            "link" -> {
                val linkName = when (getString("link", AprsBackend.DEFAULT_CONNTYPE())) {
                    "bluetooth" -> context.getString(R.string.setting_tnc_bluetooth)
                    "tcpip" -> context.getString(R.string.setting_tnc_tcp)
                    "usb" -> context.getString(R.string.setting_tnc_usb)
                    else -> getString("link", AprsBackend.DEFAULT_CONNTYPE())
                }
                "$protoName · $linkName"
            }
            else -> protoName
        }
    }

    fun getVersion(): String {
        val version = BuildConfig.VERSION_NAME.removePrefix("Mod-v").substringBefore(" ")
        return "APRSdroidMod $version"
    }

    fun getLoginString(): String {
        return AprsPacket.formatLogin(getCallsign(), getSsid(), getPasscode(), getVersion())
    }

    fun getFilterString(service: AprsService): String {
        val filterdist = getStringInt("tcp.filterdist", 50)
        val userfilter = getString("tcp.filter", "")
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(
                service,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    service,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        val lastloc = if (!hasLocationPermission) {
            ""
        } else {
            try {
                val locMan = service.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val provider = PeriodicGPS.bestProvider(locMan)
                if (locMan != null && provider != null) {
                    AprsPacket.formatRangeFilter(locMan.getLastKnownLocation(provider), filterdist)
                } else {
                    ""
                }
            } catch (_: Throwable) {
                ""
            }
        }
        return if (filterdist == 0) {
            String.format(Locale.US, " filter %s %s", userfilter, lastloc)
        } else {
            String.format(Locale.US, " filter m/%d %s %s", filterdist, userfilter, lastloc)
        }
    }

    fun getProto(): String = getString("proto", "aprsis")
    fun getAfskHQ(): Boolean = true
    fun getAfskBluetooth(): Boolean = getBoolean("afsk.btsco", false)
    fun getAfskOutput(): Int = if (getAfskBluetooth()) {
        AudioManager.STREAM_VOICE_CALL
    } else {
        getStringInt("afsk.output", 0)
    }

    fun getDigiPathPresets(): Set<String> {
        return prefs.getStringSet("digi_path_user_presets", emptySet())?.toSet() ?: emptySet()
    }

    fun saveDigiPathPresets(presets: Set<String>) {
        prefs.edit { putStringSet("digi_path_user_presets", presets) }
    }
}
