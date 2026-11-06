package org.aprsdroid.app.data.preferences

/**
 * Common preference store abstraction decoupling ViewModels and domain components
 * from raw Android Context and SharedPreferences implementations.
 */
interface AppPreferenceStore {
    fun getString(key: String, defValue: String?): String
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getStringInt(key: String, defValue: Int): Int
    fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> = defValue
    fun set(name: String, newVal: String): String
    fun set(name: String, newVal: Boolean): Boolean
    fun setStringSet(name: String, values: Set<String>) {}
    fun saveCredentials(callsign: String, passcode: String)
}
