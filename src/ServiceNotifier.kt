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
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class ServiceNotifier {
    companion object {
        const val SERVICE_NOTIFICATION: Int = 1

        @JvmStatic
        val instance = ServiceNotifier()
    }

    var callNotification: Int = SERVICE_NOTIFICATION + 1
    val callIdMap = mutableMapOf<String, Int>()

    fun setupChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel("status", ctx.getString(R.string.aprsservice), NotificationManager.IMPORTANCE_LOW)
            )
            nm?.createNotificationChannel(
                NotificationChannel("msg", ctx.getString(R.string.p_msg), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun newNotificationBuilder(ctx: Service, channel: String): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
    }

    fun newNotification(ctx: Service, status: String): Notification {
        val i = Intent(ctx, APRSdroid::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val appname = ctx.resources.getString(R.string.app_name)
        val nb = newNotificationBuilder(ctx, "status")
            .setContentTitle(appname)
            .setContentText(status)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.ic_status)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)

        nb.setShowWhen(true)

        val stopIntent = AprsService.intent(ctx, AprsService.SERVICE_STOP)
        val stopPendingIntent = PendingIntent.getService(
            ctx, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exitTitle = ctx.getString(R.string.notification_action_exit)

        val exitAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            exitTitle,
            stopPendingIntent
        ).build()
        nb.addAction(exitAction)

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
        val i = Intent(ctx, MessageActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse(call)
        }
        return newNotificationBuilder(ctx, "msg")
            .setContentTitle(call)
            .setContentText(message)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.icon)
            .setTicker("$call: $message")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .build()
    }

    fun getNotificationMgr(ctx: Context): NotificationManager {
        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun setupNotification(n: Notification, ctx: Context, prefs: PrefsWrapper, default: Boolean, prefix: String) {
        if (prefs.getBoolean(prefix + "notify_led", default)) {
            @Suppress("DEPRECATION")
            n.ledARGB = Color.YELLOW
            @Suppress("DEPRECATION")
            n.ledOnMS = 300
            @Suppress("DEPRECATION")
            n.ledOffMS = 1000
            @Suppress("DEPRECATION")
            n.flags = n.flags or Notification.FLAG_SHOW_LIGHTS
        }
        if (prefs.getBoolean(prefix + "notify_vibr", default)) {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 200, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 200, 200), -1)
            }
        }
        val sound = prefs.getString(prefix + "notify_ringtone", null)
        if (!sound.isNullOrEmpty()) {
            @Suppress("DEPRECATION")
            n.sound = Uri.parse(sound)
        }
    }

    fun notifyMessage(ctx: Service, prefs: PrefsWrapper, call: String, message: String) {
        val n = newMessageNotification(ctx, call, message)
        setupNotification(n, ctx, prefs, true, "")
        getNotificationMgr(ctx).notify(getCallNumber(call), n)
    }

    fun cancelMessage(ctx: Context, call: String) {
        getNotificationMgr(ctx).cancel(getCallNumber(call))
    }

    @JvmOverloads
    fun notifyPosition(ctx: Service, prefs: PrefsWrapper, status: String, prefix: String = "pos_") {
        val n = newNotification(ctx, status)
        setupNotification(n, ctx, prefs, false, prefix)
        getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
    }

    fun start(ctx: Service, status: String) {
        var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (Build.VERSION.SDK_INT >= 29) {
            var types = 0
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
        ServiceCompat.startForeground(ctx, SERVICE_NOTIFICATION, newNotification(ctx, status), serviceType)
    }

    fun stop(ctx: Service) {
        @Suppress("DEPRECATION")
        ctx.stopForeground(true)
    }
}
