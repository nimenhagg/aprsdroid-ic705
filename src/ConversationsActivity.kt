package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.aprsdroid.app.model.ConversationItem
import org.aprsdroid.app.ui.screen.ConversationsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.concurrent.Executors

class ConversationsActivity : BaseRecyclerActivity() {

    companion object {
        const val TAG = "APRSdroid.Conversations"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val conversationsState = mutableStateOf<List<ConversationItem>>(emptyList())

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        menu_id = R.id.conversations

        setContent {
            AprsTheme {
                ConversationsScreen(
                    conversations = conversationsState.value,
                    onBack = { finish() },
                    onOpenConversation = { call -> openMessaging(call) },
                    onDeleteConversation = { call ->
                        storage.deleteMessages(call)
                        loadData()
                        Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    },
                    onClearAllConversations = {
                        storage.deleteAllMessages()
                        loadData()
                        Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    },
                    onStartNewConversation = { call -> openMessaging(call) }
                )
            }
        }

        loadData()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, messageReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_EXPORTED)
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
        executor.submit {
            val cursor = storage.getConversations()
            val items = ConversationItem.fromCursor(cursor)
            mainHandler.post {
                conversationsState.value = items
            }
        }
    }
}
