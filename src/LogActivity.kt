package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.aprsdroid.app.data.repository.LogRepository
import org.aprsdroid.app.ui.screen.LogScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.LogViewModel

class LogActivity : BaseRecyclerActivity() {
    companion object {
        const val TAG = "APRSdroid.Log"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val repository: LogRepository by lazy { LogRepository(storage) }
    private val viewModel: LogViewModel by lazy { LogViewModel(repository) }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refresh()
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateServiceState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        menu_id = R.id.log

        setContent {
            AprsTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                LogScreen(
                    items = state.items,
                    isRunning = state.isRunning,
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
                            viewModel.updateServiceState()
                        }
                    },
                    onToggleTracking = {
                        val running = AprsService.running
                        if (!running) {
                            if (startAprsServiceWithPermissions(AprsService.SERVICE)) {
                                viewModel.updateServiceState()
                            }
                        } else {
                            startService(AprsService.intent(this, AprsService.SERVICE_STOP))
                            viewModel.updateServiceState()
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
                            viewModel.refresh()
                        }.execute()
                    },
                    onClearLogs = {
                        onStartLoading()
                        StorageCleaner(this, storage) {
                            onStopLoading()
                            viewModel.refresh()
                        }.execute()
                    }
                )
            }
        }

        viewModel.refresh()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        viewModel.updateServiceState()
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.SERVICE_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModel.refresh()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
    }
}
