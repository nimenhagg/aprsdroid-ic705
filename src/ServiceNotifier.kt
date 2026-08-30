package org.aprsdroid.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.aprsdroid.app.notification.LiveUpdates
import org.aprsdroid.app.ui.navigation.MainRoutes

class ServiceNotifier {
    companion object {
        const val SERVICE_NOTIFICATION: Int = 1

        @JvmStatic
        val instance = ServiceNotifier()
    }

    var callNotification: Int = SERVICE_NOTIFICATION + 1
    val callIdMap = mutableMapOf<String, Int>()

    private val channelSetupLock = Any()
    @Volatile
    private var channelsReady = false
    @Volatile
    private var lastStatus: String? = null

    fun setupChannels(ctx: Context) {
        if (channelsReady) return
        synchronized(channelSetupLock) {
            if (channelsReady) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel("status", ctx.getString(R.string.aprsservice), NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel("msg", ctx.getString(R.string.p_msg), NotificationManager.IMPORTANCE_DEFAULT)
            )
            channelsReady = true
        }
    }

    fun newNotificationBuilder(ctx: Context, channel: String): Notification.Builder =
        Notification.Builder(ctx, channel)

    fun newNotification(ctx: Context, status: String): Notification {
        val i = Intent(ctx, APRSdroid::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val appname = ctx.resources.getString(R.string.app_name)
        val nb = newNotificationBuilder(ctx, "status")
            .setContentTitle(appname)
            .setContentText(status)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)

        nb.setShowWhen(true)

        if (LiveUpdates.isSupported() && LiveUpdates.isEnabled(ctx)) {
            nb.addExtras(
                Bundle().apply {
                    putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true)
                    putString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT, LiveUpdates.SHORT_CRITICAL_TEXT)
                }
            )
        }

        val stopIntent = AprsService.intent(ctx, AprsService.SERVICE_STOP)
        val stopPendingIntent = PendingIntent.getService(
            ctx, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exitTitle = ctx.getString(R.string.notification_action_exit)

        try {
            val exitAction = Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_action_clear),
                exitTitle,
                stopPendingIntent
            ).build()
            nb.addAction(exitAction)
        } catch (_: Exception) {}

        return nb.build()
    }

    fun getCallNumber(call: String): Int {
        return callIdMap.getOrPut(call) {
            val id = callNotification
            callNotification += 1
            id
        }
    }

    fun newMessageNotification(ctx: Service, call: String, message: String): Notification {
        val hubIntent = Intent(ctx, HubActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(HubActivity.EXTRA_START_DESTINATION, MainRoutes.MESSAGES)
        }
        val messageIntent = Intent(ctx, MessageActivity::class.java).apply {
            data = call.toUri()
        }
        val contentIntent = PendingIntent.getActivities(
            ctx,
            getCallNumber(call),
            arrayOf(hubIntent, messageIntent),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return newNotificationBuilder(ctx, "msg")
            .setContentTitle(call)
            .setContentText(message)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setTicker("$call: $message")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .build()
    }

    fun getNotificationMgr(ctx: Context): NotificationManager {
        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun notifyMessage(ctx: Service, call: String, message: String) {
        try {
            setupChannels(ctx)
            val n = newMessageNotification(ctx, call, message)
            getNotificationMgr(ctx).notify(getCallNumber(call), n)
        } catch (_: Exception) {}
    }

    fun cancelMessage(ctx: Context, call: String) {
        try {
            getNotificationMgr(ctx).cancel(getCallNumber(call))
        } catch (_: Exception) {}
    }

    fun notifyPosition(ctx: Service, status: String) {
        lastStatus = status
        try {
            setupChannels(ctx)
            val n = newNotification(ctx, status)
            getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
        } catch (_: Exception) {}
    }

    fun start(ctx: Service, status: String) {
        lastStatus = status
        setupChannels(ctx)
        var serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= 29) {
            var types = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (types != 0) {
                serviceType = types
            }
        }
        try {
            ServiceCompat.startForeground(ctx, SERVICE_NOTIFICATION, newNotification(ctx, status), serviceType)
        } catch (_: Exception) {
            try {
                ServiceCompat.startForeground(ctx, SERVICE_NOTIFICATION, newNotification(ctx, status), 0)
            } catch (_: Exception) {}
        }
    }

    fun refresh(ctx: Context) {
        if (!AprsService.running) return
        val status = lastStatus ?: return
        try {
            setupChannels(ctx)
            getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, newNotification(ctx, status))
        } catch (_: Exception) {}
    }

    fun stop(ctx: Service) {
        try {
            @Suppress("DEPRECATION")
            ctx.stopForeground(true)
        } catch (_: Exception) {}
        lastStatus = null
    }
}
