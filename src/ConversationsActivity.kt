package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.aprsdroid.app.adapter.ConversationRecyclerAdapter
import org.aprsdroid.app.model.ConversationItem
import java.util.concurrent.Executors

class ConversationsActivity : BaseRecyclerActivity(), View.OnClickListener {

    companion object {
        const val TAG = "APRSdroid.Conversations"
    }

    private val storage: StorageDatabase by lazy { StorageDatabase.open(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: ConversationRecyclerAdapter
    private lateinit var newConversationBtn: Button

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        menu_id = R.id.conversations
        setContentView(R.layout.conversations)
        initToolbar(hasBackButton = false, titleRes = R.string.app_messages)

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty)
        emptyView.setText(R.string.msg_empty_list)

        newConversationBtn = findViewById(R.id.new_conversation)
        newConversationBtn.setOnClickListener(this)

        adapter = ConversationRecyclerAdapter(
            context = this,
            onItemClick = { item -> openMessaging(item.call) },
            onItemLongClick = { item, _ ->
                showDeleteConversationDialog(item.call)
                true
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        onStartLoading()
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
                adapter.submitList(items)
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                onStopLoading()
            }
        }
    }

    private fun showDeleteConversationDialog(call: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(call)
            .setItems(arrayOf(getString(R.string.delete_conversation))) { _, which ->
                if (which == 0) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_conversation)
                        .setMessage(getString(R.string.confirm_delete_messages, call))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            storage.deleteMessages(call)
                            loadData()
                            Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_activities, menu)
        menuInflater.inflate(R.menu.options, menu)
        menu.findItem(R.id.conversations)?.isVisible = false
        menu.findItem(R.id.startstopbtn)?.isVisible = false
        menu.findItem(R.id.singlebtn)?.isVisible = false
        menu.findItem(R.id.export)?.isVisible = false
        menu.findItem(R.id.clear)?.title = getString(R.string.clear_all_messages)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.clear_all_messages)
                    .setMessage(R.string.confirm_clear_all_messages)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        storage.deleteAllMessages()
                        loadData()
                        Toast.makeText(this, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
                true
            }
            R.id.hub -> {
                startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.log -> {
                startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                true
            }
            R.id.map -> {
                MapModes.startMap(this, prefs, "")
                true
            }
            R.id.about -> {
                AboutDialog(this).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.new_conversation) {
            newConversation()
        }
    }

    private fun newConversation() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val nmView = inflater.inflate(R.layout.new_message_view, null, false)
        val nmCall = nmView.findViewById<EditText>(R.id.callsign)
        val nmText = nmView.findViewById<EditText>(R.id.message)
        nmCall.filters = arrayOf(InputFilter.AllCaps())

        MaterialAlertDialogBuilder(this)
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
