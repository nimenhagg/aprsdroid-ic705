package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

class LocationReceiver2<Result>(
    private val bgTask: (Intent) -> Result,
    private val finishTask: (Result) -> Unit,
    private val cancelTask: (Result) -> Unit
) : BroadcastReceiver() {

    private var pending = 0
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startTask(i: Intent) {
        pending += 1
        if (pending == 1) {
            executor.submit {
                val r = bgTask(i)
                mainHandler.post {
                    finishTask(r)
                    if (pending > 1) {
                        Log.d("LocationReceiver2", "rerunning...")
                        pending = 0
                        startTask(i)
                    } else {
                        pending = 0
                    }
                }
            }
        }
    }

    override fun onReceive(ctx: Context, i: Intent) {
        startTask(i)
    }
}
