package org.aprsdroid.app.notification

import android.content.Context
import org.aprsdroid.app.R

internal enum class LiveBackendMode(
    val shortLabel: String,
) {
    IC705("705"),
    APRS_IS("IS"),
    AFSK("AFSK"),
    KISS("KISS"),
    KENWOOD("KW"),
    TNC2("TNC"),
    OTHER("APRS");

    companion object {
        fun fromProtocol(protocol: String): LiveBackendMode = when (protocol) {
            "ic705" -> IC705
            "aprsis" -> APRS_IS
            "afsk" -> AFSK
            "kiss" -> KISS
            "kenwood" -> KENWOOD
            "tnc2" -> TNC2
            else -> OTHER
        }
    }

    val readyKind: ReadyKind
        get() = when (this) {
            AFSK -> ReadyKind.LISTENING
            APRS_IS, KISS, KENWOOD, TNC2 -> ReadyKind.ONLINE
            IC705, OTHER -> ReadyKind.IDLE
        }
}

internal enum class ReadyKind {
    IDLE,
    ONLINE,
    LISTENING,
}

internal enum class LiveActivity {
    CONNECTING,
    READY,
    RECEIVING,
    TRANSMITTING,
    BEACONING,
    RECONNECTING,
    WAITING_LOCATION,
    ERROR,
}

internal data class ServiceLiveStatus(
    val mode: LiveBackendMode,
    val backendName: String,
    val activity: LiveActivity,
) {
    fun shortText(context: Context): String {
        if (activity == LiveActivity.WAITING_LOCATION) {
            return context.getString(R.string.live_status_chip_waiting_location)
        }
        val resId = when (activity) {
            LiveActivity.CONNECTING -> R.string.live_status_chip_connecting
            LiveActivity.READY -> when (mode.readyKind) {
                ReadyKind.IDLE -> R.string.live_status_chip_idle
                ReadyKind.ONLINE -> R.string.live_status_chip_online
                ReadyKind.LISTENING -> R.string.live_status_chip_listening
            }
            LiveActivity.RECEIVING -> R.string.live_status_chip_receiving
            LiveActivity.TRANSMITTING -> R.string.live_status_chip_transmitting
            LiveActivity.BEACONING -> R.string.live_status_chip_beaconing
            LiveActivity.RECONNECTING -> R.string.live_status_chip_reconnecting
            LiveActivity.ERROR -> R.string.live_status_chip_error
            LiveActivity.WAITING_LOCATION -> error("handled above")
        }
        return context.getString(resId, mode.shortLabel)
    }

    fun detailText(context: Context): String {
        val stateResId = when (activity) {
            LiveActivity.CONNECTING -> R.string.live_status_state_connecting
            LiveActivity.READY -> when (mode.readyKind) {
                ReadyKind.IDLE, ReadyKind.ONLINE -> R.string.live_status_state_connected
                ReadyKind.LISTENING -> R.string.live_status_state_listening
            }
            LiveActivity.RECEIVING -> R.string.live_status_state_receiving
            LiveActivity.TRANSMITTING -> R.string.live_status_state_transmitting
            LiveActivity.BEACONING -> R.string.live_status_state_beaconing
            LiveActivity.RECONNECTING -> R.string.live_status_state_reconnecting
            LiveActivity.WAITING_LOCATION -> R.string.live_status_state_waiting_location
            LiveActivity.ERROR -> R.string.live_status_state_error
        }
        return context.getString(
            R.string.live_status_detail,
            backendName,
            context.getString(stateResId),
        )
    }
}
