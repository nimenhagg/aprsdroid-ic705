package org.aprsdroid.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.aprsdroid.app.ui.theme.AprsTheme

class AboutDialog(private val context: Context) {
    fun show() {
        val dialog = Dialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val composeView = ComposeView(context).apply {
            setContent {
                AprsTheme {
                    AboutDialogContent(
                        onDismiss = { dialog.dismiss() },
                        onOpenGithub = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nimenhagg/aprsdroid-ic705"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        dialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
    }
}
