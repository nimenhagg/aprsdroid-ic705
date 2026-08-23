package org.aprsdroid.app

import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View

abstract class LoadingListActivity : ListActivity(), LoadingIndicator {

    var menu_id: Int = 0
    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val loadingIndicator: View? get() = findViewById(R.id.loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStartLoading() {
        loadingIndicator?.visibility = View.VISIBLE
    }

    override fun onStopLoading() {
        loadingIndicator?.visibility = View.GONE
    }

    fun setLongTitle(resId: Int, subtitle: String) {
        title = getString(resId) + ": " + subtitle
    }

    fun openDetails(call: String) {
        val i = Intent(this, StationActivity::class.java).apply {
            data = Uri.parse(call)
        }
        startActivity(i)
    }

    fun openMessaging(call: String) {
        val i = Intent(this, MessageActivity::class.java).apply {
            data = Uri.parse(call)
        }
        startActivity(i)
    }

    fun openMessageSend(call: String, message: String) {
        val i = Intent(this, MessageActivity::class.java).apply {
            data = Uri.parse(call)
            putExtra("message", message)
        }
        startActivity(i)
    }

    fun sendMessageBroadcast(call: String, message: String) {
        val i = Intent(AprsService.MESSAGETX).apply {
            putExtra(AprsService.DEST, call)
            putExtra(AprsService.BODY, message)
        }
        sendBroadcast(i)
    }

    fun trackOnMap(call: String) {
        val text = getString(R.string.map_track_call, call)
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
        MapModes.startMap(this, prefs, call)
    }

    fun callsignAction(id: Int, call: String): Boolean {
        return when (id) {
            R.id.details -> {
                openDetails(call)
                true
            }
            R.id.message -> {
                openMessaging(call)
                true
            }
            R.id.map -> {
                trackOnMap(call)
                true
            }
            R.id.aprsfi -> {
                val url = String.format("https://aprs.fi/info/a/%s?utm_source=aprsdroid&utm_medium=inapp&utm_campaign=aprsfi", call)
                UrlOpener.open(this, url)
                true
            }
            R.id.qrzcom -> {
                val basecall = call.split(Regex("[- ]+"))[0]
                val url = "https://qrz.com/db/" + basecall
                UrlOpener.open(this, url)
                true
            }
            else -> false
        }
    }
}
