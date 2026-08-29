package org.aprsdroid.app.diagnostic

/** Convenience bridge for IC-705 code to expose the latest runtime state in diagnostic bundles. */
object Ic705DiagnosticState {
    fun set(key: String, value: Any?) {
        AppLog.setState("ic705.$key", value)
    }

    fun clearSession() {
        listOf(
            "generation",
            "phase",
            "failure_reason",
            "ptt_state",
            "ptt_asserted_possible",
            "can_stream_audio",
            "reconnect_attempt",
            "possible_stale_generation",
            "network",
            "network_status",
            "network_validated",
            "network_interface",
            "network_addresses",
            "network_ipv4",
        ).forEach { AppLog.setState("ic705.$it", null) }
    }
}
