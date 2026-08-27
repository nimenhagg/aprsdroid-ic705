package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class APRSdroid : Activity() {
    fun replaceAct(act: Class<*>) {
        val i = Intent(this, act).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(i)
        finish()
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
            "hub" -> replaceAct(HubActivity::class.java)
            "map" -> replaceAct(mapmode.viewClass)
            "log" -> replaceAct(LogActivity::class.java)
            else -> replaceAct(HubActivity::class.java)
        }
    }
}
