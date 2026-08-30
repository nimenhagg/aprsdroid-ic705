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
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Digipeater
import net.ab0oo.aprs.parser.InformationField
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.Position as AprsPosition
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.data.preferences.AprsServiceSettings
import org.aprsdroid.app.data.repository.StorageDatabasePacketPostRepository
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.notification.LiveActivity
import org.aprsdroid.app.notification.LiveBackendMode
import org.aprsdroid.app.notification.ServiceLiveStatus
import org.aprsdroid.app.service.AprsBackendServiceAdapter
import org.aprsdroid.app.service.BackendLifecycleCoordinator
import org.aprsdroid.app.service.ImmediateLocationCoordinator
import org.aprsdroid.app.service.PacketPersistenceCoordinator
import org.aprsdroid.app.service.PacketSendCoordinator
import org.aprsdroid.app.service.ServicePostCoordinator
import org.aprsdroid.app.service.ServiceRuntimeState
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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
    private val serviceRuntimeState: ServiceRuntimeState by lazy {
        ServiceRuntimeState(
            readRunning = { running },
            writeRunning = { value -> running = value },
            readLinkError = { link_error },
            writeLinkError = { value -> link_error = value },
        )
    }

    @JvmField
    val handler = Handler(Looper.getMainLooper())

    private val liveStatusVersion = AtomicLong(0L)

    private fun liveStatus(activity: LiveActivity): ServiceLiveStatus = ServiceLiveStatus(
        mode = LiveBackendMode.fromProtocol(serviceSettings.backendProtocol),
        backendName = serviceSettings.backendName,
        activity = activity,
    )

    private fun updateLiveStatus(activity: LiveActivity) {
        liveStatusVersion.incrementAndGet()
        ServiceNotifier.instance.updateLiveStatus(this, liveStatus(activity))
    }

    private fun startNotifier(status: String, activity: LiveActivity) {
        liveStatusVersion.incrementAndGet()
        ServiceNotifier.instance.start(this, status, liveStatus(activity))
    }

    private fun markTransientLiveStatus(activity: LiveActivity, holdMillis: Long) {
        val token = liveStatusVersion.incrementAndGet()
        ServiceNotifier.instance.updateLiveStatus(this, liveStatus(activity))
        handler.postDelayed({
            if (serviceRuntimeState.isRunning && liveStatusVersion.get() == token) {
                updateLiveStatus(LiveActivity.READY)
            }
        }, holdMillis)
    }

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
                updateLiveStatus(LiveActivity.ERROR)
            },
            postToMain = { task -> handler.post { task() } },
            onFinished = { status -> sendPacketFinished(status) },
        )
    }

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }
    val msgService: MessageService by lazy { MessageService(this) }
    val locSource: LocationSource by lazy { LocationSource.instanciateLocation(this, prefs) }
    val msgNotifier by lazy { msgService.createMessageNotifier() }

    private val packetPersistenceCoordinator: PacketPersistenceCoordinator by lazy {
        PacketPersistenceCoordinator(
            repository = StorageDatabasePacketPostRepository(db),
            callSsid = { serviceSettings.callSsid },
            onOwnDigipeat = { lastDigi, information ->
                Log.i(TAG, "got digipeated own packet")
                val msg = getString(R.string.got_digipeated, lastDigi, information)
                ServiceNotifier.instance.notifyPosition(this, msg)
            },
            onMessage = { ts, packet, message ->
                msgService.handleMessage(ts, packet, message)
            },
            onPositionPersisted = { ts, packet, position, courseAndSpeed, objectName ->
                sendBroadcast(
                    privateIntent(this, POSITION)
                        .putExtra(SOURCE, packet.sourceCall)
                        .putExtra(
                            LOCATION,
                            AprsPacket.position2location(ts, position, courseAndSpeed) as android.os.Parcelable
                        )
                        .putExtra(CALLSIGN, objectName ?: packet.sourceCall)
                        .putExtra(PACKET, packet.toString())
                )
            },
            onPostUpdated = { postType, message ->
                sendBroadcast(
                    privateIntent(this, UPDATE)
                        .putExtra(TYPE, postType)
                        .putExtra(STATUS, message)
                )
            },
            onLogOnly = { status, message ->
                Log.d(TAG, "addPost: $status - $message")
            },
            onDebug = { message -> Log.d(TAG, message) },
            onParseFailure = { message, error ->
                Log.d(TAG, "parsePacket() unsupported packet: $message")
                error.printStackTrace()
            },
        )
    }

    private val servicePostCoordinator: ServicePostCoordinator by lazy {
        ServicePostCoordinator(
            postToMain = { task -> handler.post { task() } },
            connectionLoggingEnabled = { serviceSettings.connectionLoggingEnabled },
            addPost = { postType, statusId, message -> addPost(postType, statusId, message) },
            sendPendingMessages = { msgService.sendPendingMessages() },
            stopService = { stopSelf() },
        )
    }

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
                if (serviceRuntimeState.isRunning) stopSelf()
                return
            }
            SERVICE_SEND_PACKET -> {
                if (!serviceRuntimeState.isRunning) {
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
                    if (!serviceRuntimeState.isRunning) return
                } else return
            }
        }

        val isOnce = (i.action == SERVICE_ONCE)
        val toastString = if (isOnce) {
            if (!serviceRuntimeState.isRunning) {
                singleShot = true
            }
            getString(R.string.service_once)
        } else {
            getString(R.string.service_start)
        }

        showToast(String.format(toastString, serviceSettings.locationSourceName, serviceSettings.backendName))

        val callssid = serviceSettings.callSsid
        startNotifier(callssid, LiveActivity.CONNECTING)

        if (!serviceRuntimeState.isRunning) {
            serviceRuntimeState.markStarted()
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
        updateLiveStatus(LiveActivity.CONNECTING)
        if (backendCoordinator.replaceAndStart()) {
            onPosterStarted()
        }
    }

    fun triggerImmediateLocation() {
        // ImmediateLocationCoordinator releases one-shot listeners after 15 seconds.
        // Keep the UI state slightly longer, then fall back to the backend-ready state.
        markTransientLiveStatus(LiveActivity.WAITING_LOCATION, 16_000L)
        immediateLocationCoordinator.trigger(locSource)
    }

    fun onPosterStarted() {
        Log.d(TAG, "onPosterStarted")
        val locInfo = locSource.start(singleShot)
        val callssid = serviceSettings.callSsid
        val message = "$callssid: $locInfo"
        startNotifier(message, LiveActivity.READY)

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
        serviceRuntimeState.markStopped()
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

    private fun enqueuePacket(
        packet: APRSPacket,
        statusPostfix: String,
        activity: LiveActivity,
    ) {
        markTransientLiveStatus(activity, 2_500L)
        packetSendCoordinator.send(packet, statusPostfix)
    }

    @JvmOverloads
    fun sendPacket(packet: APRSPacket, statusPostfix: String = "") {
        enqueuePacket(packet, statusPostfix, LiveActivity.TRANSMITTING)
    }

    fun postLocation(location: Location) {
        var symbol = serviceSettings.symbol("")
        if (symbol.length != 2) {
            symbol = getString(R.string.default_symbol)
        }
        val status = serviceSettings.status(getString(R.string.default_status))
        val packet = formatLoc(symbol, status, location)
        Log.d(TAG, "packet: $packet")
        enqueuePacket(
            packet,
            String.format(Locale.US, " (±%dm)", location.accuracy.toInt()),
            LiveActivity.BEACONING,
        )
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
        packetPersistenceCoordinator.parsePacket(ts, message, source)
    }

    fun getCSE(field: InformationField): CourseAndSpeedExtension? {
        return packetPersistenceCoordinator.courseAndSpeed(field)
    }

    fun addPosition(ts: Long, ap: APRSPacket, field: InformationField, pos: AprsPosition, objectname: String?) {
        packetPersistenceCoordinator.addPosition(ts, ap, field, pos, objectname)
    }

    fun addPost(t: Int, status: String?, message: String) {
        packetPersistenceCoordinator.addPost(t, status, message)
    }

    fun addPost(t: Int, statusId: Int, message: String) {
        addPost(t, getString(statusId), message)
    }

    fun postAddPost(t: Int, statusId: Int, message: String) {
        servicePostCoordinator.post(t, statusId, message)
    }

    fun postSubmit(post: String) {
        markTransientLiveStatus(LiveActivity.RECEIVING, 2_500L)
        postAddPost(StorageDatabase.Companion.Post.TYPE_INCMG, R.string.post_incmg, post)
    }

    fun postAbort(post: String) {
        updateLiveStatus(LiveActivity.ERROR)
        postAddPost(StorageDatabase.Companion.Post.TYPE_ERROR, R.string.post_error, post)
    }

    fun postPosterStarted() {
        handler.post { onPosterStarted() }
    }

    fun postLinkOn(link: Int) {
        serviceRuntimeState.markLinkOn()
        sendBroadcast(privateIntent(this, LINK_ON).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkon, getString(link))
        startNotifier(message, LiveActivity.READY)
    }

    fun postLinkOff(link: Int) {
        serviceRuntimeState.markLinkOff(link)
        sendBroadcast(privateIntent(this, LINK_OFF).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkoff, getString(link))
        startNotifier(message, LiveActivity.RECONNECTING)
    }
}
