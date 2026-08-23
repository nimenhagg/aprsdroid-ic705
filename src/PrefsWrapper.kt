package org.aprsdroid.app

import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.location.PeriodicGPS

import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.media.AudioManager
import android.preference.PreferenceManager
import android.util.Log
import java.util.Locale

class PrefsWrapper(@JvmField val context: Context) {
    @JvmField
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        val currentStatus = prefs.getString("status", null)
        if (currentStatus == null || currentStatus == "https://aprsdroid.org/" || currentStatus == "https://aprsdroid.org") {
            prefs.edit().putString("status", "APRSDroid Mod").apply()
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
        prefs.edit().putBoolean(name, newVal).commit()
        return newVal
    }

    fun set(name: String, newVal: String): String {
        prefs.edit().putString(name, newVal).commit()
        return newVal
    }

    fun getShowObjects(): Boolean = prefs.getBoolean("show_objects", true)
    fun getShowSatellite(): Boolean = prefs.getBoolean("show_satellite", false)

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
        return getListItemName("loc_source", LocationSource.DEFAULT_CONNTYPE(),
            R.array.p_locsource_ev, R.array.p_locsource_e)
    }

    fun getBackendName(): String {
        val proto = getListItemName("proto", AprsBackend.DEFAULT_PROTO(),
            R.array.p_conntype_ev, R.array.p_conntype_e)
        val link = AprsBackend.defaultProtoInfo(this).link()
        return when (link) {
            "aprsis" -> String.format(Locale.US, "%s, %s", proto,
                getListItemName(link, AprsBackend.DEFAULT_CONNTYPE(), R.array.p_aprsis_ev, R.array.p_aprsis_e))
            "link" -> String.format(Locale.US, "%s, %s", proto,
                getListItemName(link, AprsBackend.DEFAULT_CONNTYPE(), R.array.p_link_ev, R.array.p_link_e))
            else -> proto
        }
    }

    fun getVersion(): String {
        return context.getString(R.string.build_version).split(" ").take(2).joinToString(" ")
    }

    fun getLoginString(): String {
        return AprsPacket.formatLogin(getCallsign(), getSsid(), getPasscode(), getVersion())
    }

    fun getFilterString(service: AprsService): String {
        val filterdist = getStringInt("tcp.filterdist", 50)
        val userfilter = getString("tcp.filter", "")
        val lastloc = try {
            val locMan = service.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val provider = PeriodicGPS.bestProvider(locMan)
            if (locMan != null && provider != null) {
                AprsPacket.formatRangeFilter(locMan.getLastKnownLocation(provider), filterdist)
            } else ""
        } catch (_: Throwable) {
            ""
        }
        return if (filterdist == 0) {
            String.format(Locale.US, " filter %s %s", userfilter, lastloc)
        } else {
            String.format(Locale.US, " filter m/%d %s %s", filterdist, userfilter, lastloc)
        }
    }

    fun getProto(): String = getString("proto", "aprsis")
    fun getAfskHQ(): Boolean = getBoolean("afsk.hqdemod", true)
    fun getAfskBluetooth(): Boolean = getBoolean("afsk.btsco", false) && getAfskHQ()
    fun getAfskOutput(): Int = if (getAfskBluetooth()) AudioManager.STREAM_VOICE_CALL else getStringInt("afsk.output", 0)
}
