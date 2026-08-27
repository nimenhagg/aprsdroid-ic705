package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.aprsdroid.app.data.repository.StationRepository
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
import org.aprsdroid.app.ui.screen.HubStationScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.HubViewModel

class HubActivity : BaseRecyclerActivity() {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val repository: StationRepository by lazy { StationRepository(storage) }
    private val viewModel: HubViewModel by lazy { HubViewModel(repository, prefs) }
    private val firstRunDialogVisible = mutableStateOf(false)

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

        setContent {
            AprsTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                HubStationScreen(
                    myCall = state.myCall.ifEmpty { prefs.getCallSsid() },
                    isRunning = state.isRunning,
                    stations = state.stations,
                    myLat = state.myLat,
                    myLon = state.myLon,
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
                    onStationClick = { item ->
                        if (prefs.getStationTapAction() == "details") openDetails(item.call) else openMessaging(item.call)
                    },
                    onStationLongClick = { item ->
                        if (prefs.getStationTapAction() == "details") openMessaging(item.call) else openDetails(item.call)
                    },
                    onOpenMap = {
                        val mode = MapModes.defaultMapMode(this, prefs)
                        startActivity(Intent(this, mode.viewClass))
                    },
                    onOpenLogs = { startActivity(Intent(this, LogActivity::class.java)) },
                    onOpenMessages = { startActivity(Intent(this, ConversationsActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, PrefsAct::class.java)) },
                    onClearLogs = {
                        StorageCleaner(this, storage) { viewModel.refresh() }.execute()
                    }
                )

                if (firstRunDialogVisible.value) {
                    PasscodeDialogCompose(
                        initialCallsign = prefs.getCallsign(),
                        initialPasscode = prefs.getString("passcode", ""),
                        firstRun = true,
                        onDismiss = {
                            firstRunDialogVisible.value = false
                            if (prefs.getCallsign().isEmpty()) finish()
                        },
                        onSave = { call, pass ->
                            prefs.prefs.edit {
                                putString("callsign", call)
                                putString("passcode", pass)
                                putBoolean("firstrun", false)
                            }
                            firstRunDialogVisible.value = false
                            viewModel.refresh()
                        },
                    )
                }
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

        if (prefs.getBoolean("firstrun", true) || prefs.getCallsign().isEmpty()) {
            firstRunDialogVisible.value = true
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
    }
}
