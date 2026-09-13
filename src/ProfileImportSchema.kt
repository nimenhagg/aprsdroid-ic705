package org.aprsdroid.app

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
}
