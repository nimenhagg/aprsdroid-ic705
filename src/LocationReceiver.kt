package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler

class LocationReceiver(
    private val handler: Handler,
    private val callback: () -> Unit
) : BroadcastReceiver() {

    private val runnable = Runnable { callback() }

    override fun onReceive(ctx: Context, i: Intent) {
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 100)
    }
}
