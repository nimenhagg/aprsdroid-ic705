package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import org.aprsdroid.app.ui.component.AboutDialogContent
import org.aprsdroid.app.ui.theme.AprsTheme

class AboutDialog(private val context: Context) {
    fun show() {
        val dialog = ComponentDialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        (context as? LifecycleOwner)?.let {
            dialog.window?.decorView?.let { decor ->
                ViewTreeLifecycleOwner.set(decor, it)
                (context as? ViewModelStoreOwner)?.let { vmOwner ->
                    ViewTreeViewModelStoreOwner.set(decor, vmOwner)
                }
                (context as? SavedStateRegistryOwner)?.let { savedOwner ->
                    ViewTreeSavedStateRegistryOwner.set(decor, savedOwner)
                }
            }
        }

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
