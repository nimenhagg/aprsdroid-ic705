package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Shared host for Compose screens that still need APRS service permission handling
 * and common navigation helpers.
 *
 * UI chrome belongs to each Compose screen; this class intentionally carries no
 * AppCompat ActionBar/menu responsibilities.
 */
abstract class BaseRecyclerActivity : ComponentActivity(), LoadingIndicator, PermissionHelper {

    companion object {
        private const val APRS_SERVICE_PERMISSION = 1020
        private const val STATE_PENDING_SERVICE_ACTION = "pending_service_action"
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    private var pendingServiceAction: String? = null
    override var pendingPermissionAction: Int? = null
    override var pendingPermissions: Set<String> = emptySet()
    override val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> handlePermissionResult(grants) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)
        pendingServiceAction = savedInstanceState?.getString(STATE_PENDING_SERVICE_ACTION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_SERVICE_ACTION, pendingServiceAction)
        savePermissionState(outState)
        super.onSaveInstanceState(outState)
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

    override fun onStartLoading() {}
    override fun onStopLoading() {}

    fun openDetails(call: String) {
        val i = Intent(this, StationActivity::class.java).apply {
            data = call.toUri()
        }
        startActivity(i)
    }

    fun openMessaging(call: String) {
        val i = Intent(this, MessageActivity::class.java).apply {
            data = call.toUri()
        }
        startActivity(i)
    }

    fun openMessageSend(call: String, message: String) {
        val i = Intent(this, MessageActivity::class.java).apply {
            data = call.toUri()
            putExtra("message", message)
        }
        startActivity(i)
    }

    fun sendMessageBroadcast(call: String, message: String) {
        val i = AprsService.privateIntent(this, AprsService.MESSAGETX).apply {
            putExtra(AprsService.DEST, call)
            putExtra(AprsService.BODY, message)
        }
        sendBroadcast(i)
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
