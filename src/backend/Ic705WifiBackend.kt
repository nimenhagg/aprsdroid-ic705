package org.aprsdroid.app.backend

import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.AprsBackend
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.ic705.backend.Ic705BackendPrefs
import org.aprsdroid.app.ic705.backend.Ic705BackendService
import org.aprsdroid.app.ic705.backend.Ic705WifiBackendController

class Ic705WifiBackend(
    service: AprsService,
    preferences: PrefsWrapper
) : AprsBackend(preferences) {

    private val controller: Ic705WifiBackendController =
        Ic705WifiBackendController.createDefault(
            ServiceBridge(service),
            PrefsBridge(preferences),
            service
        )

    override fun start(): Boolean = controller.start()

    override fun update(packet: APRSPacket): String = controller.update(packet)

    override fun stop() {
        controller.stop()
    }

    private class ServiceBridge(private val service: AprsService) : Ic705BackendService {
        override fun postPosterStarted() = service.postPosterStarted()
        override fun postLinkOn(link: Int) = service.postLinkOn(link)
        override fun postLinkOff(link: Int) = service.postLinkOff(link)
        override fun postAbort(message: String) = service.postAbort(message)
        override fun postSubmit(text: String) = service.postSubmit(text)
        override fun getString(resId: Int): String = service.getString(resId)
    }

    private class PrefsBridge(private val preferences: PrefsWrapper) : Ic705BackendPrefs {
        override val address: String
            get() = preferences.getString("ic705.address", "").trim()
        override val controlPort: Int
            get() = try {
                preferences.getString("ic705.control_port", "50001").trim().toInt()
            } catch (_: Exception) {
                50001
            }
        override val username: String
            get() = preferences.getString("ic705.username", "")
        override val password: String
            get() = preferences.getString("ic705.password", "")
    }
}
