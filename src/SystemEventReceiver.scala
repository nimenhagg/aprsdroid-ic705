package org.aprsdroid.app

import android.content.{BroadcastReceiver, Context, Intent}

/**
 * Boot auto-start disabled as part of Phase 2 lifecycle simplification.
 */
class SystemEventReceiver extends BroadcastReceiver {
	override def onReceive(ctx : Context, i : Intent) {
		// Boot auto-start removed
	}
}
