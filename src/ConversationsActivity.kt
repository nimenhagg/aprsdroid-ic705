package org.aprsdroid.app

import org.aprsdroid.app.service.ServiceEventBus

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.aprsdroid.app.data.repository.MessageRepository
import org.aprsdroid.app.ui.screen.ConversationsScreen
import org.aprsdroid.app.ui.theme.AprsTheme
import org.aprsdroid.app.ui.viewmodel.ConversationsViewModel

class ConversationsActivity : BaseRecyclerActivity() {

    companion object {
        const val TAG = "APRSdroid.Conversations"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val repository: MessageRepository by lazy { MessageRepository(storage) }
    private val viewModel: ConversationsViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    ConversationsViewModel(repository, ServiceEventBus)
                }
            },
        )[ConversationsViewModel::class.java]
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AprsTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                ConversationsScreen(
                    conversations = state.conversations,
                    embeddedTopLevel = false,
                    onBack = { finish() },
                    onOpenConversation = { call -> openMessaging(call) },
                    onDeleteConversation = { call ->
                        viewModel.deleteConversation(call)
                        Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    },
                    onClearAllConversations = {
                        viewModel.deleteAllConversations()
                        Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    },
                    onStartNewConversation = { call -> openMessaging(call) }
                )
            }
        }

        viewModel.refresh()
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, messageReceiver, IntentFilter(AprsService.MESSAGE), ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModel.refresh()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(messageReceiver) } catch (_: Exception) {}
    }
}
