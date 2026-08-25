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
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class ServiceNotifier {
    companion object {
        const val SERVICE_NOTIFICATION: Int = 1

        @JvmStatic
        val instance = ServiceNotifier()
    }

    var callNotification: Int = SERVICE_NOTIFICATION + 1
    val callIdMap = mutableMapOf<String, Int>()

    fun setupChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel("status", ctx.getString(R.string.aprsservice), NotificationManager.IMPORTANCE_LOW)
        )
        nm?.createNotificationChannel(
            NotificationChannel("msg", ctx.getString(R.string.p_msg), NotificationManager.IMPORTANCE_DEFAULT)
        )
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
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)

        nb.setShowWhen(true)

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
        val i = Intent(ctx, MessageActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = call.toUri()
        }
        return newNotificationBuilder(ctx, "msg")
            .setContentTitle(call)
            .setContentText(message)
            .setContentIntent(PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setTicker("$call: $message")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .build()
    }

    fun getNotificationMgr(ctx: Context): NotificationManager {
        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun setupNotification(n: Notification, ctx: Context, prefs: PrefsWrapper, default: Boolean, prefix: String) {
        try {
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
                val pattern = longArrayOf(0, 200, 200)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, -1)
                }
            }
            val sound = prefs.getString(prefix + "notify_ringtone", null)
            if (!sound.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                n.sound = sound.toUri()
            }
        } catch (_: Exception) {}
    }

    fun notifyMessage(ctx: Service, prefs: PrefsWrapper, call: String, message: String) {
        try {
            val n = newMessageNotification(ctx, call, message)
            setupNotification(n, ctx, prefs, true, "")
            getNotificationMgr(ctx).notify(getCallNumber(call), n)
        } catch (_: Exception) {}
    }

    fun cancelMessage(ctx: Context, call: String) {
        try {
            getNotificationMgr(ctx).cancel(getCallNumber(call))
        } catch (_: Exception) {}
    }

    @JvmOverloads
    fun notifyPosition(ctx: Service, prefs: PrefsWrapper, status: String, prefix: String = "pos_") {
        try {
            val n = newNotification(ctx, status)
            setupNotification(n, ctx, prefs, false, prefix)
            getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
        } catch (_: Exception) {}
    }

    fun start(ctx: Service, status: String) {
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

    fun stop(ctx: Service) {
        try {
            @Suppress("DEPRECATION")
            ctx.stopForeground(true)
        } catch (_: Exception) {}
    }
}
