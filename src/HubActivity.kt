package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.aprsdroid.app.location.LocationSource
import org.aprsdroid.app.service.ImmediateLocationCoordinator
import org.aprsdroid.app.diagnostic.AppLog
import org.aprsdroid.app.update.GitHubUpdateChecker
import org.aprsdroid.app.update.UpdateCheckResult

class HubActivity : BaseRecyclerActivity() {

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"
        private const val MAP_LOCATION_PERMISSION = 1021
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val stationRepository: StationRepository by lazy { StationRepository(storage) }
    private val messageRepository: MessageRepository by lazy { MessageRepository(storage) }
    private val logRepository: LogRepository by lazy { LogRepository(storage) }
    private val mapRepository: MapStationRepository by lazy { MapStationRepository(storage, PrefsWrapper(applicationContext)) }
    private val viewModel: HubViewModel by lazy {
        ViewModelProvider(this, viewModelFactory {
            initializer { HubViewModel(stationRepository, PrefsWrapper(applicationContext)) }
        })[HubViewModel::class.java]
    }
    private val conversationsViewModel: ConversationsViewModel by lazy { ConversationsViewModel(messageRepository) }
    private val logViewModel: LogViewModel by lazy {
        ViewModelProvider(this, viewModelFactory {
            initializer { LogViewModel(logRepository) }
        })[LogViewModel::class.java]
    }
    private val mapViewModel: MapViewModel by lazy {
        ViewModelProvider(this, viewModelFactory {
            initializer { MapViewModel(mapRepository, prefs.getShowObjects()) }
        })[MapViewModel::class.java]
    }
    private val firstRunDialogVisible = mutableStateOf(false)
    private val pendingStartDestination = mutableStateOf<String?>(null)
    private val updateAvailableState = mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null)
    private val mapCurrentLocation = mutableStateOf<Location?>(null)
    private var activeRoute: String = MainRoutes.STATIONS

    private val mapLocationCoordinator by lazy {
        ImmediateLocationCoordinator(
            locationManagerProvider = {
                getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            },
            handler = Handler(Looper.getMainLooper()),
            onLocation = { location ->
                runOnUiThread { mapCurrentLocation.value = location }
            },
            logTag = "APRSdroid.MapLocation",
        )
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVisibleState()
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
        consumeNavigationIntent(intent)
        GitHubUpdateChecker.checkAutomatically(BuildConfig.VERSION_NAME) { update ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    AppLog.i("UPDATE", "automatic_check_update_available", mapOf("current" to update.current, "latest" to update.latest))
                    updateAvailableState.value = update
                }
            }
        }

        setContent {
            AprsTheme {
                val hubState = viewModel.uiState.collectAsStateWithLifecycle().value
                val conversationsState = conversationsViewModel.uiState.collectAsStateWithLifecycle().value
                val logState = logViewModel.uiState.collectAsStateWithLifecycle().value
                val mapState = mapViewModel.uiState.collectAsStateWithLifecycle().value
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val selectedRoute = currentBackStackEntry?.destination?.route
                val requestedStartDestination = pendingStartDestination.value

                LaunchedEffect(selectedRoute) {
                    activeRoute = selectedRoute ?: MainRoutes.STATIONS
                    refreshVisibleState()
                }

                LaunchedEffect(requestedStartDestination) {
                    if (requestedStartDestination != null && requestedStartDestination != MainRoutes.STATIONS) {
                        navController.navigateTopLevel(requestedStartDestination)
                    }
                    pendingStartDestination.value = null
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = MainRoutes.STATIONS,
                            // Bottom-navigation roots are peers, not a hierarchy. Let the
                            // NavigationBar selection indicator carry the motion instead of
                            // translating/fading an entire heavy screen (MapView/LazyColumn).
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None }
                        ) {
                            composable(MainRoutes.STATIONS) {
                                HubStationScreen(
                                    myCall = hubState.myCall.ifEmpty { prefs.getCallSsid() },
                                    isRunning = hubState.isRunning,
                                    serviceStatus = hubState.serviceStatus,
                                    stations = hubState.stations,
                                    searchQuery = hubState.searchQuery,
                                    isSearching = hubState.isSearching,
                                    onSearchQueryChanged = viewModel::setSearchQuery,
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
                                        if (prefs.getStationTapAction() == "details") {
                                            openDetails(item.call)
                                        } else {
                                            openMessaging(item.call)
                                        }
                                    },
                                    onStationLongClick = { item ->
                                        if (prefs.getStationTapAction() == "details") {
                                            openMessaging(item.call)
                                        } else {
                                            openDetails(item.call)
                                        }
                                    },
                                    onOpenMap = { navController.navigateTopLevel(MainRoutes.MAP) },
                                    onOpenLogs = { navController.navigateTopLevel(MainRoutes.PACKETS) },
                                    onOpenMessages = { navController.navigateTopLevel(MainRoutes.MESSAGES) },
                                    onOpenSettings = { startActivity(Intent(this@HubActivity, PrefsAct::class.java)) },
                                    onClearLogs = {
                                        StorageCleaner(this@HubActivity, storage) { refreshTopLevelState() }.execute()
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
                                    currentLocation = mapCurrentLocation.value,
                                    onShowObjectsChanged = { showObjects -> mapViewModel.refresh(showObjects) },
                                    onStationClick = { call ->
                                        showMapStation(call) { target -> openMessaging(target) }
                                    },
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
                                    },
                                    onRequestCurrentLocation = { requestMapCurrentLocation() }
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

                    if (MainRoutes.isTopLevel(selectedRoute)) {
                        MainNavigationBar(
                            selectedRoute = selectedRoute,
                            onDestinationSelected = { route -> navController.navigateTopLevel(route) }
                        )
                    }
                }

                updateAvailableState.value?.let { update ->
                    AlertDialog(
                        onDismissRequest = { updateAvailableState.value = null },
                        title = {
                            Text(stringResource(R.string.update_available_title, update.latest))
                        },
                        text = {
                            Text(stringResource(R.string.update_available_message, update.current))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    updateAvailableState.value = null
                                    UrlOpener.open(this@HubActivity, update.releaseUrl)
                                },
                            ) {
                                Text(stringResource(R.string.update_open_release))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateAvailableState.value = null }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationIntent(intent)
    }

    private fun consumeNavigationIntent(intent: Intent?) {
        pendingStartDestination.value = intent
            ?.getStringExtra(EXTRA_START_DESTINATION)
            ?.let(MainRoutes::normalizeStartDestination)
    }

    private fun requestMapCurrentLocation() {
        if (checkPermissions(LocationSource.getPermissions(prefs), MAP_LOCATION_PERMISSION)) {
            mapLocationCoordinator.triggerDeviceLocation()
        }
    }

    override fun onAllPermissionsGranted(action: Int) {
        if (action == MAP_LOCATION_PERMISSION) {
            mapLocationCoordinator.trigger(LocationSource.instanciateLocation(this, prefs))
        } else {
            super.onAllPermissionsGranted(action)
        }
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

    private fun showMapStation(
        call: String,
        showMessageAction: Boolean = true,
        onSendMessageRequested: ((String) -> Unit)? = null
    ) {
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
        StationBottomSheetHelper.show(
            context = this,
            call = call,
            db = storage,
            myLat = myLat,
            myLon = myLon,
            showMessageAction = showMessageAction,
            onSendMessageRequested = onSendMessageRequested
        )
    }

    private fun refreshTopLevelState() {
        conversationsViewModel.refresh()
        refreshVisibleState()
    }

    private fun refreshVisibleState() {
        when (activeRoute) {
            MainRoutes.STATIONS -> viewModel.refresh()
            MainRoutes.MAP -> {
                // The map also uses the hub's own-position state for its locate button.
                viewModel.refresh(includeStations = false)
                mapViewModel.refresh()
            }
            MainRoutes.PACKETS -> logViewModel.refresh()
        }
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
        ContextCompat.registerReceiver(this, serviceStateReceiver, IntentFilter(AprsService.LIVE_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
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
