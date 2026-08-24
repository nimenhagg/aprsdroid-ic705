package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.ui.screen.StationDetailScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.concurrent.Executors

class StationActivity : AppCompatActivity() {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val mycall: String by lazy { prefs.getCallSsid() }
    private val executor = Executors.newSingleThreadExecutor()

    private val targetcall: String? by lazy {
        intent.dataString?.removePrefix("call:")?.removePrefix("sms:")?.takeIf { it.isNotEmpty() }
            ?: intent.getStringExtra("call")
            ?: intent.getStringExtra("targetcall")
    }

    private var currentStationItem by mutableStateOf<StationItem?>(null)
    private var currentSsidList by mutableStateOf<List<StationItem>>(emptyList())
    private var currentPostList by mutableStateOf<List<LogPostItem>>(emptyList())
    private var myLatState by mutableIntStateOf(0)
    private var myLonState by mutableIntStateOf(0)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                StationDetailScreen(
                    targetCall = targetcall ?: "未知台站",
                    stationItem = currentStationItem,
                    ssidList = currentSsidList,
                    postList = currentPostList,
                    myLat = myLatState,
                    myLon = myLonState,
                    onBack = { finish() },
                    onSendMessage = { call ->
                        UIHelper.openMessageChat(this, call)
                    },
                    onOpenMap = { call ->
                        val intent = Intent(this, MapAct::class.java).apply {
                            putExtra("call", call)
                        }
                        startActivity(intent)
                    },
                    onOpenQrz = { call ->
                        val baseCall = call.split("-")[0]
                        val intent = Intent(Intent.ACTION_VIEW, "https://www.qrz.com/db/$baseCall".toUri())
                        startActivity(intent)
                    },
                    onOpenAprsFi = { call ->
                        val intent = Intent(Intent.ACTION_VIEW, "https://aprs.fi/info/a/$call".toUri())
                        startActivity(intent)
                    },
                    onSelectSsid = { call ->
                        if (!call.equals(targetcall, ignoreCase = true)) {
                            UIHelper.openCallsignDetails(this, call)
                            finish()
                        }
                    }
                )
            }
        }

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

    private fun loadData() {
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

            val targetItem = ssidItems.find { it.call.equals(target, ignoreCase = true) }
                ?: ssidItems.firstOrNull()

            runOnUiThread {
                myLatState = myLat
                myLonState = myLon
                currentStationItem = targetItem
                currentSsidList = ssidItems
                currentPostList = postItems
            }
        }
    }
}
