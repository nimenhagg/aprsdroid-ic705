package org.aprsdroid.app.diagnostic

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import org.aprsdroid.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticReportBuilder {
    fun buildBundle(context: Context): File {
        AppLog.flush()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "diagnostic_logs").apply { mkdirs() }
        val report = File(dir, "report.txt")
        report.writeText(buildReport(context), Charsets.UTF_8)
        val zip = File(dir, "aprsdroid_diagnostic_$stamp.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            addFile(out, report, "report.txt")
            AppLog.filesForExport().forEach { file ->
                addFile(out, file, "events/${file.name}")
            }
        }
        return zip
    }

    private fun buildReport(context: Context): String = buildString {
        appendLine("=================================================================")
        appendLine("              APRSdroid Diagnostic Report")
        appendLine("=================================================================")
        appendLine("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())}")
        appendLine("Package Name: ${context.packageName}")
        appendLine("Version Name: ${BuildConfig.VERSION_NAME}")
        appendLine("Version Code: ${BuildConfig.VERSION_CODE}")
        appendLine("Build Type:   ${BuildConfig.BUILD_TYPE}")
        appendLine("Source Rev:   ${BuildConfig.SOURCE_REVISION}")
        appendLine("Bundle Format: structured-jsonl-v1")
        appendLine()

        appendLine("--- [1. Device & OS Environment] ---")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Brand:        ${Build.BRAND}")
        appendLine("Model:        ${Build.MODEL}")
        appendLine("Product:      ${Build.PRODUCT}")
        appendLine("Device:       ${Build.DEVICE}")
        appendLine("Board:        ${Build.BOARD}")
        appendLine("Hardware:     ${Build.HARDWARE}")
        appendLine("Android Ver:  ${Build.VERSION.RELEASE}")
        appendLine("API Level:    ${Build.VERSION.SDK_INT}")
        appendLine("Supported ABI:${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Display:      ${Build.DISPLAY}")
        appendLine("Fingerprint:  ${Build.FINGERPRINT}")
        appendLine()

        appendLine("--- [2. Memory Diagnostics] ---")
        val rt = Runtime.getRuntime()
        appendLine("JVM Free Memory:  ${rt.freeMemory() / (1024 * 1024)} MB")
        appendLine("JVM Total Memory: ${rt.totalMemory() / (1024 * 1024)} MB")
        appendLine("JVM Max Memory:   ${rt.maxMemory() / (1024 * 1024)} MB")
        appendLine()

        appendLine("--- [3. IC-705 Runtime Snapshot] ---")
        val state = AppLog.snapshotState()
        if (state.isEmpty()) appendLine("No runtime snapshot available.") else state.toSortedMap().forEach { (key, value) ->
            appendLine("$key = $value")
        }
        appendLine()

        appendLine("--- [4. Current Android Network Snapshot] ---")
        val networks = NetworkEventLogger.snapshot(context)
        if (networks.isEmpty()) appendLine("No networks visible.") else networks.forEach(::appendLine)
        appendLine()

        appendLine("--- [5. Relevant Preferences] ---")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = listOf(
            "proto",
            "link",
            "activity",
            "ic705.address",
            "ic705.control_port",
            "ic705.username",
            "tcp.server",
            "tcp.sotimeout",
        )
        keys.forEach { key ->
            if (!prefs.contains(key)) return@forEach
            val value = if (key.contains("username", ignoreCase = true)) "[REDACTED]" else prefs.all[key]
            appendLine("$key = $value")
        }
        appendLine()

        appendLine("--- [6. Persistent Event Logs] ---")
        appendLine("Structured JSONL event logs are included under events/ in this ZIP.")
        appendLine("They persist across process restarts and include crash/network/session events.")
        appendLine()
        appendLine("=================================================================")
        appendLine("                       End of Report")
        appendLine("=================================================================")
    }

    private fun addFile(out: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        out.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(out) }
        out.closeEntry()
    }
}
