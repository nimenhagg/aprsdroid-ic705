package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.ListView
import androidx.core.content.ContextCompat

class StationActivity : StationHelper(R.string.app_sta), View.OnClickListener {

    val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    val postlist: ListView by lazy { findViewById(R.id.postlist) }

    val mycall: String by lazy { prefs.getCallSsid() }
    val pla: StationListAdapter by lazy {
        StationListAdapter(this, prefs, mycall, targetcall ?: "", StationListAdapter.SSIDS)
    }
    val la: PostListAdapter by lazy { PostListAdapter(this) }
    val locReceiver = LocationReceiver2(
        { load_cursor() },
        { replace_cursor(it) },
        { cancel_cursor(it) }
    )

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stationactivity)

        listView.setOnCreateContextMenuListener(this)

        onStartLoading()
        listAdapter = pla
        postlist.adapter = la
        ContextCompat.registerReceiver(this, locReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_EXPORTED)
        locReceiver.startTask(Intent())

        intArrayOf(R.id.map, R.id.qrzcom, R.id.aprsfi).forEach { id ->
            findViewById<View>(id)?.setOnClickListener(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pla.onDestroy()
        try { unregisterReceiver(locReceiver) } catch (_: Exception) {}
        la.changeCursor(null)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.details)?.isVisible = false
        menu.findItem(R.id.messagesclear)?.isVisible = false
        return true
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val c = listView.getItemAtPosition(position) as Cursor
        val call = c.getString(StorageDatabase.Companion.Station.COLUMN_CALL)
        Log.d("StationActivity", "onListItemClick: " + call)

        if (targetcall == call) {
            targetcall?.let { callsignAction(R.id.map, it) }
        } else {
            UIHelper.openCallsignDetails(this, call)
            finish()
        }
    }

    override fun onClick(view: View) {
        targetcall?.let { callsignAction(view.id, it) }
    }

    fun load_cursor(): Cursor {
        val c = storage.getStaPosts(targetcall ?: "", "300")
        c.count
        return c
    }

    fun replace_cursor(c: Cursor) {
        la.changeCursor(c)
    }

    fun cancel_cursor(c: Cursor) {
        c.close()
    }
}
