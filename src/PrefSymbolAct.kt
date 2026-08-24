package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import org.aprsdroid.app.ui.screen.SymbolPickerScreen
import org.aprsdroid.app.ui.theme.AprsTheme

class PrefSymbolAct : AppCompatActivity() {

    private val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentSymbol = prefs.getString("symbol", "/$").takeIf { it.length == 2 } ?: "/$"

        setContent {
            AprsTheme {
                SymbolPickerScreen(
                    initialSymbol = currentSymbol,
                    onSaveSymbol = { chosenSym ->
                        prefs.prefs.edit().putString("symbol", chosenSym).apply()
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}
