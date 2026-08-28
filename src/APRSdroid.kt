package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.aprsdroid.app.ui.navigation.MainRoutes

class APRSdroid : Activity() {
    private fun replaceAct(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun openHub(startDestination: String = MainRoutes.STATIONS) {
        replaceAct(
            Intent(this, HubActivity::class.java).apply {
                putExtra(HubActivity.EXTRA_START_DESTINATION, startDestination)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsWrapper(this)
        val sp = prefs.prefs

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<android.os.Parcelable>("device")
        if (UsbTnc.checkDeviceHandle(sp, device) && sp.getBoolean("service_running", false)) {
            startService(AprsService.intent(this, AprsService.SERVICE))
        }

        val mapmode = MapModes.defaultMapMode(this, prefs)
        when (sp.getString("activity", "hub")) {
            "hub" -> openHub()
            // Map remains on the legacy Activity until its lifecycle-heavy migration lands.
            "map" -> replaceAct(Intent(this, mapmode.viewClass))
            "log" -> openHub(MainRoutes.PACKETS)
            else -> openHub()
        }
    }
}
