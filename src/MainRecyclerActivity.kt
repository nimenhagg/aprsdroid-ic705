package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.view.Menu
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat

abstract class MainRecyclerActivity(
    val actname: String,
    menuid: Int
) : BaseRecyclerActivity(), View.OnClickListener {

    init {
        menu_id = menuid
    }

    val singleBtn: Button by lazy { findViewById(R.id.singlebtn) }
    val startstopBtn: Button by lazy { findViewById(R.id.startstopbtn) }

    val miclReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            // microphone volume level update
        }
    }

    val linkOnOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            setTitleStatus()
        }
    }

    fun onContentViewLoaded() {
        initToolbar(hasBackButton = false)
        singleBtn.setOnClickListener(this)
        startstopBtn.setOnClickListener(this)
    }

    fun setTitleStatus() {
        val titleText = if (AprsService.running) {
            getString(R.string.app_name) + " (" + prefs.getCallSsid() + ")"
        } else {
            getString(R.string.app_name)
        }
        title = titleText
        supportActionBar?.title = titleText
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        setTitleStatus()
        setupButtons(AprsService.running)

        ContextCompat.registerReceiver(this, miclReceiver, IntentFilter(AprsService.MICLEVEL), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, linkOnOffReceiver, IntentFilter(AprsService.SERVICE_STOPPED), ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, linkOnOffReceiver, IntentFilter(AprsService.LINK_OFF), ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, linkOnOffReceiver, IntentFilter(AprsService.LINK_ON), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(miclReceiver) } catch (_: Throwable) {}
        try { unregisterReceiver(linkOnOffReceiver) } catch (_: Throwable) {}
    }

    fun setupButtons(running: Boolean) {
        if (running) {
            startstopBtn.setText(R.string.stoplog)
        } else {
            startstopBtn.setText(R.string.startlog)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val mapItem = menu.findItem(R.id.map)
        if (mapItem != null) {
            val mode = MapModes.defaultMapMode(this, prefs)
            mapItem.setTitle(mode.title ?: getString(R.string.show_map))
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.singlebtn -> {
                startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
                setupButtons(true)
            }
            R.id.startstopbtn -> {
                val isRunning = AprsService.running
                if (!isRunning) {
                    startService(AprsService.intent(this, AprsService.SERVICE))
                } else {
                    startService(AprsService.intent(this, AprsService.SERVICE_STOP))
                }
                setupButtons(!isRunning)
            }
        }
    }

    override fun onStopLoading() {
        super.onStopLoading()
        setupButtons(AprsService.running)
    }
}
