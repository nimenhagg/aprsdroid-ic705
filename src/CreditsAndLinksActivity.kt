package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.screen.CreditsAndLinksScreen
import org.aprsdroid.app.ui.theme.AprsTheme

class CreditsAndLinksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AprsTheme {
                CreditsAndLinksScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenUrl = { url -> UrlOpener.open(this@CreditsAndLinksActivity, url) },
                )
            }
        }
    }
}
