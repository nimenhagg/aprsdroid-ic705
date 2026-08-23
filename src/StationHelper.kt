package org.aprsdroid.app

import android.content.res.Configuration
import android.view.Menu
import android.view.MenuItem

abstract class StationHelper(val titleId: Int) : LoadingListActivity() {
    val targetcall: String? by lazy { intent.dataString }

    override fun onResume() {
        super.onResume()
        setLongTitle(titleId, targetcall ?: "")
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        setLongTitle(titleId, targetcall ?: "")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.context_call, menu)
        return true
    }

    override fun onOptionsItemSelected(mi: MenuItem): Boolean {
        return callsignAction(mi.itemId, targetcall ?: "")
    }
}
