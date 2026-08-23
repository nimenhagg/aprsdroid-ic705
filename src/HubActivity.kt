package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.adapter.StationRecyclerAdapter
import org.aprsdroid.app.model.StationItem
import java.util.concurrent.Executors

class HubActivity : MainRecyclerActivity("hub", R.id.hub) {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: StationRecyclerAdapter

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        onContentViewLoaded()

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty)
        emptyView.setText(R.string.empty_logview)

        adapter = StationRecyclerAdapter(
            context = this,
            mycall = prefs.getCallSsid(),
            targetcall = "",
            onItemClick = { item -> openMessaging(item.call) },
            onItemLongClick = { item, _ ->
                openDetails(item.call)
                true
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        onStartLoading()
        loadData()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
        loadData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    fun loadData() {
        executor.submit {
            val mycall = prefs.getCallSsid()
            var myLat = 0
            var myLon = 0

            val posCursor = storage.getStaPosition(mycall)
            if (posCursor.count > 0 && posCursor.moveToFirst()) {
                val latIdx = posCursor.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIdx = posCursor.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIdx >= 0) myLat = posCursor.getInt(latIdx)
                if (lonIdx >= 0) myLon = posCursor.getInt(lonIdx)
            }
            posCursor.close()

            val cursor = storage.getNeighbors(mycall, myLat, myLon, System.currentTimeMillis() - prefs.getShowAge(), "300")
            val items = StationItem.fromCursor(cursor)

            mainHandler.post {
                adapter.myLat = myLat
                adapter.myLon = myLon
                adapter.submitList(items)
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                onStopLoading()
            }
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
}
