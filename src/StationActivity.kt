package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.aprsdroid.app.data.repository.StationRepository
import org.aprsdroid.app.ui.screen.StationDetailScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.StationViewModel

class StationActivity : ComponentActivity() {

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val myCall: String by lazy { prefs.getCallSsid() }

    private val targetCall: String? by lazy {
        intent.dataString?.removePrefix("call:")?.removePrefix("sms:")?.takeIf { it.isNotEmpty() }
            ?: intent.getStringExtra("call")
            ?: intent.getStringExtra("targetcall")
    }

    private val viewModel: StationViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    StationViewModel(StationRepository(storage))
                }
            },
        )[StationViewModel::class.java]
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val updatedCall = intent?.getStringExtra(StorageDatabase.Companion.Post.CALL)
                ?: intent?.getStringExtra("call")
            val target = targetCall
            if (updatedCall == null || target == null ||
                updatedCall.equals(target, ignoreCase = true) ||
                updatedCall.startsWith(target.split("-")[0], ignoreCase = true)
            ) {
                loadData()
            }
        }
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                StationDetailScreen(
                    targetCall = targetCall ?: getString(R.string.unknown_station),
                    stationItem = state.stationItem,
                    ssidList = state.ssidList,
                    postList = state.postList,
                    myLat = state.myLat,
                    myLon = state.myLon,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
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
                        if (!call.equals(targetCall, ignoreCase = true)) {
                            UIHelper.openCallsignDetails(this, call)
                            finish()
                        }
                    },
                )
            }
        }

        loadData()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            IntentFilter(AprsService.UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        loadData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    private fun loadData() {
        val target = targetCall ?: return
        viewModel.requestRefresh(target, myCall)
    }
}
