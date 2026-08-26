package org.aprsdroid.app.diagnostic

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small persistent structured logger for field diagnostics.
 *
 * Critical events are written both to Android logcat and to rotating JSONL files in
 * noBackupFilesDir so they survive process death/restarts and can be exported later.
 */
object AppLog {
    private const val MAX_FILE_BYTES = 1_048_576L
    private const val MAX_ARCHIVES = 4
    private const val CURRENT = "events-current.jsonl"
    private const val DIR = "diagnostic_events"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aprsdroid-log").apply { isDaemon = true }
    }
    private val initialized = AtomicBoolean(false)
    private val stateLock = Any()
    private val state = linkedMapOf<String, String>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        installCrashHandler()
        i("APP", "process_start", mapOf("pid" to android.os.Process.myPid()))
    }

    fun setState(key: String, value: Any?) {
        synchronized(stateLock) {
            if (value == null) state.remove(key) else state[key] = sanitizeValue(key, value.toString())
        }
    }

    fun snapshotState(): Map<String, String> = synchronized(stateLock) { LinkedHashMap(state) }

    fun d(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) =
        write(Log.DEBUG, tag, event, fields, null)

    fun i(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) =
        write(Log.INFO, tag, event, fields, null)

    fun w(tag: String, event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        write(Log.WARN, tag, event, fields, error)

    fun e(tag: String, event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        write(Log.ERROR, tag, event, fields, error)

    fun filesForExport(): List<File> {
        val dir = appContext?.let(::logDir) ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun flush(timeoutMillis: Long = 750L) {
        val marker = executor.submit { Unit }
        runCatching { marker.get(timeoutMillis, TimeUnit.MILLISECONDS) }
    }

    private fun write(
        priority: Int,
        tag: String,
        event: String,
        fields: Map<String, Any?>,
        error: Throwable?,
    ) {
        val safeTag = if (tag.startsWith("APRSdroid")) tag else "APRSdroid.$tag"
        val message = buildString {
            append(event)
            if (fields.isNotEmpty()) {
                append(' ')
                append(fields.entries.joinToString(" ") { (k, v) -> "$k=${sanitizeValue(k, v?.toString() ?: "null")}" })
            }
        }
        if (error == null) Log.println(priority, safeTag, message) else Log.println(priority, safeTag, "$message\n${Log.getStackTraceString(error)}")

        val context = appContext ?: return
        val record = buildJsonLine(priority, safeTag, event, fields, error)
        executor.execute {
            runCatching { appendRecord(context, record) }
        }
    }

    private fun buildJsonLine(
        priority: Int,
        tag: String,
        event: String,
        fields: Map<String, Any?>,
        error: Throwable?,
    ): String {
        val fieldJson = fields.entries.joinToString(",") { (key, value) ->
            "\"${escape(key)}\":\"${escape(sanitizeValue(key, value?.toString() ?: "null"))}\""
        }
        val errorJson = error?.let {
            ",\"error_type\":\"${escape(it.javaClass.name)}\",\"error\":\"${escape(it.message ?: "")}\",\"stack\":\"${escape(Log.getStackTraceString(it))}\""
        }.orEmpty()
        return buildString {
            append('{')
            append("\"time\":\"").append(escape(utcNow())).append("\",")
            append("\"elapsed_ms\":").append(SystemClock.elapsedRealtime()).append(',')
            append("\"level\":\"").append(levelName(priority)).append("\",")
            append("\"tag\":\"").append(escape(tag)).append("\",")
            append("\"event\":\"").append(escape(event)).append("\",")
            append("\"thread\":\"").append(escape(Thread.currentThread().name)).append("\",")
            append("\"fields\":{").append(fieldJson).append('}')
            append(errorJson)
            append('}')
        }
    }

    private fun appendRecord(context: Context, record: String) {
        val dir = logDir(context)
        val current = File(dir, CURRENT)
        if (current.exists() && current.length() >= MAX_FILE_BYTES) rotate(dir)
        OutputStreamWriter(FileOutputStream(current, true), Charsets.UTF_8).use { writer ->
            writer.append(record).append('\n')
        }
    }

    private fun rotate(dir: File) {
        File(dir, "events-$MAX_ARCHIVES.jsonl").delete()
        for (index in MAX_ARCHIVES - 1 downTo 1) {
            val src = File(dir, "events-$index.jsonl")
            if (src.exists()) src.renameTo(File(dir, "events-${index + 1}.jsonl"))
        }
        val current = File(dir, CURRENT)
        if (current.exists()) current.renameTo(File(dir, "events-1.jsonl"))
    }

    private fun logDir(context: Context): File = File(context.noBackupFilesDir, DIR).apply { mkdirs() }

    private fun installCrashHandler() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e(
                    "CRASH",
                    "uncaught_exception",
                    mapOf("thread" to thread.name),
                    throwable,
                )
                flush(1200L)
            } finally {
                previousCrashHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun sanitizeValue(key: String, raw: String): String {
        val lower = key.lowercase(Locale.ROOT)
        return when {
            lower.contains("password") || lower.contains("passcode") || lower.contains("secret") || lower.contains("token") -> "[REDACTED]"
            lower.contains("latitude") || lower.contains("longitude") || lower == "lat" || lower == "lon" -> "[REDACTED]"
            else -> raw.take(2048)
        }
    }

    private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private fun levelName(priority: Int): String = when (priority) {
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        else -> priority.toString()
    }

    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append(String.format(Locale.US, "\\u%04x", c.code)) else append(c)
            }
        }
    }
}
