package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import org.aprsdroid.app.ui.screen.SymbolPickerScreen
import org.aprsdroid.app.ui.theme.AprsTheme

class PrefSymbolAct : ComponentActivity() {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentSymbol = prefs.getString("symbol", "/$").takeIf { it.length == 2 } ?: "/$"

        setContent {
            AprsTheme {
                SymbolPickerScreen(
                    initialSymbol = currentSymbol,
                    onSaveSymbol = { chosenSym ->
                        prefs.prefs.edit { putString("symbol", chosenSym) }
                        finish()
                    },
                    onCancel = {
                        onBackPressedDispatcher.onBackPressed()
                    }
                )
            }
        }
    }
}
