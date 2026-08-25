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
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.ui.screen.LogScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.concurrent.Executors

class LogActivity : BaseRecyclerActivity() {
    companion object {
        const val TAG = "APRSdroid.Log"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val itemsState = mutableStateOf<List<LogPostItem>>(emptyList())
    private val isRunningState = mutableStateOf(false)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isRunningState.value = AprsService.running
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        menu_id = R.id.log

        setContent {
            AprsTheme {
                LogScreen(
                    items = itemsState.value,
                    isRunning = isRunningState.value,
                    onBack = {
                        startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        finish()
                    },
                    onOpenHub = {
                        startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    },
                    onOpenMap = {
                        MapModes.startMap(this, prefs, null)
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, PrefsAct::class.java))
                    },
                    onSendPosition = {
                        if (startAprsServiceWithPermissions(AprsService.SERVICE_ONCE)) {
                            isRunningState.value = true
                        }
                    },
                    onToggleTracking = {
                        val running = AprsService.running
                        if (!running) {
                            if (startAprsServiceWithPermissions(AprsService.SERVICE)) {
                                isRunningState.value = true
                            }
                        } else {
                            startService(AprsService.intent(this, AprsService.SERVICE_STOP))
                            isRunningState.value = false
                        }
                    },
                    onItemClick = { item ->
                        if (item.type == StorageDatabase.Companion.Post.TYPE_POST || item.type == StorageDatabase.Companion.Post.TYPE_INCMG) {
                            val call = item.message.split(">")[0]
                            if (call.isNotBlank()) {
                                openDetails(call)
                            }
                        }
                    },
                    onExportLogs = {
                        onStartLoading()
                        LogExporter(this, storage, null) {
                            onStopLoading()
                            loadData()
                        }.execute()
                    },
                    onClearLogs = {
                        onStartLoading()
                        StorageCleaner(this, storage) {
                            onStopLoading()
                            loadData()
                        }.execute()
                    }
                )
            }
        }

        loadData()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        isRunningState.value = AprsService.running
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.SERVICE_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        loadData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
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
                isRunningState.value = AprsService.running
                itemsState.value = items
            }
        }
    }
}
