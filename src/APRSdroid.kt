package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager

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
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<android.os.Parcelable>("device")
        if (UsbTnc.checkDeviceHandle(sp, device) && sp.getBoolean("service_running", false)) {
            startService(AprsService.intent(this, AprsService.SERVICE))
        }

        val mapmode = MapModes.defaultMapMode(this, PrefsWrapper(this))
        when (sp.getString("activity", "log")) {
            "hub" -> replaceAct(HubActivity::class.java)
            "map" -> replaceAct(mapmode.viewClass)
            else -> replaceAct(LogActivity::class.java)
        }
    }
}
