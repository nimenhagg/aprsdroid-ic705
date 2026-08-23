package org.aprsdroid.app

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutDialog(private val context: Context) {
    fun show() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.aboutview, null, false)

        val linkIds = intArrayOf(
            R.id.about_copyright,
            R.id.about_gpl,
            R.id.about_thanks5,
            R.id.about_thanks6,
            R.id.about_credits
        )
        for (id in linkIds) {
            view.findViewById<TextView>(id)?.movementMethod = LinkMovementMethod.getInstance()
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.about)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
