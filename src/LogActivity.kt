package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import androidx.core.content.ContextCompat

class LogActivity : MainListActivity("log", R.id.log) {
    val TAG = "APRSdroid.Log"

    val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    val postlist: ListView get() = listView
    val la: PostListAdapter by lazy { PostListAdapter(this) }
    val locReceiver = LocationReceiver2(
        { load_cursor() },
        { replace_cursor(it) },
        { cancel_cursor(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        Log.d(TAG, "starting " + getString(R.string.build_version))
        onContentViewLoaded()
        onStartLoading()

        la.filterQueryProvider = storage.getPostFilter("300")
        postlist.adapter = la
        postlist.isTextFilterEnabled = true
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, locReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
        locReceiver.startTask(Intent())
        postlist.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(locReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        la.changeCursor(null)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val c = listView.getItemAtPosition(position) as Cursor
        val t = c.getInt(StorageDatabase.Companion.Post.COLUMN_TYPE)
        if (t != StorageDatabase.Companion.Post.TYPE_POST && t != StorageDatabase.Companion.Post.TYPE_INCMG) return
        val call = c.getString(StorageDatabase.Companion.Post.COLUMN_MESSAGE).split(">")[0]
        Log.d(TAG, "onListItemClick: " + call)
        openDetails(call)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_activities, menu)
        menuInflater.inflate(R.menu.options_map, menu)
        menuInflater.inflate(R.menu.options, menu)
        menu.findItem(R.id.log)?.isVisible = false
        menu.findItem(R.id.age)?.isVisible = false
        menu.findItem(R.id.overlays)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
                true
            }
            R.id.hub -> {
                startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.map -> {
                MapModes.startMap(this, prefs, "")
                true
            }
            R.id.conversations -> {
                startActivity(Intent(this, ConversationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.ic705_rx_diagnostic -> {
                startActivity(Intent(this, org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity::class.java))
                true
            }
            R.id.export -> {
                onStartLoading()
                LogExporter(this, storage, null) { onStopLoading() }.execute()
                true
            }
            R.id.clear -> {
                onStartLoading()
                StorageCleaner(this, storage) { onStopLoading() }.execute()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun load_cursor(): Cursor {
        val c = storage.getPosts("300")
        c.count
        return c
    }

    fun replace_cursor(c: Cursor) {
        if (!listView.hasTextFilter()) {
            la.changeCursor(c)
        }
        onStopLoading()
    }

    fun cancel_cursor(c: Cursor) {
        c.close()
    }
}
