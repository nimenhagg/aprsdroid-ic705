package org.aprsdroid.app.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.aprsdroid.app.PrefsWrapper

private const val KEY_COMPACT_LISTS = "ui.compact_lists"

@Composable
fun rememberCompactListMode(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { PrefsWrapper.defaultSharedPreferences(context) }
    val state = remember(prefs) { mutableStateOf(prefs.getBoolean(KEY_COMPACT_LISTS, false)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_COMPACT_LISTS) {
                state.value = sharedPreferences.getBoolean(KEY_COMPACT_LISTS, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return state
}

fun setCompactListMode(context: Context, enabled: Boolean) {
    PrefsWrapper.defaultSharedPreferences(context.applicationContext).edit {
        putBoolean(KEY_COMPACT_LISTS, enabled)
    }
}
