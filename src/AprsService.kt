package org.aprsdroid.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.APRSTypes
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Digipeater
import net.ab0oo.aprs.parser.InformationField
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.ObjectPacket
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.Position as AprsPosition
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.data.preferences.AprsServiceSettings
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.service.AprsBackendServiceAdapter
import org.aprsdroid.app.service.BackendLifecycleCoordinator
import org.aprsdroid.app.service.ImmediateLocationCoordinator
import org.aprsdroid.app.service.PacketSendCoordinator
import java.util.Locale

class AprsService : Service() {

    companion object {
        const val PACKAGE = "org.aprsdroid.app"

        // Action intents
        const val SERVICE = "$PACKAGE.SERVICE"
        const val SERVICE_ONCE = "$PACKAGE.ONCE"
        const val SERVICE_SEND_PACKET = "$PACKAGE.SEND_PACKET"
        const val SERVICE_FREQUENCY = "$PACKAGE.FREQUENCY"
        const val SERVICE_STOP = "$PACKAGE.SERVICE_STOP"

        // Event intents
        const val SERVICE_STARTED = "$PACKAGE.SERVICE_STARTED"
        const val SERVICE_STOPPED = "$PACKAGE.SERVICE_STOPPED"
        const val POSITION = "$PACKAGE.POSITION"
        const val MICLEVEL = "$PACKAGE.MICLEVEL"
        const val LINK_ON = "$PACKAGE.LINK_ON"
        const val LINK_OFF = "$PACKAGE.LINK_OFF"
        const val LINK_INFO = "$PACKAGE.LINK_INFO"

        // Broadcast actions
        const val UPDATE = "$PACKAGE.UPDATE"
        const val MESSAGE = "$PACKAGE.MESSAGE"
        const val MESSAGETX = "$PACKAGE.MESSAGETX"

        // Broadcast intent extras
        const val API_VERSION = "api_version"
        const val CALLSIGN = "callsign"
        const val TYPE = "type"
        const val STATUS = "status"
        const val LOCATION = "location"
        const val SOURCE = "source"
        const val PACKET = "packet"
        const val DEST = "dest"
        const val BODY = "body"

        const val API_VERSION_CODE = 1

        @JvmField
        var running = false
        @JvmField
        var link_error = 0

        @JvmStatic
        fun intent(ctx: Context, action: String): Intent {
            return Intent(action, null, ctx, AprsService::class.java)
        }

        @JvmStatic
        fun privateIntent(ctx: Context, action: String): Intent {
            return Intent(action).setPackage(ctx.packageName)
        }
    }

    val TAG = "APRSdroid.Service"

    val APP_VERSION: String by lazy {
        val ver = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        val digits = ver.filter { it.isDigit() }.take(2)
        "APDR$digits"
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val serviceSettings: AprsServiceSettings by lazy { AprsServiceSettings(prefs) }
    private val backendCoordinator: BackendLifecycleCoordinator by lazy {
        BackendLifecycleCoordinator {
            AprsBackendServiceAdapter(AprsBackend.instanciateUploader(this, prefs))
        }
    }

    @JvmField
    val handler = Handler(Looper.getMainLooper())

    private val immediateLocationCoordinator: ImmediateLocationCoordinator by lazy {
        ImmediateLocationCoordinator(
            locationManagerProvider = {
                getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            },
            handler = handler,
            onLocation = { location -> postLocation(location) },
            logTag = TAG,
        )
    }

    private val packetSendCoordinator: PacketSendCoordinator by lazy {
        PacketSendCoordinator(
            updateBackend = { packet -> backendCoordinator.update(packet) },
            onTxPost = { status, packetText ->
                addPost(StorageDatabase.Companion.Post.TYPE_TX, status, packetText)
            },
            onErrorPost = { errorText ->
                addPost(StorageDatabase.Companion.Post.TYPE_ERROR, "Error", errorText)
            },
            postToMain = { task -> handler.post { task() } },
            onFinished = { status -> sendPacketFinished(status) },
        )
    }

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    val msgService: MessageService by lazy { MessageService(this) }
    val locSource: LocationSource by lazy { LocationSource.instanciateLocation(this, prefs) }
    val msgNotifier by lazy { msgService.createMessageNotifier() }

    var singleShot = false

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $i, $flags, $startId")
        if (i != null) handleStart(i)
        return START_REDELIVER_INTENT
    }

