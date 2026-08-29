package org.aprsdroid.app.service

import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.AprsBackend

/**
 * Small service-facing contract around an APRS backend.
 *
 * Keeping this contract independent from Android Service lifecycle makes the
 * ownership rules testable without constructing AprsService.
 */
internal interface ServiceBackend {
    fun start(): Boolean
    fun stop()
    fun update(packet: APRSPacket): String
}

/** Adapts the existing legacy AprsBackend hierarchy without changing it. */
internal class AprsBackendServiceAdapter(
    private val delegate: AprsBackend,
) : ServiceBackend {
    override fun start(): Boolean = delegate.start()

    override fun stop() = delegate.stop()

    override fun update(packet: APRSPacket): String = delegate.update(packet)
}

/**
 * Owns the backend instance used by AprsService.
 *
 * This deliberately contains no Android notifications, broadcasts or location
 * behavior. Those remain AprsService responsibilities until later refactors.
 */
internal class BackendLifecycleCoordinator(
    private val backendFactory: () -> ServiceBackend,
) {
    private var backend: ServiceBackend? = null

    /**
     * Stop the previous backend, create the currently configured one and start it.
     * A backend whose start() returns false is still retained so teardown can stop it.
     */
    fun replaceAndStart(): Boolean {
        backend?.stop()
        val replacement = backendFactory()
        backend = replacement
        return replacement.start()
    }

    /**
     * Stop and release the owned backend. Returns true only when one was present.
     * Clearing ownership makes repeated teardown safe and prevents post-stop updates.
     */
    fun stop(): Boolean {
        val current = backend ?: return false
        backend = null
        current.stop()
        return true
    }

    fun update(packet: APRSPacket): String? = backend?.update(packet)
}
