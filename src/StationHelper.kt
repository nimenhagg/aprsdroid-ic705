package org.aprsdroid.app

/**
 * Shared callsign target resolver for Compose station/message screens.
 *
 * The old AppCompat ActionBar/context-menu hooks were removed after those actions
 * moved into the Compose UI.
 */
abstract class StationHelper : BaseRecyclerActivity() {
    val targetcall: String? by lazy {
        intent.dataString?.removePrefix("call:")?.removePrefix("sms:")?.takeIf { it.isNotEmpty() }
            ?: intent.getStringExtra("call")
            ?: intent.getStringExtra("targetcall")
    }
}
