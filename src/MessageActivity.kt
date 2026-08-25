package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.aprsdroid.app.model.MessageItem
import org.aprsdroid.app.ui.screen.MessageChatScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.concurrent.Executors

class MessageActivity : StationHelper(R.string.app_messages) {

    companion object {
        const val TAG = "APRSdroid.Message"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val mycall: String by lazy { prefs.getCallSsid() }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val messagesState = mutableStateOf<List<MessageItem>>(emptyList())

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                MessageChatScreen(
                    targetCall = targetcall ?: "",
                    myCall = mycall,
                    messages = messagesState.value,
                    onBack = { finish() },
                    onSendMessage = { msg -> sendMessage(msg) },
                    onDeleteMessage = { id ->
                        storage.deleteMessage(id)
                        loadData()
                    },
                    onRestartMessage = { item ->
                        val cv = ContentValues().apply {
                            put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
                            put(StorageDatabase.Companion.Message.RETRYCNT, 0)
                            put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
                        }
                        storage.updateMessage(item.id, cv)
                        sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGETX))
                        loadData()
                    },
                    onAbortMessage = { item ->
                        storage.updateMessageType(item.id, StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
                        sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGE))
                        loadData()
                    },
                    onClearAllMessages = {
                        targetcall?.let { call ->
                            storage.deleteMessages(call)
                            loadData()
                        }
                    },
                    onExportLogs = {
                        targetcall?.let { call ->
                            LogExporter(this, storage, "call = '$call'") {}.execute()
                        }
                    }
                )
            }
        }

        loadData()

        if (savedInstanceState == null) {
            val message = intent.getStringExtra("message")
            if (message != null && !targetcall.isNullOrEmpty()) {
                Log.d(TAG, "sending message to $targetcall: $message")
                sendMessage(message)
            }
        }
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        targetcall?.let { ServiceNotifier.instance.cancelMessage(this, it) }
        ContextCompat.registerReceiver(this, messageReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_NOT_EXPORTED)
        loadData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(messageReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    fun loadData() {
        val target = targetcall ?: return
        executor.submit {
            val cursor = storage.getMessages(target)
            val items = MessageItem.fromCursor(cursor)
            mainHandler.post {
                messagesState.value = items
            }
        }
    }

    fun sendMessage(msg: String) {
        if (msg.isEmpty() || targetcall.isNullOrEmpty()) return
        Log.d(TAG, "sending $msg")

        val cv = ContentValues().apply {
            put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
            put(StorageDatabase.Companion.Message.RETRYCNT, 0)
            put(StorageDatabase.Companion.Message.CALL, targetcall)
            put(StorageDatabase.Companion.Message.MSGID, storage.createMsgId(targetcall ?: ""))
            put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
            put(StorageDatabase.Companion.Message.TEXT, msg)
        }
        storage.addMessage(cv)
        sendMessageBroadcast(targetcall ?: "", msg)
        sendBroadcast(AprsService.privateIntent(this, AprsService.MESSAGE))
        loadData()

        if (!AprsService.running) {
            Toast.makeText(this, R.string.msg_stored_offline, Toast.LENGTH_SHORT).show()
        }
    }
}
