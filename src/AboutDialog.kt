package org.aprsdroid.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.aprsdroid.app.ui.theme.AprsTheme

class AboutDialog(private val context: Context) {
    fun show() {
        val activity = context as? Activity ?: return
        val dialog = object : ComponentDialog(activity) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val composeView = ComposeView(activity).apply {
                    setContent {
                        AprsTheme {
                            AboutDialogContent(
                                onDismiss = { dismiss() },
                                onOpenGithub = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/nimenhagg/aprsdroid-ic705".toUri()
                                    )
                                    activity.startActivity(intent)
                                }
                            )
                        }
                    }
                }
                setContentView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        dialog.show()
    }
}
