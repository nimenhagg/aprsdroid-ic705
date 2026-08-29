package org.aprsdroid.app.data.preferences

import org.aprsdroid.app.PrefsWrapper

/**
 * Typed view of the preferences consumed directly by AprsService.
 *
 * This deliberately keeps the existing SharedPreferences storage and keys.
 * It is a migration boundary: service code stops knowing raw keys while
 * settings UI and legacy backends can continue using PrefsWrapper unchanged.
 */
class AprsServiceSettings(
    private val prefs: PrefsWrapper,
) {
    var serviceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) {
            prefs.setBoolean(KEY_SERVICE_RUNNING, value)
        }

    var frequencyText: String
        get() = prefs.getString(KEY_FREQUENCY, null)
        set(value) {
            prefs.set(KEY_FREQUENCY, value)
        }

    val frequencyMhz: Float
        get() = prefs.getStringFloat(KEY_FREQUENCY, 0.0f)

    val callSsid: String
        get() = prefs.getCallSsid()

    val locationSourceName: String
        get() = prefs.getLocationSourceName()

    val backendName: String
        get() = prefs.getBackendName()

    val digipeaterPath: String
        get() = prefs.getString(KEY_DIGI_PATH, DEFAULT_DIGI_PATH)

    val includeBatteryOnAprsIs: Boolean
        get() = prefs.getSendBatteryAprsIs() && prefs.getProto() == PROTO_APRS_IS

    val positionAmbiguity: Int
        get() = prefs.getStringInt(KEY_PRIVACY_AMBIGUITY, 0)

    val includeSpeedAndBearing: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_SPEED_BEARING, true)

    val includeAltitude: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ALTITUDE, true)

    val connectionLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONNECTION_LOG, false)

    fun symbol(defaultValue: String): String = prefs.getString(KEY_SYMBOL, defaultValue)

    fun status(defaultValue: String): String = prefs.getString(KEY_STATUS, defaultValue)

    private companion object {
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_FREQUENCY = "frequency"
        const val KEY_DIGI_PATH = "digi_path"
        const val KEY_PRIVACY_AMBIGUITY = "priv_ambiguity"
        const val KEY_PRIVACY_SPEED_BEARING = "priv_spdbear"
        const val KEY_PRIVACY_ALTITUDE = "priv_altitude"
        const val KEY_CONNECTION_LOG = "conn_log"
        const val KEY_SYMBOL = "symbol"
        const val KEY_STATUS = "status"

        const val DEFAULT_DIGI_PATH = "WIDE1-1"
        const val PROTO_APRS_IS = "aprsis"
    }
}
