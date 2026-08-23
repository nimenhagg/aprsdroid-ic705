package org.aprsdroid.app

import android.content.Context
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class KenwoodProto(val service: AprsService, isStream: InputStream, osStream: OutputStream) : TncProto(isStream, null) {
    companion object {
        const val TAG = "APRSdroid.KenwoodProto"
    }

    private val br = BufferedReader(InputStreamReader(isStream))
    private val sinkhole = LocationSinkhole()
    private val locMan = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val output: OutputStreamWriter? = OutputStreamWriter(osStream)
    private val executor = Executors.newSingleThreadExecutor()

    private var nmeaListener: OnNmeaMessageListener? = null

    init {
        if (service.prefs.getBoolean("kenwood.gps", false)) {
            Handler(Looper.getMainLooper()).post {
                try {
                    locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, sinkhole)
                    val listener = OnNmeaMessageListener { nmea, timestamp ->
                        onNmeaReceived(timestamp, nmea)
                    }
                    nmeaListener = listener
                    locMan.addNmeaListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "GPS listener registration error: $e")
                }
            }
        }
    }

    fun wpl2aprs(line: String): String {
        val s = line.split(Regex("[,*]"))
        return when (s.getOrNull(0)) {
            "\$PKWDWPL" -> {
                val lat = "${s.getOrElse(3){""}}${s.getOrElse(4){""}}"
                val lon = "${s.getOrElse(5){""}}${s.getOrElse(6){""}}"
                val call = s.getOrElse(11){""}.trim()
                val sym = s.getOrElse(12){"//"}
                val s0 = if (sym.isNotEmpty()) sym[0] else '/'
                val s1 = if (sym.length > 1) sym[1] else '/'
                "$call>APRS:!$lat$s0$lon$s1"
            }
            "\$GPWPL" -> {
                val lat = "${s.getOrElse(1){""}}${s.getOrElse(2){""}}"
                val lon = "${s.getOrElse(3){""}}${s.getOrElse(4){""}}"
                val call = s.getOrElse(5){""}.trim()
                "$call>APRS:!$lat/$lon/"
            }
            else -> line.replaceFirst(Regex("^(cmd:)+"), "")
        }
    }

    fun yaesu2aprs(line1: String, line2: String): String {
        Log.d(TAG, "line1: $line1")
        Log.d(TAG, "line2: $line2")
        return line1.replace(Regex(" \\[[0-9/: ]+\\] <UI ?[A-Z]?>:$"), ":") + line2
    }

    override fun readPacket(): String {
        var line: String? = br.readLine()
        while (line.isNullOrEmpty()) {
            line = br.readLine() ?: throw java.io.IOException("Kenwood EOF")
        }
        if (line.contains("] <UI") && line.endsWith(">:")) {
            val next = br.readLine() ?: ""
            return yaesu2aprs(line, next)
        }
        Log.d(TAG, "got $line")
        return wpl2aprs(line)
    }

    override fun writePacket(p: APRSPacket) {
        // No-op
    }

    fun onNmeaReceived(timestamp: Long, nmea: String) {
        if (output != null && (nmea.startsWith("\$GPGGA") || nmea.startsWith("\$GPRMC"))) {
            Log.d(TAG, "NMEA >>> $nmea")
            try {
                executor.submit {
                    try {
                        output.write(nmea)
                        output.flush()
                    } catch (e: Exception) {
                        Log.e(TAG, "error sending NMEA to Kenwood: $e")
                    }
                }
                if (service.prefs.getBoolean("kenwood.gps_debug", false)) {
                    service.postAddPost(StorageDatabase.Companion.Post.TYPE_TX, R.string.p_conn_kwd, nmea.trim())
                }
            } catch (e: Exception) {
                Log.e(TAG, "error queueing NMEA: $e")
            }
        } else {
            Log.d(TAG, "NMEA --- $nmea")
        }
    }

    class LocationSinkhole : LocationListener {
        override fun onLocationChanged(location: Location) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
    }

    override fun stop() {
        try {
            locMan.removeUpdates(sinkhole)
            nmeaListener?.let { locMan.removeNmeaListener(it) }
            executor.shutdownNow()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping KenwoodProto: $e")
        }
        super.stop()
    }
}
