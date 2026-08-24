package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.ui.screen.HubStationScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.concurrent.Executors

class HubActivity : MainRecyclerActivity("hub", R.id.hub) {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val stationsState = mutableStateOf<List<StationItem>>(emptyList())
    private val isRunningState = mutableStateOf(false)
    private val myLatState = mutableIntStateOf(0)
    private val myLonState = mutableIntStateOf(0)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                HubStationScreen(
                    myCall = prefs.getCallSsid(),
                    isRunning = isRunningState.value,
                    stations = stationsState.value,
                    myLat = myLatState.intValue,
                    myLon = myLonState.intValue,
                    onSendPosition = {
                        startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
                        isRunningState.value = true
                    },
                    onToggleTracking = {
                        val running = AprsService.running
                        if (!running) {
                            startService(AprsService.intent(this, AprsService.SERVICE))
                            isRunningState.value = true
                        } else {
                            startService(AprsService.intent(this, AprsService.SERVICE_STOP))
                            isRunningState.value = false
                        }
                    },
                    onStationClick = { item -> openMessaging(item.call) },
                    onStationLongClick = { item -> openDetails(item.call) },
                    onOpenMap = {
                        val mode = MapModes.defaultMapMode(this, prefs)
                        replaceAct(mode.viewClass)
                    },
                    onOpenLogs = { replaceAct(LogActivity::class.java) },
                    onOpenMessages = { startActivity(Intent(this, ConversationsActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, PrefsAct::class.java)) },
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) }
                )
            }
        }

        loadData()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        isRunningState.value = AprsService.running
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
                myLatState.intValue = myLat
                myLonState.intValue = myLon
                isRunningState.value = AprsService.running
                stationsState.value = items
            }
        }
    }
}
