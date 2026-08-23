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
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val c = listView.getItemAtPosition(position) as? Cursor ?: return@setOnItemLongClickListener false
            val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
            AlertDialog.Builder(this)
                .setTitle(call)
                .setItems(arrayOf(getString(R.string.delete_conversation))) { _, which ->
                    if (which == 0) {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.delete_conversation)
                            .setMessage(getString(R.string.confirm_delete_messages, call))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val storage = StorageDatabase.open(this)
                                storage.deleteMessages(call)
                                pla.changeCursor(storage.getConversations())
                                android.widget.Toast.makeText(this, R.string.messages_cleared, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                .show()
            true
        }

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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.options_activities, menu)
        menuInflater.inflate(R.menu.options, menu)
        menu.findItem(R.id.conversations)?.isVisible = false
        menu.findItem(R.id.startstopbtn)?.isVisible = false
        menu.findItem(R.id.singlebtn)?.isVisible = false
        menu.findItem(R.id.export)?.isVisible = false
        menu.findItem(R.id.clear)?.title = getString(R.string.clear_all_messages)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.clear_all_messages)
                    .setMessage(R.string.confirm_clear_all_messages)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        val storage = StorageDatabase.open(this)
                        storage.deleteAllMessages()
                        pla.changeCursor(storage.getConversations())
                        android.widget.Toast.makeText(this, R.string.messages_cleared, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.preferences -> {
                startActivity(android.content.Intent(this, PrefsAct::class.java))
                true
            }
            R.id.hub -> {
                startActivity(android.content.Intent(this, HubActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.log -> {
                startActivity(android.content.Intent(this, LogActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.map -> {
                MapModes.startMap(this, prefs, "")
                true
            }
            R.id.about -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.build_version))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: android.view.ContextMenu, v: View, menuInfo: android.view.ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as? android.widget.AdapterView.AdapterContextMenuInfo ?: return
        val c = listView.getItemAtPosition(info.position) as? Cursor ?: return
        val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
        menu.setHeaderTitle(call)
        menu.add(0, 1001, 0, R.string.delete_conversation)
    }

    override fun onContextItemSelected(item: android.view.MenuItem): Boolean {
        val info = item.menuInfo as? android.widget.AdapterView.AdapterContextMenuInfo ?: return false
        val c = listView.getItemAtPosition(info.position) as? Cursor ?: return false
        val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
        if (item.itemId == 1001) {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_conversation)
                .setMessage(getString(R.string.confirm_delete_messages, call))
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    val storage = StorageDatabase.open(this)
                    storage.deleteMessages(call)
                    pla.changeCursor(storage.getConversations())
                    android.widget.Toast.makeText(this, R.string.messages_cleared, android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return true
        }
        return super.onContextItemSelected(item)
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
