package org.aprsdroid.app

import org.aprsdroid.app.audio.Ax25Limits

internal enum class ProfilePreferenceType {
    STRING,
    BOOLEAN,
    FLOAT,
    STRING_SET,
}

internal object ProfileImportSchema {
    private val schema = buildMap {
        fun strings(vararg keys: String) = keys.forEach { put(it, ProfilePreferenceType.STRING) }
        fun booleans(vararg keys: String) = keys.forEach { put(it, ProfilePreferenceType.BOOLEAN) }
        fun floats(vararg keys: String) = keys.forEach { put(it, ProfilePreferenceType.FLOAT) }

        strings(
            "callsign", "ssid", "passcode", "digi_path", "frequency", "status", "symbol",
            "proto", "aprsis", "link", "loc_source", "mapmode", "map_custom_url",
            "map_custom_subdomains", "activity", "show_age", "station_tap_action",
            "baudrate", "afsk.output", "afsk.prefix", "bt.channel", "bt.mac", "http.server",
            "ic705.address", "ic705.control_port", "ic705.password", "ic705.username",
            "kiss.delay", "kiss.init", "tcp.filter", "tcp.filterdist", "tcp.server",
            "tcp.sotimeout", "udp.server", "interval", "manual_lat", "manual_lon",
            "priv_ambiguity", "sb.fastrate", "sb.fastspeed", "sb.slowrate",
            "sb.slowspeed", "sb.turnmin", "sb.turnslope", "sb.turntime",
        )

        booleans(
            "show_objects", "show_satellite", "send_battery_aprsis", "periodicposition",
            "priv_spdbear", "priv_altitude", "afsk.btsco", "bt.client", "kenwood.gps",
            "kenwood.gps_debug", "keepscreen", "conn_log", "notification_live_updates",
        )

        floats("map_lat", "map_lon", "map_zoom")
        put("digi_path_user_presets", ProfilePreferenceType.STRING_SET)
    }

    private val blockedKeys = setOf("service_running", "firstrun")

    fun isBlockedKey(key: String): Boolean = key in blockedKeys

    /**
     * Keys that change where data goes or which credentials are used.
     *
     * The import dialog lists these by name so a user can tell "this profile
     * only tweaks display options" apart from "this profile repoints my
     * APRS-IS server and rewrites my radio password".
     */
    private val sensitiveKeys = setOf(
        "callsign",
        "passcode",
        "ic705.password",
        "ic705.username",
        "ic705.address",
        "ic705.control_port",
        "tcp.server",
        "udp.server",
        "http.server",
        "proto",
        "link",
    )

    fun isSensitiveKey(key: String): Boolean = key in sensitiveKeys

    /** Sensitive keys present in an import, in schema order, for display. */
    fun sensitiveKeysIn(keys: Collection<String>): List<String> =
        schema.keys.filter { it in sensitiveKeys && it in keys }

    fun typeFor(key: String): ProfilePreferenceType? = schema[key]

    fun isImportableKey(key: String): Boolean = typeFor(key) != null

    fun validateType(key: String, incoming: Any) {
        val expected = requireNotNull(typeFor(key)) { "Unsupported profile key: $key" }
        val compatible = when (expected) {
            ProfilePreferenceType.STRING -> incoming is String
            ProfilePreferenceType.BOOLEAN -> incoming is Boolean
            ProfilePreferenceType.FLOAT -> incoming is Number
            ProfilePreferenceType.STRING_SET ->
                incoming is Set<*> && incoming.all { it is String }
        }
        require(compatible) {
            "Profile value type does not match preference schema: $key"
        }
    }

    /**
     * Rejects values that parse correctly but cannot be used.
     *
     * A profile is an untrusted document, and the identity keys are the ones an
     * unusable value actually breaks: an over-long callsign is accepted by the
     * settings dialog's schema but can never be encoded into an AX.25 address
     * field, so every transmit path would fail or silently truncate it.
     */
    fun validateValue(key: String, value: Any) {
        when (key) {
            "callsign", "ssid" -> {
                val text = (value as? String)?.trim().orEmpty()
                require(text.length <= Ax25Limits.MAX_CALLSIGN_CHARS) {
                    "Profile callsign \"$text\" exceeds ${Ax25Limits.MAX_CALLSIGN_CHARS} characters: $key"
                }
                require(text.none { it == '-' }) {
                    "Profile callsign must not contain an SSID separator: $key"
                }
            }
        }
    }
}
