package org.aprsdroid.app.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.aprsdroid.app.service.ServiceEventBus

@OptIn(ExperimentalCoroutinesApi::class)
internal fun ServiceEventBus.resetForTesting() {
    _isServiceRunning.value = false
    _linkError.value = 0
    _liveStatus.value = null
    _packetEvents.resetReplayCache()
    _messageEvents.resetReplayCache()
}
