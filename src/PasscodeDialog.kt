package org.aprsdroid.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
import org.aprsdroid.app.ui.theme.AprsTheme

class PasscodeDialog(
    private val act: Activity,
    private val firstrun: Boolean
) : ComponentDialog(act) {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(act) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val initialCall = prefs.getCallsign()
        val initialPass = prefs.getString("passcode", "")

        val composeView = ComposeView(act).apply {
            setContent {
                AprsTheme {
                    PasscodeDialogCompose(
                        initialCallsign = initialCall,
                        initialPasscode = initialPass,
                        firstRun = firstrun,
                        onDismiss = {
                            dismiss()
                            if (firstrun && prefs.getCallsign().isEmpty()) {
                                act.finish()
                            }
                        },
                        onSave = { call, pass ->
                            prefs.prefs.edit {
                                putString("callsign", call)
                                putString("passcode", pass)
                                putBoolean("firstrun", false)
                            }
                            dismiss()
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
