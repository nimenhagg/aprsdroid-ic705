package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView

class HubActivity : MainListActivity("hub", R.id.hub) {

    val sla: StationListAdapter by lazy {
        StationListAdapter(this, prefs, prefs.getCallSsid(), "", StationListAdapter.NEIGHBORS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        onContentViewLoaded()

        onStartLoading()
        listAdapter = sla
        listView.isTextFilterEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        sla.onDestroy()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val c = listView.getItemAtPosition(position) as? android.database.Cursor
        c?.let {
            val call = it.getString(StorageDatabase.Companion.Station.COLUMN_CALL)
            openMessaging(call)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_activities, menu)
        menuInflater.inflate(R.menu.options_map, menu)
        menuInflater.inflate(R.menu.options, menu)
        menu.findItem(R.id.hub)?.isVisible = false
        menu.findItem(R.id.age)?.isVisible = true
        menu.findItem(R.id.overlays)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
                true
            }
            R.id.map -> {
                MapModes.startMap(this, prefs, "")
                true
            }
            R.id.log -> {
                startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.conversations -> {
                startActivity(Intent(this, ConversationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.export -> {
                onStartLoading()
                LogExporter(this, StorageDatabase.open(this), null) { onStopLoading() }.execute()
                true
            }
            R.id.clear -> {
                onStartLoading()
                StorageCleaner(this, StorageDatabase.open(this)) { onStopLoading() }.execute()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
