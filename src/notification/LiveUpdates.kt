package org.aprsdroid.app.notification

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import org.aprsdroid.app.PrefsWrapper

internal object LiveUpdates {
    private const val PREF_KEY = "notification_live_updates"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun isEnabled(context: Context): Boolean =
        PrefsWrapper.defaultSharedPreferences(context).getBoolean(PREF_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        PrefsWrapper.defaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_KEY, enabled)
            .apply()
    }

    fun canPost(context: Context): Boolean =
        isSupported() && NotificationManagerCompat.from(context).canPostPromotedNotifications()
}
