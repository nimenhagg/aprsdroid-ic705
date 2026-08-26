package org.aprsdroid.app.diagnostic

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.aprsdroid.app.R

object LogReportManager {
    fun shareDiagnosticReport(context: Context) {
        Toast.makeText(context, R.string.share_diagnostic_logs_generating, Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val bundle = DiagnosticReportBuilder.buildBundle(context)
                val authority = "${context.packageName}.fileprovider"
                val fileUri = FileProvider.getUriForFile(context, authority, bundle)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_SUBJECT, "APRSdroid Diagnostic Bundle")
                    putExtra(Intent.EXTRA_TEXT, "APRSdroid diagnostic bundle with report and persistent structured event logs.")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_diagnostic_logs_chooser)).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context is Activity) context.runOnUiThread { context.startActivity(chooser) }
                else context.startActivity(chooser)
            } catch (error: Exception) {
                AppLog.e("DIAG", "diagnostic_export_failed", error = error)
                if (context is Activity) {
                    context.runOnUiThread {
                        Toast.makeText(
                            context,
                            context.getString(R.string.share_diagnostic_logs_failed, error.message ?: "Unknown error"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }, "aprsdroid-diagnostic-export").start()
    }
}
