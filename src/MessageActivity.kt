package org.aprsdroid.app

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MessageActivity : StationHelper(R.string.app_messages),
    View.OnClickListener, View.OnKeyListener, TextWatcher {

    companion object {
        const val TAG = "APRSdroid.Message"
    }

    val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    val mycall: String by lazy { prefs.getCallSsid() }
    val pla: MessageListAdapter by lazy {
        MessageListAdapter(this, prefs, mycall, targetcall ?: "")
    }

    val msginput: EditText by lazy { findViewById(R.id.msginput) }
    val msgsend: Button by lazy { findViewById(R.id.msgsend) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.message_act)

        listView.setOnCreateContextMenuListener(this)

        onStartLoading()
        listAdapter = pla

        msginput.addTextChangedListener(this)
        msginput.setOnKeyListener(this)
        msgsend.setOnClickListener(this)

        val message = intent.getStringExtra("message")
        if (message != null && !targetcall.isNullOrEmpty()) {
            Log.d(TAG, "sending message to " + targetcall + ": " + message)
            sendMessage(message)
        }
    }

    override fun onResume() {
        super.onResume()
        targetcall?.let { ServiceNotifier.instance.cancelMessage(this, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pla.onDestroy()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.message)?.isVisible = false
        return true
    }

    fun menuMessageCursor(menuInfo: ContextMenu.ContextMenuInfo): Cursor {
        val info = menuInfo as AdapterContextMenuInfo
        return listView.getItemAtPosition(info.position) as Cursor
    }

    fun messageAction(id: Int, c: Cursor): Boolean {
        val msgId = c.getLong(0)
        val msgType = c.getInt(StorageDatabase.Companion.Message.COLUMN_TYPE)
        return when (id) {
            R.id.copy -> {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                @Suppress("DEPRECATION")
                clip.text = c.getString(StorageDatabase.Companion.Message.COLUMN_TEXT)
                true
            }
            R.id.abort -> {
                if (msgType == StorageDatabase.Companion.Message.TYPE_OUT_NEW) {
                    storage.updateMessageType(msgId, StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
                    sendBroadcast(AprsService.MSG_PRIV_INTENT)
                }
                true
            }
            R.id.resend -> {
                if (msgType != StorageDatabase.Companion.Message.TYPE_INCOMING) {
                    val cv = ContentValues().apply {
                        put(StorageDatabase.Companion.Message.TYPE, StorageDatabase.Companion.Message.TYPE_OUT_NEW)
                        put(StorageDatabase.Companion.Message.RETRYCNT, 0)
                        put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
                    }
                    storage.updateMessage(msgId, cv)
                    sendBroadcast(AprsService.MSG_TX_PRIV_INTENT)
                }
                true
            }
            else -> false
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val c = menuMessageCursor(menuInfo)
        val msgType = c.getInt(StorageDatabase.Companion.Message.COLUMN_TYPE)
        val titleId = if (msgType == StorageDatabase.Companion.Message.TYPE_INCOMING) R.string.msg_from else R.string.msg_to
        menuInflater.inflate(R.menu.context_msg, menu)
        menu.setGroupVisible(R.id.msg_menu_out, msgType != StorageDatabase.Companion.Message.TYPE_INCOMING)
        menu.setHeaderTitle(getString(titleId, c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo
        return if (info != null) {
            messageAction(item.itemId, menuMessageCursor(info))
        } else false
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
        Log.d(TAG, "sending " + msg)
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
        sendBroadcast(AprsService.MSG_PRIV_INTENT)

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
