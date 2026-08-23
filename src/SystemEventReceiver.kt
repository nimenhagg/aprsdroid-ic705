package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot auto-start disabled as part of Phase 2 lifecycle simplification.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        // Boot auto-start removed
    }
}
