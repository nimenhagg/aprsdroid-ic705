package org.aprsdroid.app

import org.aprsdroid.app.service.ServiceEventBus

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.aprsdroid.app.data.repository.MessageRepository
import org.aprsdroid.app.ui.component.StationBottomSheetHelper
import org.aprsdroid.app.ui.screen.MessageChatScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.MessageChatViewModel

class MessageActivity : StationHelper() {

    companion object {
        const val TAG = "APRSdroid.Message"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val repository: MessageRepository by lazy { MessageRepository(storage) }
    private val viewModel: MessageChatViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    MessageChatViewModel(repository, ServiceEventBus)
                }
            },
        )[MessageChatViewModel::class.java]
    }
    private val mycall: String by lazy { prefs.getCallSsid() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = targetcall ?: ""

        setContent {
            AprsTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                MessageChatScreen(
                    targetCall = target,
                    myCall = mycall,
                    messages = state.messages,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onCallsignClick = { showTargetStation(target) },
                    onSendMessage = { msg -> sendMessage(msg) },
                    onDeleteMessage = { id ->
                        viewModel.deleteMessage(id, target)
                    },
                    onRestartMessage = { item ->
                        viewModel.restartMessage(item, target) {
                            sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGETX))
                        }
                    },
                    onAbortMessage = { item ->
                        viewModel.abortMessage(item, target) {
                            sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGE))
                        }
                    },
                    onClearAllMessages = {
                        viewModel.deleteAll(target)
                    },
                    onExportLogs = {
                        LogExporter(this, storage, "call = '$target'") {}.execute()
                    }
                )
            }
        }

        if (target.isNotEmpty()) {
            viewModel.refresh(target)
        }

        if (savedInstanceState == null) {
            val message = intent.getStringExtra("message")
            if (message != null && target.isNotEmpty()) {
                Log.d(TAG, "sending message to $target: $message")
                sendMessage(message)
            }
        }
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        targetcall?.let {
            ServiceNotifier.instance.cancelMessage(this, it)
            viewModel.refresh(it)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun showTargetStation(call: String) {
        if (call.isBlank()) return

        var myLat = 0
        var myLon = 0
        val position = storage.getStaPosition(mycall)
        try {
            if (position.count > 0 && position.moveToFirst()) {
                val latIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LAT)
                val lonIndex = position.getColumnIndex(StorageDatabase.Companion.Station.LON)
                if (latIndex >= 0 && lonIndex >= 0) {
                    myLat = position.getInt(latIndex)
                    myLon = position.getInt(lonIndex)
                }
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
            showMessageAction = false
        )
    }

    private fun sendMessage(msg: String) {
        val target = targetcall ?: return
        if (msg.isEmpty()) return
        Log.d(TAG, "sending $msg")

        val cv = ContentValues().apply {
            put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
            put(StorageDatabase.Companion.Message.RETRYCNT, 0)
            put(StorageDatabase.Companion.Message.CALL, target)
            put(StorageDatabase.Companion.Message.MSGID, storage.createMsgId(target))
            put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
            put(StorageDatabase.Companion.Message.TEXT, msg)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            storage.addMessage(cv)
            withContext(Dispatchers.Main) {
                sendMessageBroadcast(target, msg)
                sendBroadcast(AprsService.privateIntent(this@MessageActivity, AprsService.MESSAGE))
                viewModel.refresh(target)

                if (!AprsService.running) {
                    Toast.makeText(this@MessageActivity, R.string.msg_stored_offline, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
