package org.aprsdroid.app.backend

import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.{AprsBackend, AprsService, PrefsWrapper}
import org.aprsdroid.app.ic705.backend.{Ic705BackendPrefs, Ic705BackendService, Ic705WifiBackendController}

class Ic705WifiBackend(
    service: AprsService,
    preferences: PrefsWrapper
) extends AprsBackend(preferences) {

    private val controller: Ic705WifiBackendController =
        Ic705WifiBackendController.createDefault(
            new ServiceBridge(service),
            new PrefsBridge(preferences),
            service
        )

    override def start(): Boolean = controller.start()

    override def update(packet: APRSPacket): String = controller.update(packet)

    override def stop(): Unit = controller.stop()

    private class ServiceBridge(service: AprsService) extends Ic705BackendService {
        override def postPosterStarted(): Unit = service.postPosterStarted()
        override def postAbort(message: String): Unit = service.postAbort(message)
        override def postSubmit(text: String): Unit = service.postSubmit(text)
        override def getString(resId: Int): String = service.getString(resId)
    }

    private class PrefsBridge(preferences: PrefsWrapper) extends Ic705BackendPrefs {
        override def getAddress: String = preferences.getString("ic705.address", "").trim
        override def getControlPort: Int = {
            try {
                preferences.getString("ic705.control_port", "50001").trim.toInt
            } catch {
                case _: Exception => 50001
            }
        }
        override def getUsername: String = preferences.getString("ic705.username", "")
        override def getPassword: String = preferences.getString("ic705.password", "")
    }
}
