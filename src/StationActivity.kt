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
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.aprsdroid.app.adapter.LogRecyclerAdapter
import org.aprsdroid.app.adapter.StationRecyclerAdapter
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.model.StationItem
import java.util.concurrent.Executors

class StationActivity : StationHelper(R.string.app_sta), View.OnClickListener {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val mycall: String by lazy { prefs.getCallSsid() }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var ssidRecyclerView: RecyclerView
    private lateinit var postRecyclerView: RecyclerView
    private lateinit var ssidAdapter: StationRecyclerAdapter
    private lateinit var postAdapter: LogRecyclerAdapter

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stationactivity)
        initToolbar(hasBackButton = true, titleRes = R.string.app_sta)

        ssidRecyclerView = findViewById(R.id.ssid_recycler_view)
        postRecyclerView = findViewById(R.id.post_recycler_view)

        ssidAdapter = StationRecyclerAdapter(
            context = this,
            mycall = mycall,
            targetcall = targetcall ?: "",
            onItemClick = { item ->
                if (targetcall == item.call) {
                    callsignAction(R.id.map, item.call)
                } else {
                    UIHelper.openCallsignDetails(this, item.call)
                    finish()
                }
            }
        )

        postAdapter = LogRecyclerAdapter(onItemClick = {})

        ssidRecyclerView.layoutManager = LinearLayoutManager(this)
        ssidRecyclerView.adapter = ssidAdapter

        postRecyclerView.layoutManager = LinearLayoutManager(this)
        postRecyclerView.adapter = postAdapter

        intArrayOf(R.id.map, R.id.qrzcom, R.id.aprsfi).forEach { id ->
            findViewById<View>(id)?.setOnClickListener(this)
        }

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
        val target = targetcall ?: return
        executor.submit {
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

            val ssidCursor = storage.getAllSsids(target)
            val ssidItems = StationItem.fromCursor(ssidCursor)

            val postCursor = storage.getStaPosts(target, "300")
            val postItems = LogPostItem.fromCursor(postCursor)

            mainHandler.post {
                ssidAdapter.myLat = myLat
                ssidAdapter.myLon = myLon
                ssidAdapter.submitList(ssidItems)
                postAdapter.submitList(postItems)
                onStopLoading()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.details)?.isVisible = false
        menu.findItem(R.id.messagesclear)?.isVisible = false
        return true
    }

    override fun onClick(view: View) {
        targetcall?.let { callsignAction(view.id, it) }
    }
}
