package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

interface PermissionHelper {
    var pendingPermissionAction: Int?
    var pendingPermissions: Set<String>
    val permissionLauncher: ActivityResultLauncher<Array<String>>

    val activity: Activity
        get() = this as Activity

    fun getActionName(action: Int): Int
    fun onAllPermissionsGranted(action: Int)
    fun onPermissionsFailedCancel(action: Int)

    fun checkPermissions(permissions: Array<String>, action: Int): Boolean {
        val notGranted = permissions.filter {
            activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        return if (notGranted.isNotEmpty()) {
            pendingPermissionAction = action
            pendingPermissions = notGranted.toSet()
            permissionLauncher.launch(notGranted.toTypedArray())
            false
        } else {
            pendingPermissionAction = null
            pendingPermissions = emptySet()
            onAllPermissionsGranted(action)
            true
        }
    }

    fun handlePermissionResult(grants: Map<String, Boolean>) {
        val action = pendingPermissionAction ?: return
        pendingPermissionAction = null
        val requestedPermissions = pendingPermissions.ifEmpty { grants.keys }
        pendingPermissions = emptySet()
        val failedPerms = requestedPermissions.filterTo(mutableSetOf()) { permission ->
            grants[permission] != true
        }
        if (failedPerms.isNotEmpty()) {
            onPermissionsFailed(action, failedPerms)
        } else {
            onAllPermissionsGranted(action)
        }
    }

    fun restorePermissionState(savedInstanceState: android.os.Bundle?) {
        pendingPermissionAction = savedInstanceState
            ?.takeIf { it.containsKey(STATE_PENDING_PERMISSION_ACTION) }
            ?.getInt(STATE_PENDING_PERMISSION_ACTION)
        pendingPermissions = savedInstanceState
            ?.getStringArrayList(STATE_PENDING_PERMISSIONS)
            ?.toSet()
            .orEmpty()
    }

    fun savePermissionState(outState: android.os.Bundle) {
        pendingPermissionAction?.let { action ->
            outState.putInt(STATE_PENDING_PERMISSION_ACTION, action)
            outState.putStringArrayList(STATE_PENDING_PERMISSIONS, ArrayList(pendingPermissions))
        }
    }

    fun getPermissionName(permission: String): String {
        return try {
            val pi = activity.packageManager.getPermissionInfo(permission, 0)
            pi.loadLabel(activity.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            permission.split(".").last()
        }
    }

    fun onPermissionsFailed(action: Int, permissions: Set<String>) {
        val sb = StringBuilder(activity.getString(R.string.no_perm_text))
        sb.append("\n\n")
        for (p in permissions) {
            sb.append("- ").append(getPermissionName(p)).append("\n")
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(getActionName(action))
            .setMessage(sb.toString())
            .setPositiveButton(R.string.preferences) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onPermissionsFailedCancel(action)
            }
            .show()
    }

    companion object {
        private const val STATE_PENDING_PERMISSION_ACTION = "pending_permission_action"
        private const val STATE_PENDING_PERMISSIONS = "pending_permissions"
    }
}