    @SuppressLint("WrongConstant")
    fun handleStart(i: Intent) {
        when (i.action) {
            SERVICE_STOP -> {
                serviceSettings.serviceRunning = false
                if (running) stopSelf()
                return
            }
            SERVICE_SEND_PACKET -> {
                if (!running) {
                    Log.d(TAG, "SEND_PACKET ignored, service not running.")
                    return
                }
                val dataField = i.getStringExtra("data") ?: run {
                    Log.d(TAG, "SEND_PACKET ignored, data extra is empty.")
                    return
                }
                val p = Parser.parseBody(serviceSettings.callSsid, APP_VERSION, null, dataField)
                sendPacket(p)
                return
            }
            SERVICE_FREQUENCY -> {
                val dataField = i.getStringExtra("frequency") ?: run {
                    Log.d(TAG, "FREQUENCY ignored, 'frequency' extra is empty.")
                    return
                }
                val freqCleaned = dataField.replace("MHz", "").trim()
                val freq = try { freqCleaned.toFloat(); freqCleaned } catch (_: Throwable) { "" }
                if (serviceSettings.frequencyText != freq) {
                    serviceSettings.frequencyText = freq
                    if (!running) return
                } else return
            }
        }

        val isOnce = (i.action == SERVICE_ONCE)
        val toastString = if (isOnce) {
            if (!running) {
                singleShot = true
            }
            getString(R.string.service_once)
        } else {
            getString(R.string.service_start)
        }

        showToast(String.format(toastString, serviceSettings.locationSourceName, serviceSettings.backendName))

        val callssid = serviceSettings.callSsid
        ServiceNotifier.instance.start(this, callssid)

        if (!running) {
            running = true
            startPoster()
            ContextCompat.registerReceiver(
                this, msgNotifier,
                IntentFilter(MESSAGETX),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            onPosterStarted()
            if (isOnce) {
                triggerImmediateLocation()
            }
        }
    }

    fun startPoster() {
        if (backendCoordinator.replaceAndStart()) {
            onPosterStarted()
        }
    }

    fun triggerImmediateLocation() {
        immediateLocationCoordinator.trigger(locSource)
    }

    fun onPosterStarted() {
        Log.d(TAG, "onPosterStarted")
        val locInfo = locSource.start(singleShot)
        val callssid = serviceSettings.callSsid
        val message = "$callssid: $locInfo"
        ServiceNotifier.instance.start(this, message)

        msgService.sendPendingMessages()

        sendBroadcast(
            privateIntent(this, SERVICE_STARTED)
                .putExtra(API_VERSION, API_VERSION_CODE)
                .putExtra(CALLSIGN, callssid)
        )

        if (!singleShot) {
            serviceSettings.serviceRunning = true
        } else {
            triggerImmediateLocation()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onUnbind(i: Intent?): Boolean = false

    fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        addPost(StorageDatabase.Companion.Post.TYPE_INFO, null, msg)
    }

    override fun onDestroy() {
        running = false
        link_error = 0
        if (backendCoordinator.stop()) {
            showToast(getString(R.string.service_stop))
            sendBroadcast(privateIntent(this, SERVICE_STOPPED))
        }
        msgService.stop()
        locSource.stop()
        try { unregisterReceiver(msgNotifier) } catch (_: Exception) {}
        ServiceNotifier.instance.stop(this)
        packetSendCoordinator.shutdownNow()
    }

    fun newPacket(payload: InformationField): APRSPacket {
        val digipath = serviceSettings.digipeaterPath
        return APRSPacket(serviceSettings.callSsid, APP_VERSION, Digipeater.parseList(digipath, true), payload)
    }

    private fun batteryCommentToken(): String? {
        if (!serviceSettings.includeBatteryOnAprsIs) return null
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val percentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (percentage in 0..100) "BAT:${percentage}%" else null
    }

    private fun buildPositionComment(protocolFields: String, status: String): String {
        val maxLength = 43
        val base = StringBuilder(protocolFields.take(maxLength))
        val userStatus = status.trim()
        if (userStatus.isNotEmpty() && base.length < maxLength) {
            if (base.isNotEmpty()) base.append(' ')
            val remaining = maxLength - base.length
            if (remaining > 0) base.append(userStatus.take(remaining))
        }

        val battery = batteryCommentToken()
        if (battery != null) {
            val separator = if (base.isEmpty()) "" else " "
            if (base.length + separator.length + battery.length <= maxLength) {
                base.append(separator).append(battery)
            }
        }
        return base.toString()
    }

    fun formatLoc(symbol: String, status: String, location: Location): APRSPacket {
        val sym0 = if (symbol.isNotEmpty()) symbol[0] else '/'
        val sym1 = if (symbol.length > 1) symbol[1] else '>'
        val pos = AprsPosition(location.latitude, location.longitude, 0, sym0, sym1).apply {
            positionAmbiguity = serviceSettings.positionAmbiguity
        }
        val statusSpd = if (serviceSettings.includeSpeedAndBearing) AprsPacket.formatCourseSpeed(location) else ""
        val statusFreq = AprsPacket.formatFreq(statusSpd, serviceSettings.frequencyMhz)
        val statusAlt = if (serviceSettings.includeAltitude) AprsPacket.formatAltitude(location) else ""
        val comment = buildPositionComment(statusSpd + statusFreq + statusAlt, status)
        return newPacket(PositionPacket(pos, comment, true))
    }

    @JvmOverloads
    fun sendPacket(packet: APRSPacket, statusPostfix: String = "") {
        packetSendCoordinator.send(packet, statusPostfix)
    }

    fun postLocation(location: Location) {
        var symbol = serviceSettings.symbol("")
        if (symbol.length != 2) {
            symbol = getString(R.string.default_symbol)
        }
        val status = serviceSettings.status(getString(R.string.default_status))
        val packet = formatLoc(symbol, status, location)
        Log.d(TAG, "packet: $packet")
        sendPacket(packet, String.format(Locale.US, " (±%dm)", location.accuracy.toInt()))
    }

    fun sendPacketFinished(result: String) {
        if (singleShot) {
            singleShot = false
            stopSelf()
        } else {
            val message = "${serviceSettings.callSsid}: $result"
            ServiceNotifier.instance.notifyPosition(this, message)
        }
    }

    fun parsePacket(ts: Long, message: String, source: Int) {
        try {
            var fap = Parser.parse(message)
            if (fap.type == APRSTypes.T_THIRDPARTY) {
                Log.d(TAG, "parsePacket: third-party packet from " + fap.sourceCall)
                val inner = fap.aprsInformation.toString()
                fap = Parser.parse(inner.substring(1))
            }

            val callssid = serviceSettings.callSsid
            if (source == StorageDatabase.Companion.Post.TYPE_INCMG &&
                fap.sourceCall.equals(callssid, ignoreCase = true) &&
                fap.lastUsedDigi != null
            ) {
                Log.i(TAG, "got digipeated own packet")
                val msg = getString(R.string.got_digipeated, fap.lastUsedDigi, fap.aprsInformation.toString())
                ServiceNotifier.instance.notifyPosition(this, msg)
                return
            }

            if (fap.aprsInformation == null) {
                Log.d(TAG, "parsePacket() misses payload: $message")
                return
            }
            if (fap.hasFault()) {
                throw Exception("FAP fault")
            }

            when (val info = fap.aprsInformation) {
                is PositionPacket -> addPosition(ts, fap, info, info.position, null)
                is ObjectPacket -> addPosition(ts, fap, info, info.position, info.objectName)
                is MessagePacket -> msgService.handleMessage(ts, fap, info)
            }
        } catch (e: Exception) {
            Log.d(TAG, "parsePacket() unsupported packet: $message")
            e.printStackTrace()
        }
    }

    fun getCSE(field: InformationField): CourseAndSpeedExtension? {
        return field.extension as? CourseAndSpeedExtension
    }

    fun addPosition(ts: Long, ap: APRSPacket, field: InformationField, pos: AprsPosition, objectname: String?) {
        val cse = getCSE(field)
        db.addPosition(ts, ap, pos, cse, objectname)

        sendBroadcast(
            privateIntent(this, POSITION)
                .putExtra(SOURCE, ap.sourceCall)
                .putExtra(LOCATION, AprsPacket.position2location(ts, pos, cse) as android.os.Parcelable)
                .putExtra(CALLSIGN, objectname ?: ap.sourceCall)
                .putExtra(PACKET, ap.toString())
        )
    }

    fun addPost(t: Int, status: String?, message: String) {
        val ts = System.currentTimeMillis()
        db.addPost(ts, t, status ?: "", message)
        if (t == StorageDatabase.Companion.Post.TYPE_POST || t == StorageDatabase.Companion.Post.TYPE_INCMG || t == StorageDatabase.Companion.Post.TYPE_TX) {
            parsePacket(ts, message, t)
        } else {
            Log.d(TAG, "addPost: $status - $message")
        }
        sendBroadcast(
            privateIntent(this, UPDATE)
                .putExtra(TYPE, t)
                .putExtra(STATUS, message)
        )
    }

    fun addPost(t: Int, statusId: Int, message: String) {
        addPost(t, getString(statusId), message)
    }

    fun postAddPost(t: Int, statusId: Int, message: String) {
        if (t == StorageDatabase.Companion.Post.TYPE_INFO && !serviceSettings.connectionLoggingEnabled) {
            return
        }
        handler.post {
            addPost(t, statusId, message)
            if (t == StorageDatabase.Companion.Post.TYPE_INCMG) {
                msgService.sendPendingMessages()
            } else if (t == StorageDatabase.Companion.Post.TYPE_ERROR) {
                stopSelf()
            }
        }
    }

    fun postSubmit(post: String) {
        postAddPost(StorageDatabase.Companion.Post.TYPE_INCMG, R.string.post_incmg, post)
    }

    fun postAbort(post: String) {
        postAddPost(StorageDatabase.Companion.Post.TYPE_ERROR, R.string.post_error, post)
    }

    fun postPosterStarted() {
        handler.post { onPosterStarted() }
    }

    fun postLinkOn(link: Int) {
        link_error = 0
        sendBroadcast(privateIntent(this, LINK_ON).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkon, getString(link))
        ServiceNotifier.instance.start(this, message)
    }

    fun postLinkOff(link: Int) {
        link_error = link
        sendBroadcast(privateIntent(this, LINK_OFF).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkoff, getString(link))
        ServiceNotifier.instance.start(this, message)
    }
}
