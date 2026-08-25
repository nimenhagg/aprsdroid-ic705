package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.aprsdroid.app.data.repository.MessageRepository
import org.aprsdroid.app.ui.screen.MessageChatScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.MessageChatViewModel

class MessageActivity : StationHelper(R.string.app_messages) {

    companion object {
        const val TAG = "APRSdroid.Message"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val repository: MessageRepository by lazy { MessageRepository(storage) }
    private val viewModel: MessageChatViewModel by lazy { MessageChatViewModel(repository) }
    private val mycall: String by lazy { prefs.getCallSsid() }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            targetcall?.let { viewModel.refresh(it) }
        }
    }

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
                    onBack = { finish() },
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
                        viewModel.clearAll(target)
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
        ContextCompat.registerReceiver(this, messageReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(messageReceiver) } catch (_: Exception) {}
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
        storage.addMessage(cv)
        sendMessageBroadcast(target, msg)
        sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGE))
        viewModel.refresh(target)

        if (!AprsService.running) {
            Toast.makeText(this, R.string.msg_stored_offline, Toast.LENGTH_SHORT).show()
        }
    }
}
