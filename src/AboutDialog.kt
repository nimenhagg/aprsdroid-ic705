package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutDialog(private val context: Context) {
    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.about_title)
            .setMessage(
                context.getString(
                    R.string.about_message,
                    context.getString(R.string.build_revision),
                )
            )
            .setPositiveButton(R.string.about_close, null)
            .setNeutralButton(R.string.about_open_source) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/nimenhagg/aprsdroid-ic705".toUri())
                context.startActivity(intent)
            }
            .show()
    }
}
