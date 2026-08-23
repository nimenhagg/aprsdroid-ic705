package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

interface PermissionHelper {
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
            activity.requestPermissions(notGranted.toTypedArray(), action)
            false
        } else {
            onAllPermissionsGranted(action)
            true
        }
    }

    fun handleRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val failedPerms = mutableSetOf<String>()
        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                failedPerms.add(permissions[i])
            }
        }
        if (failedPerms.isNotEmpty()) {
            onPermissionsFailed(requestCode, failedPerms)
        } else {
            onAllPermissionsGranted(requestCode)
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
}
