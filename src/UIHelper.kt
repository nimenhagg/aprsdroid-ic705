package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import org.aprsdroid.app.location.LocationSource
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object UIHelper {
    const val TAG = "APRSdroid.UIHelper"

    @JvmStatic
    fun getExportDirectory(ctx: Context): File {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()
        }
        return File(base, "APRSdroid")
    }

    @JvmStatic
    fun shareFile(ctx: Context, file: File, filename: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, file.toString()))
    }

    @JvmStatic
    fun formatDistance(distM: Float, metric: Boolean): String {
        return if (metric) {
            if (distM < 1000) {
                String.format(Locale.US, "%1.0fm", distM)
            } else {
                String.format(Locale.US, "%1.1fkm", distM / 1000f)
            }
        } else {
            val distMiles = distM * 0.000621371192f
            if (distMiles < 0.1f) {
                String.format(Locale.US, "%1.0fft", distM * 3.2808399f)
            } else {
                String.format(Locale.US, "%1.1fmi", distMiles)
            }
        }
    }

    @JvmStatic
    fun formatSpeed(speedMs: Float, metric: Boolean): String {
        return if (metric) {
            String.format(Locale.US, "%1.0fkm/h", speedMs * 3.6f)
        } else {
            String.format(Locale.US, "%1.0fmph", speedMs * 2.23693629f)
        }
    }

    @JvmStatic
    fun formatAltitude(altM: Float, metric: Boolean): String {
        return if (metric) {
            String.format(Locale.US, "%1.0fm", altM)
        } else {
            String.format(Locale.US, "%1.0fft", altM * 3.2808399f)
        }
    }

    @JvmStatic
    fun formatBearing(bearingDeg: Float): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = (((bearingDeg + 11.25f) % 360f) / 22.5f).toInt()
        return directions[index % 16]
    }

    @JvmStatic
    fun formatRelativeTime(context: Context, timeMs: Long): String {
        return android.text.format.DateUtils.getRelativeTimeSpanString(context, timeMs).toString()
    }

    @JvmStatic
    fun openCallsignDetails(context: Context, callsign: String) {
        val uri = Uri.parse(callsign)
        val intent = Intent(context, StationActivity::class.java).apply {
            data = uri
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun openMessageChat(context: Context, callsign: String) {
        val uri = Uri.parse(callsign)
        val intent = Intent(context, MessageActivity::class.java).apply {
            data = uri
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun getRequiredPermissions(prefs: PrefsWrapper): Array<String> {
        val perms = mutableSetOf<String>()
        perms.addAll(AprsBackend.defaultBackendPermissions(prefs))
        perms.addAll(LocationSource.getPermissions(prefs))
        return perms.toTypedArray()
    }
}

object UrlOpener {
    @JvmStatic
    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

class UrlOpenerClickListener(private val ctx: Context, private val url: String) : DialogInterface.OnClickListener {
    override fun onClick(d: DialogInterface, which: Int) {
        UrlOpener.open(ctx, url)
    }
}

class StorageCleaner(private val context: Context, private val storage: StorageDatabase, private val onPost: () -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun execute() {
        executor.submit {
            Log.d("StorageCleaner", "trimming...")
            storage.trimPosts(Long.MAX_VALUE)
            handler.post {
                Log.d("StorageCleaner", "broadcasting...")
                context.sendBroadcast(Intent(AprsService.UPDATE))
                onPost()
            }
        }
    }
}

class MessageCleaner(private val context: Context, private val storage: StorageDatabase, private val call: String) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun execute() {
        executor.submit {
            Log.d("MessageCleaner", "deleting...")
            storage.deleteMessages(call)
            handler.post {
                Log.d("MessageCleaner", "broadcasting...")
                context.sendBroadcast(AprsService.MSG_PRIV_INTENT)
            }
        }
    }
}

class LogExporter(
    private val activity: Activity,
    private val storage: StorageDatabase,
    private val call: String?,
    private val onDone: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun execute() {
        val filename = String.format("aprsdroid-%s.log", SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()))
        val directory = UIHelper.getExportDirectory(activity)
        val file = File(directory, filename)

        executor.submit {
            Log.d("LogExporter", "saving " + filename + " for callsign " + call)
            val c = storage.getExportPosts(call)
            var error: String? = null
            if (c.count == 0) {
                error = activity.getString(R.string.export_empty)
            } else {
                try {
                    directory.mkdirs()
                    val fo = PrintWriter(file)
                    while (c.moveToNext()) {
                        val ts = c.getString(StorageDatabase.Companion.Post.COLUMN_TSS)
                        val tpe = c.getInt(StorageDatabase.Companion.Post.COLUMN_TYPE)
                        val packet = c.getString(StorageDatabase.Companion.Post.COLUMN_MESSAGE)
                        fo.print(ts)
                        fo.print("\t")
                        fo.print(if (tpe == StorageDatabase.Companion.Post.TYPE_INCMG) "RX" else "TX")
                        fo.print("\t")
                        fo.println(packet)
                    }
                    fo.close()
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    c.close()
                }
            }

            handler.post {
                onDone()
                if (error != null) {
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
                } else {
                    UIHelper.shareFile(activity, file, filename)
                }
            }
        }
    }
}
