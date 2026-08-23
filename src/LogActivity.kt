package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.adapter.LogRecyclerAdapter
import org.aprsdroid.app.model.LogPostItem
import java.util.concurrent.Executors

class LogActivity : MainRecyclerActivity("log", R.id.log) {
    companion object {
        const val TAG = "APRSdroid.Log"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: LogRecyclerAdapter

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        Log.d(TAG, "starting " + getString(R.string.build_version))
        onContentViewLoaded()

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty)
        emptyView.setText(R.string.empty_logview)

        adapter = LogRecyclerAdapter(
            onItemClick = { item ->
                if (item.type == StorageDatabase.Companion.Post.TYPE_POST || item.type == StorageDatabase.Companion.Post.TYPE_INCMG) {
                    val call = item.message.split(">")[0]
                    Log.d(TAG, "onItemClick: $call")
                    openDetails(call)
                }
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
            val cursor = storage.getPosts("300")
            val items = LogPostItem.fromCursor(cursor)
            mainHandler.post {
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
            R.id.about -> {
                AboutDialog(this).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
