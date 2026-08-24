package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.aprsdroid.app.adapter.MessageRecyclerAdapter
import org.aprsdroid.app.model.MessageItem
import java.util.concurrent.Executors

class MessageActivity : StationHelper(R.string.app_messages),
    View.OnClickListener, View.OnKeyListener, TextWatcher {

    companion object {
        const val TAG = "APRSdroid.Message"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val mycall: String by lazy { prefs.getCallSsid() }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageRecyclerAdapter
    private lateinit var msginput: EditText
    private lateinit var msgsend: Button

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.message_act)
        initToolbar(hasBackButton = true, titleRes = R.string.app_messages)

        recyclerView = findViewById(R.id.recycler_view)
        msginput = findViewById(R.id.msginput)
        msgsend = findViewById(R.id.msgsend)

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.layoutManager = layoutManager

        adapter = MessageRecyclerAdapter(
            context = this,
            mycall = mycall,
            targetcall = targetcall ?: "",
            onItemClick = {},
            onItemLongClick = { item, _ ->
                showMessageOptionsDialog(item)
                true
            }
        )
        recyclerView.adapter = adapter

        msginput.addTextChangedListener(this)
        msginput.setOnKeyListener(this)
        msgsend.setOnClickListener(this)

        onStartLoading()
        loadData()

        val message = intent.getStringExtra("message")
        if (message != null && !targetcall.isNullOrEmpty()) {
            Log.d(TAG, "sending message to $targetcall: $message")
            sendMessage(message)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onResume() {
        super.onResume()
        targetcall?.let { ServiceNotifier.instance.cancelMessage(this, it) }
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
        val target = targetcall ?: return
        executor.submit {
            val cursor = storage.getMessages(target)
            val items = MessageItem.fromCursor(cursor)
            mainHandler.post {
                adapter.submitList(items) {
                    if (items.isNotEmpty()) {
                        recyclerView.scrollToPosition(items.size - 1)
                    }
                }
                onStopLoading()
            }
        }
    }

    private fun showMessageOptionsDialog(item: MessageItem) {
        val items = mutableListOf<String>()
        items.add(getString(android.R.string.copy))
        items.add(getString(R.string.delete_message))
        if (item.type != StorageDatabase.Companion.Message.TYPE_INCOMING) {
            items.add(getString(R.string.msg_restart))
            if (item.type == StorageDatabase.Companion.Message.TYPE_OUT_NEW) {
                items.add(getString(R.string.msg_abort))
            }
        }

        val title = if (item.type == StorageDatabase.Companion.Message.TYPE_INCOMING) {
            getString(R.string.msg_from, targetcall)
        } else {
            getString(R.string.msg_to, targetcall)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(android.R.string.copy) -> {
                        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = android.content.ClipData.newPlainText("APRS message", item.text)
                        clip.setPrimaryClip(clipData)
                        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.delete_message) -> {
                        storage.deleteMessage(item.id)
                        loadData()
                        Toast.makeText(this, R.string.message_deleted, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.msg_restart) -> {
                        val cv = ContentValues().apply {
                            put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
                            put(StorageDatabase.Companion.Message.RETRYCNT, 0)
                            put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
                        }
                        storage.updateMessage(item.id, cv)
                        sendBroadcast(Intent(AprsService.MSG_TX_PRIV_INTENT))
                        loadData()
                    }
                    getString(R.string.msg_abort) -> {
                        storage.updateMessageType(item.id, StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
                        sendBroadcast(Intent(AprsService.MSG_PRIV_INTENT))
                        loadData()
                    }
                }
            }
            .show()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.message)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.messagesclear -> {
                targetcall?.let { call ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.app_messages_clear)
                        .setMessage(getString(R.string.confirm_delete_messages, call))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            storage.deleteMessages(call)
                            loadData()
                            Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                true
            }
            R.id.sta_export -> {
                targetcall?.let { call ->
                    onStartLoading()
                    LogExporter(this, storage, "call = '$call'") { onStopLoading() }.execute()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun afterTextChanged(s: Editable?) {
        msgsend.isEnabled = (msginput.text?.length ?: 0) > 0
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        return if (event?.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
            sendMessage()
            true
        } else false
    }

    fun sendMessage() {
        sendMessage(msginput.text.toString())
    }

    fun sendMessage(msg: String) {
        if (msg.isEmpty() || targetcall.isNullOrEmpty()) return
        Log.d(TAG, "sending $msg")
        msginput.text = null

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
        sendBroadcast(Intent(AprsService.MSG_PRIV_INTENT))
        loadData()

        if (!AprsService.running) {
            Toast.makeText(this, R.string.msg_stored_offline, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.msgsend) {
            sendMessage()
        }
    }
}
