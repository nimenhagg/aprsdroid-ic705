package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat

abstract class MainListActivity(
    val actname: String,
    menuid: Int
) : LoadingListActivity(), View.OnClickListener {

    init {
        menu_id = menuid
    }

    val singleBtn: Button by lazy { findViewById(R.id.singlebtn) }
    val startstopBtn: Button by lazy { findViewById(R.id.startstopbtn) }

    val miclReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            @Suppress("DEPRECATION")
            setProgress(i.getIntExtra("level", 100) * 99)
        }
    }

    val linkOnOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            setTitleStatus()
        }
    }

    fun onContentViewLoaded() {
        singleBtn.setOnClickListener(this)
        startstopBtn.setOnClickListener(this)
        registerForContextMenu(listView)
    }

    fun setTitleStatus() {
        title = if (AprsService.running) {
            getString(R.string.app_name) + " (" + prefs.getCallSsid() + ")"
        } else {
            getString(R.string.app_name)
        }
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
            startstopBtn.background.setColorFilter(0xffffc0c0.toInt(), PorterDuff.Mode.MULTIPLY)
            startstopBtn.setText(R.string.stoplog)
        } else {
            startstopBtn.background.setColorFilter(0xffc0ffc0.toInt(), PorterDuff.Mode.MULTIPLY)
            startstopBtn.setText(R.string.startlog)
        }
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
