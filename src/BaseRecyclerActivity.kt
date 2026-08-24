package org.aprsdroid.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseRecyclerActivity : AppCompatActivity(), LoadingIndicator, PermissionHelper {

    companion object {
        private const val APRS_SERVICE_PERMISSION = 1020
        private const val STATE_PENDING_SERVICE_ACTION = "pending_service_action"
    }

    var menu_id: Int = 0
    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private val loadingIndicator: View? get() = findViewById(R.id.loading)
    private var pendingServiceAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingServiceAction = savedInstanceState?.getString(STATE_PENDING_SERVICE_ACTION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_SERVICE_ACTION, pendingServiceAction)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun startAprsServiceWithPermissions(action: String): Boolean {
        pendingServiceAction = action
        return checkPermissions(UIHelper.getRequiredPermissions(prefs), APRS_SERVICE_PERMISSION)
    }

    private fun startPendingAprsService() {
        val action = pendingServiceAction ?: return
        pendingServiceAction = null
        ContextCompat.startForegroundService(this, AprsService.intent(this, action))
    }

    fun replaceAct(act: Class<*>) {
        val i = Intent(this, act)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
        finish()
    }

    fun initToolbar(hasBackButton: Boolean = false, titleRes: Int? = null) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            if (hasBackButton) {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                toolbar.setNavigationOnClickListener { finish() }
            }
            if (titleRes != null) {
                supportActionBar?.setTitle(titleRes)
            }
        }
    }

    override fun onStartLoading() {
        loadingIndicator?.visibility = View.VISIBLE
    }

    override fun onStopLoading() {
        loadingIndicator?.visibility = View.GONE
    }

    fun setLongTitle(resId: Int, subtitle: String) {
        val t = getString(resId) + ": " + subtitle
        title = t
        supportActionBar?.title = t
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
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.preferences -> {
                startActivity(Intent(this, PrefsAct::class.java))
                true
            }
            R.id.about -> {
                AboutDialog(this).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // PermissionHelper defaults
    override fun getActionName(action: Int): Int = if (action == APRS_SERVICE_PERMISSION) {
        R.string.startlog
    } else {
        R.string.preferences
    }

    override fun onAllPermissionsGranted(action: Int) {
        if (action == APRS_SERVICE_PERMISSION) {
            startPendingAprsService()
        }
    }

    override fun onPermissionsFailedCancel(action: Int) {
        if (action == APRS_SERVICE_PERMISSION) {
            pendingServiceAction = null
        }
    }
}
