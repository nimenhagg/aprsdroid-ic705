package org.aprsdroid.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aprsdroid.app.notification.ServiceLiveStatus

data class AprsPacketEvent(
    val callsign: String,
    val type: Int,
    val source: String,
    val packet: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class AprsMessageEvent(
    val destination: String,
    val sender: String = "",
    val body: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Reactive event hub decoupling background APRS services and ViewModels/UI.
 * Eliminates reliance on untyped Android BroadcastReceivers for intra-app communication.
 */
open class ServiceEventBus {
    internal val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    internal val _linkError = MutableStateFlow(0)
    val linkError: StateFlow<Int> = _linkError.asStateFlow()

    internal val _liveStatus = MutableStateFlow<ServiceLiveStatus?>(null)
    val liveStatus: StateFlow<ServiceLiveStatus?> = _liveStatus.asStateFlow()

    internal val _packetEvents = MutableSharedFlow<AprsPacketEvent>(replay = 1, extraBufferCapacity = 64)
    val packetEvents: SharedFlow<AprsPacketEvent> = _packetEvents.asSharedFlow()

    internal val _messageEvents = MutableSharedFlow<AprsMessageEvent>(replay = 1, extraBufferCapacity = 64)
    val messageEvents: SharedFlow<AprsMessageEvent> = _messageEvents.asSharedFlow()

    fun updateServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateLinkStatus(linkError: Int) {
        _linkError.value = linkError
    }

    fun updateLiveStatus(status: ServiceLiveStatus?) {
        _liveStatus.value = status
    }

    fun postPacketEvent(event: AprsPacketEvent) {
        _packetEvents.tryEmit(event)
    }

    fun postMessageEvent(event: AprsMessageEvent) {
        _messageEvents.tryEmit(event)
    }



    companion object : ServiceEventBus()
}
