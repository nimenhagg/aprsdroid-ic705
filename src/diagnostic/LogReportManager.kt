package org.aprsdroid.app.diagnostic

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import org.aprsdroid.app.BuildConfig
import org.aprsdroid.app.R
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object LogReportManager {

    private val executor = Executors.newSingleThreadExecutor()

    fun shareDiagnosticReport(context: Context) {
        Toast.makeText(context, R.string.share_diagnostic_logs_generating, Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val reportContent = buildDiagnosticReport(context)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "aprsdroid_diagnostic_${timeStamp}.txt"

                val logDir = File(context.cacheDir, "diagnostic_logs").apply { mkdirs() }
                val reportFile = File(logDir, fileName)
                reportFile.writeText(reportContent, Charsets.UTF_8)

                val authority = "${context.packageName}.fileprovider"
                val fileUri = FileProvider.getUriForFile(context, authority, reportFile)

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "APRSdroid Diagnostic Log - $timeStamp")
                    putExtra(Intent.EXTRA_TEXT, "APRSdroid Diagnostic Report\nGenerated at: $timeStamp\nApp Version: ${BuildConfig.VERSION_NAME}")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_diagnostic_logs_chooser)).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                if (context is Activity) {
                    context.runOnUiThread {
                        context.startActivity(chooser)
                    }
                } else {
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                if (context is Activity) {
                    context.runOnUiThread {
                        val msg = context.getString(R.string.share_diagnostic_logs_failed, e.message ?: "Unknown error")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun buildDiagnosticReport(context: Context): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())

        sb.append("=================================================================\n")
        sb.append("              APRSdroid System Diagnostic Report                 \n")
        sb.append("=================================================================\n")
        sb.append("Generated At: ").append(now).append("\n")
        sb.append("Package Name: ").append(context.packageName).append("\n")
        sb.append("Version Name: ").append(BuildConfig.VERSION_NAME).append("\n")
        sb.append("Version Code: ").append(BuildConfig.VERSION_CODE).append("\n")
        sb.append("Build Type:   ").append(BuildConfig.BUILD_TYPE).append("\n")
        sb.append("\n")

        // 1. Hardware & OS Information
        sb.append("--- [1. Device & OS Environment] ---\n")
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        sb.append("Brand:        ").append(Build.BRAND).append("\n")
        sb.append("Model:        ").append(Build.MODEL).append("\n")
        sb.append("Product:      ").append(Build.PRODUCT).append("\n")
        sb.append("Device:       ").append(Build.DEVICE).append("\n")
        sb.append("Board:        ").append(Build.BOARD).append("\n")
        sb.append("Hardware:     ").append(Build.HARDWARE).append("\n")
        sb.append("Android Ver:  ").append(Build.VERSION.RELEASE).append("\n")
        sb.append("API Level:    ").append(Build.VERSION.SDK_INT).append("\n")
        sb.append("Supported ABI:").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n")
        sb.append("Display:      ").append(Build.DISPLAY).append("\n")
        sb.append("Fingerprint:  ").append(Build.FINGERPRINT).append("\n")
        sb.append("\n")

        // 2. Memory Diagnostics
        sb.append("--- [2. Memory Diagnostics] ---\n")
        val rt = Runtime.getRuntime()
        val freeMb = rt.freeMemory() / (1024 * 1024)
        val totalMb = rt.totalMemory() / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)
        sb.append("JVM Free Memory:  ").append(freeMb).append(" MB\n")
        sb.append("JVM Total Memory: ").append(totalMb).append(" MB\n")
        sb.append("JVM Max Memory:   ").append(maxMb).append(" MB\n")
        sb.append("\n")

        // 3. SharedPreferences Snapshot (with sensitive fields masked)
        sb.append("--- [3. Preferences Snapshot] ---\n")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val allPrefs = prefs.all
        for ((key, value) in allPrefs.toSortedMap()) {
            val isSensitive = key.lowercase(Locale.ROOT).contains("passcode") ||
                key.lowercase(Locale.ROOT).contains("password") ||
                key.lowercase(Locale.ROOT).contains("secret")
            if (isSensitive) {
                sb.append(key).append(" = [PROTECTED/MASKED]\n")
            } else {
                sb.append(key).append(" = ").append(value).append("\n")
            }
        }
        sb.append("\n")

        // 4. Recent Logcat of the app process
        sb.append("--- [4. Recent Process Logcat Output (Latest 600 lines)] ---\n")
        try {
            val pid = android.os.Process.myPid()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:V"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains(pid.toString()) || l.contains("APRSdroid") || l.contains("AndroidRuntime") || l.contains("FATAL") || l.contains("MapLibre") || l.contains("Ic705")) {
                    lines.add(l)
                }
            }
            reader.close()
            process.destroy()

            val recentLines = if (lines.size > 600) lines.takeLast(600) else lines
            if (recentLines.isEmpty()) {
                sb.append("No matching logcat lines found for PID $pid.\n")
            } else {
                for (logLine in recentLines) {
                    sb.append(logLine).append("\n")
                }
            }
        } catch (e: Exception) {
            sb.append("Failed to dump logcat: ").append(e.message).append("\n")
        }

        sb.append("\n=================================================================\n")
        sb.append("                       End of Report                             \n")
        sb.append("=================================================================\n")

        return sb.toString()
    }
}
