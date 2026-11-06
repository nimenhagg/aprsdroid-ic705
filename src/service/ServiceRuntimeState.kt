package org.aprsdroid.app.service

/**
 * Centralizes AprsService runtime-state mutations while preserving the legacy
 * static compatibility fields as the backing storage and emitting reactive Flow updates.
 *
 * Reads are delegated as well as writes, so external legacy callers that still
 * observe or mutate AprsService.running / AprsService.link_error remain visible
 * to the service during this incremental refactor.
 */
internal class ServiceRuntimeState(
    private val readRunning: () -> Boolean,
    private val writeRunning: (Boolean) -> Unit,
    private val readLinkError: () -> Int,
    private val writeLinkError: (Int) -> Unit,
) {
    val isRunning: Boolean
        get() = readRunning()

    val linkError: Int
        get() = readLinkError()

    fun markStarted() {
        writeRunning(true)
        ServiceEventBus.updateServiceRunning(true)
    }

    fun markStopped() {
        writeRunning(false)
        writeLinkError(0)
        ServiceEventBus.updateServiceRunning(false)
        ServiceEventBus.updateLinkStatus(0)
        ServiceEventBus.updateLiveStatus(null)
    }

    fun markLinkOn() {
        writeLinkError(0)
        ServiceEventBus.updateLinkStatus(0)
    }

    fun markLinkOff(link: Int) {
        writeLinkError(link)
        ServiceEventBus.updateLinkStatus(link)
    }
}
