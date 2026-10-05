// SPDX-License-Identifier: GPL-2.0-or-later
// See LICENSING.md: this file is additionally available under GPL-2.0-or-later.

package org.aprsdroid.app.backend

import android.location.Location
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.AprsBackend
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.ic705.backend.Ic705BackendPrefs
import org.aprsdroid.app.ic705.backend.Ic705BackendService
import org.aprsdroid.app.ic705.backend.Ic705WifiBackendController
import org.aprsdroid.app.radio.WlanRadioModel
import java.util.Locale

class Ic705WifiBackend(
    service: AprsService,
    preferences: PrefsWrapper
) : AprsBackend(preferences) {

    private val controller: Ic705WifiBackendController =
        Ic705WifiBackendController.createDefault(
            ServiceBridge(service, preferences),
            PrefsBridge(preferences),
            service
        )

    override fun start(): Boolean = controller.start()

    override fun update(packet: APRSPacket): String = controller.update(packet)

    override fun stop() {
        controller.stop()
    }

    private class ServiceBridge(
        private val service: AprsService,
        private val preferences: PrefsWrapper,
    ) : Ic705BackendService {
        override fun postPosterStarted() = service.postPosterStarted()
        override fun postLinkOn(link: Int) = service.postLinkOn(link)
        override fun postLinkOff(link: Int) = service.postLinkOff(link)
        override fun postAbort(message: String) = service.postAbort(message)
        override fun postSubmit(text: String) = service.postSubmit(text)
        override fun getString(resId: Int): String = service.getString(resId)

        override fun onFrequencyChanged(frequencyMhz: Float) {
            val freqStr = String.format(Locale.US, "%.3f", frequencyMhz)
            preferences.set("frequency", freqStr)
            service.sendBroadcast(
                AprsService.privateIntent(service, AprsService.SERVICE_FREQUENCY)
                    .putExtra("frequency", freqStr)
            )
        }

        override fun onGpsLocation(location: Location) {
            service.postLocation(location)
        }
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
        override val model: String
            get() = preferences.getString("ic705.model", "IC-705")
        override val syncFreq: Boolean
            get() = preferences.getIc705SyncFreq()
        override val useGps: Boolean
            get() = preferences.getIc705UseGps()
        override val civAddress: Int
            get() {
                val modelObj = WlanRadioModel.findById(model)
                return WlanRadioModel.parseCivAddress(
                    preferences.getString("ic705.civ_address", ""),
                    defaultAddress = modelObj.defaultCivAddress,
                )
            }
    }
}
