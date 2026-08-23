package org.aprsdroid.app

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView

class ConversationsActivity : LoadingListActivity(), View.OnClickListener {

    companion object {
        const val TAG = "APRSdroid.Conversations"
    }

    val mycall: String get() = prefs.getCallSsid()
    val pla: ConversationListAdapter by lazy { ConversationListAdapter(this, prefs) }
    val newConversationBtn: Button by lazy { findViewById(R.id.new_conversation) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        menu_id = R.id.conversations
        setContentView(R.layout.conversations)

        registerForContextMenu(listView)
        newConversationBtn.setOnClickListener(this)
        listView.setOnCreateContextMenuListener(this)

        onStartLoading()
        listAdapter = pla
        listView.isTextFilterEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        pla.onDestroy()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val c = listView.getItemAtPosition(position) as Cursor
        val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
        openMessaging(call)
    }

    override fun onClick(view: View) {
        if (view.id == R.id.new_conversation) {
            newConversation()
        }
    }

    fun newConversation() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val nmView = inflater.inflate(R.layout.new_message_view, null, false)
        val nmCall = nmView.findViewById<EditText>(R.id.callsign)
        val nmText = nmView.findViewById<EditText>(R.id.message)
        nmCall.filters = arrayOf(InputFilter.AllCaps())

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.msg_send_new))
            .setView(nmView)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                openMessageSend(nmCall.text.toString(), nmText.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
}
