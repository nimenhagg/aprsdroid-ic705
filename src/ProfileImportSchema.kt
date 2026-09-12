package org.aprsdroid.app

internal enum class ProfilePreferenceType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
}

internal object ProfileImportSchema {
    private val booleanKeys = setOf(
        "show_objects",
        "show_satellite",
        "send_battery_aprsis",
        "periodicposition",
        "priv_spdbear",
        "priv_altitude",
        "afsk.btsco",
        "bt.client",
        "kenwood.gps",
        "kenwood.gps_debug",
    )

    fun validateType(key: String, existing: Any?, incoming: Any) {
        val expected = existingType(existing) ?: expectedTypeForUnsetKey(key)
        val compatible = when (expected) {
            ProfilePreferenceType.STRING -> incoming is String
            ProfilePreferenceType.BOOLEAN -> incoming is Boolean
            ProfilePreferenceType.INT -> incoming is Int
            ProfilePreferenceType.LONG -> incoming is Long || incoming is Int
            ProfilePreferenceType.FLOAT -> incoming is Number
        }
        require(compatible) {
            "Profile value type does not match preference schema: $key"
        }
    }

    private fun existingType(value: Any?): ProfilePreferenceType? = when (value) {
        null -> null
        is String -> ProfilePreferenceType.STRING
        is Boolean -> ProfilePreferenceType.BOOLEAN
        is Int -> ProfilePreferenceType.INT
        is Long -> ProfilePreferenceType.LONG
        is Float -> ProfilePreferenceType.FLOAT
        else -> null
    }

    private fun expectedTypeForUnsetKey(key: String): ProfilePreferenceType =
        if (key in booleanKeys) ProfilePreferenceType.BOOLEAN else ProfilePreferenceType.STRING
}
