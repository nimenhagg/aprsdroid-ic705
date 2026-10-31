package org.aprsdroid.app.ui.component

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import org.aprsdroid.app.update.renderReleaseNotesHtml

/**
 * Displays GitHub Release Markdown using Android's native rich-text support.
 *
 * The Markdown-to-HTML conversion escapes raw HTML and sanitizes link URLs before
 * this view receives the rendered content.
 */
@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val html = remember(markdown) { renderReleaseNotesHtml(markdown) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
            }
        },
        update = { view ->
            view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            view.setLineSpacing(0f, 1.15f)
            view.linksClickable = true
            view.movementMethod = LinkMovementMethod.getInstance()
        },
        modifier = modifier.fillMaxWidth(),
    )
}
