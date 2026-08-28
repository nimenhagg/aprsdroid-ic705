package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.aprsdroid.app.data.repository.LogRepository
import org.aprsdroid.app.data.repository.MapStationRepository
import org.aprsdroid.app.data.repository.MessageRepository
import org.aprsdroid.app.data.repository.StationRepository
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
import org.aprsdroid.app.ui.component.StationBottomSheetHelper
import org.aprsdroid.app.ui.navigation.MainNavigationBar
import org.aprsdroid.app.ui.navigation.MainRoutes
import org.aprsdroid.app.ui.navigation.navigateTopLevel
import org.aprsdroid.app.ui.screen.ConversationsScreen
import org.aprsdroid.app.ui.screen.EmbeddedMapScreen
import org.aprsdroid.app.ui.screen.HubStationScreen
import org.aprsdroid.app.ui.screen.LogScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.ConversationsViewModel
import org.aprsdroid.app.ui.viewmodel.HubViewModel
import org.aprsdroid.app.ui.viewmodel.LogViewModel
import org.aprsdroid.app.ui.viewmodel.MapViewModel

class HubActivity : BaseRecyclerActivity() {

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val stationRepository: StationRepository by lazy { StationRepository(storage) }
    private val messageRepository: MessageRepository by lazy { MessageRepository(storage) }
    private val logRepository: LogRepository by lazy { LogRepository(storage) }
    private val mapRepository: MapStationRepository by lazy { MapStationRepository(storage, prefs) }
    private val viewModel: HubViewModel by lazy { HubViewModel(stationRepository, prefs) }
    private val conversationsViewModel: ConversationsViewModel by lazy { ConversationsViewModel(messageRepository) }
    private val logViewModel: LogViewModel by lazy { LogViewModel(logRepository) }
    private val mapViewModel: MapViewModel by lazy { MapViewModel(mapRepository, prefs.getShowObjects()) }
    private val firstRunDialogVisible = mutableStateOf(false)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refresh()
            logViewModel.refresh()
            mapViewModel.refresh()
        }
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            conversationsViewModel.refresh()
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateServiceState()
            logViewModel.updateServiceState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestedStartDestination = MainRoutes.normalizeStartDestination(
            intent.getStringExtra(EXTRA_START_DESTINATION)
        )

        setContent {
            AprsTheme {
                val hubState = viewModel.uiState.collectAsStateWithLifecycle().value
                val conversationsState = conversationsViewModel.uiState.collectAsStateWithLifecycle().value
                val logState = logViewModel.uiState.collectAsStateWithLifecycle().value
                val mapState = mapViewModel.uiState.collectAsStateWithLifecycle().value
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val selectedRoute = currentBackStackEntry?.destination?.route

                LaunchedEffect(requestedStartDestination) {
                    if (requestedStartDestination != MainRoutes.STATIONS) {
                        navController.navigateTopLevel(requestedStartDestination)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = MainRoutes.STATIONS
                        ) {
                            composable(MainRoutes.STATIONS) {
                                HubStationScreen(
                                    myCall = hubState.myCall.ifEmpty { prefs.getCallSsid() },
                                    isRunning = hubState.isRunning,
                                    stations = hubState.stations,
                                    myLat = hubState.myLat,
                                    myLon = hubState.myLon,
                                    onSendPosition = {
                                        if (startAprsServiceWithPermissions(AprsService.SERVICE_ONCE)) {
                                            viewModel.updateServiceState()
                                            logViewModel.updateServiceState()
                                        }
                                    },
                                    onToggleTracking = { toggleTracking() },
                                    onStationClick = { item ->
                                        if (prefs.getStationTapAction() == "details") openDetails(item.call) else openMessaging(item.call)
                                    },
                                    onStationLongClick = { item ->
                                        if (prefs.getStationTapAction() == "details") openMessaging(item.call) else openDetails(item.call)
                                    },
                                    onOpenMap = { navController.navigateTopLevel(MainRoutes.MAP) },
                                    onOpenLogs = { navController.navigateTopLevel(MainRoutes.PACKETS) },
                                    onOpenMessages = { navController.navigateTopLevel(MainRoutes.MESSAGES) },
                                    onOpenSettings = { startActivity(Intent(this@HubActivity, PrefsAct::class.java)) },
                                    onClearLogs = {
                                        StorageCleaner(this@HubActivity, storage) {
                                            refreshTopLevelState()
                                        }.execute()
                                    }
                                )
                            }

                            composable(MainRoutes.MAP) {
                                EmbeddedMapScreen(
                                    prefs = prefs,
                                    stations = mapState.stations,
                                    dataLoading = mapState.isLoading,
                                    showObjects = mapState.showObjects,
                                    myLat = hubState.myLat,
                                    myLon = hubState.myLon,
                                    onShowObjectsChanged = { showObjects ->
                                        mapViewModel.refresh(showObjects)
                                    },
                                    onStationClick = { call -> showMapStation(call) },
                                    onBack = { navController.navigateTopLevel(MainRoutes.STATIONS) },
                                    onOpenPackets = { navController.navigateTopLevel(MainRoutes.PACKETS) },
                                    onOpenSettings = {
                                        startActivity(Intent(this@HubActivity, PrefsAct::class.java))
                                    },
                                    onClearLogs = {
                                        onStartLoading()
                                        StorageCleaner(this@HubActivity, storage) {
                                            onStopLoading()
                                            refreshTopLevelState()
                                        }.execute()
                                    }
                                )
                            }

                            composable(MainRoutes.MESSAGES) {
                                ConversationsScreen(
                                    conversations = conversationsState.conversations,
                                    onBack = { navController.navigateTopLevel(MainRoutes.STATIONS) },
                                    onOpenConversation = { call -> openMessaging(call) },
                                    onDeleteConversation = { call ->
                                        conversationsViewModel.deleteConversation(call)
                                        Toast.makeText(this@HubActivity, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                                    },
                                    onClearAllConversations = {
                                        conversationsViewModel.clearAllConversations()
                                        Toast.makeText(this@HubActivity, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                                    },
                                    onStartNewConversation = { call -> openMessaging(call) }
                                )
                            }

                            composable(MainRoutes.PACKETS) {
                                LogScreen(
                                    items = logState.items,
                                    isRunning = logState.isRunning,
                                    onBack = { navController.navigateTopLevel(MainRoutes.STATIONS) },
                                    onOpenHub = { navController.navigateTopLevel(MainRoutes.STATIONS) },
                                    onOpenMap = { navController.navigateTopLevel(MainRoutes.MAP) },
                                    onOpenSettings = { startActivity(Intent(this@HubActivity, PrefsAct::class.java)) },
                                    onSendPosition = {
                                        if (startAprsServiceWithPermissions(AprsService.SERVICE_ONCE)) {
                                            viewModel.updateServiceState()
                                            logViewModel.updateServiceState()
                                        }
                                    },
                                    onToggleTracking = { toggleTracking() },
                                    onItemClick = { item ->
                                        if (item.type == StorageDatabase.Companion.Post.TYPE_POST || item.type == StorageDatabase.Companion.Post.TYPE_INCMG) {
                                            val call = item.message.split(">")[0]
                                            if (call.isNotBlank()) openDetails(call)
                                        }
                                    },
                                    onExportLogs = {
                                        onStartLoading()
                                        LogExporter(this@HubActivity, storage, null) {
                                            onStopLoading()
                                            logViewModel.refresh()
                                        }.execute()
                                    },
                                    onClearLogs = {
                                        onStartLoading()
                                        StorageCleaner(this@HubActivity, storage) {
                                            onStopLoading()
                                            refreshTopLevelState()
                                        }.execute()
                                    }
                                )
                            }
                        }
                    }

                    MainNavigationBar(
                        selectedRoute = selectedRoute,
                        onDestinationSelected = { route -> navController.navigateTopLevel(route) }
                    )
                }

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
                            refreshTopLevelState()
                        },
                    )
                }
            }
        }

        refreshTopLevelState()
    }

    private fun toggleTracking() {
        val running = AprsService.running
        if (!running) {
            if (startAprsServiceWithPermissions(AprsService.SERVICE)) {
                viewModel.updateServiceState()
                logViewModel.updateServiceState()
            }
        } else {
            startService(AprsService.intent(this, AprsService.SERVICE_STOP))
            viewModel.updateServiceState()
            logViewModel.updateServiceState()
        }
    }

    private fun showMapStation(call: String) {
        var myLat = 0
        var myLon = 0
        val position = storage.getStaPosition(prefs.getCallSsid())
        try {
            if (position.count > 0 && position.moveToFirst()) {
                val latIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIndex >= 0) myLat = position.getInt(latIndex)
                if (lonIndex >= 0) myLon = position.getInt(lonIndex)
            }
        } finally {
            position.close()
        }
        StationBottomSheetHelper.show(this, call, storage, myLat, myLon)
    }

    private fun refreshTopLevelState() {
        viewModel.refresh()
        conversationsViewModel.refresh()
        logViewModel.refresh()
        mapViewModel.refresh()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        viewModel.updateServiceState()
        logViewModel.updateServiceState()
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter(AprsService.UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, messageReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.SERVICE_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LINK_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshTopLevelState()

        if (prefs.getBoolean("firstrun", true) || prefs.getCallsign().isEmpty()) firstRunDialogVisible.value = true
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(messageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
    }
}
