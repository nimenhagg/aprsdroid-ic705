package org.aprsdroid.app.ui.component

import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.graphics.Typeface
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
import org.xml.sax.XMLReader
import java.util.Locale

/**
 * Displays GitHub Release Markdown using Android's native rich-text support.
 *
 * Unknown HTML tags emitted by CommonMark are handled explicitly because Android's
 * Html parser only supports a subset of HTML. Raw HTML is escaped before reaching this view.
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
            view.text = HtmlCompat.fromHtml(
                html,
                HtmlCompat.FROM_HTML_MODE_COMPACT,
                null,
                MarkdownTagHandler(),
            )
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

private class MarkdownTagHandler : Html.TagHandler {
    private data class ListState(
        val ordered: Boolean,
        var nextIndex: Int = 1,
    )

    private class CodeMarker
    private class CodeBlockMarker
    private class StrikeMarker
    private class BoldMarker

    private val lists = mutableListOf<ListState>()
    private var tableCellCount = 0

    override fun handleTag(
        opening: Boolean,
        tag: String,
        output: Editable,
        xmlReader: XMLReader,
    ) {
        when (tag.lowercase(Locale.ROOT)) {
            "ul", "ol" -> {
                if (opening) {
                    lists.add(ListState(ordered = tag.equals("ol", ignoreCase = true)))
                } else {
                    appendLineBreak(output)
                    if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                }
            }
            "li" -> {
                if (opening) {
                    appendLineBreak(output)
                    val depth = (lists.size - 1).coerceAtLeast(0)
                    val list = lists.lastOrNull()
                    val marker = if (list?.ordered == true) {
                        "${list.nextIndex++}. "
                    } else {
                        "• "
                    }
                    output.append("  ".repeat(depth)).append(marker)
                } else {
                    appendLineBreak(output)
                }
            }
            "code" -> {
                if (opening) {
                    startSpan(output, CodeMarker())
                } else {
                    closeSpan<CodeMarker>(output, TypefaceSpan("monospace"))
                }
            }
            "codeblock" -> {
                if (opening) {
                    if (output.isNotEmpty() && output.last() != '\n') output.append("\n\n")
                    startSpan(output, CodeBlockMarker())
                } else {
                    closeSpan<CodeBlockMarker>(output, TypefaceSpan("monospace"))
                    appendLineBreak(output)
                }
            }
            "del" -> {
                if (opening) {
                    startSpan(output, StrikeMarker())
                } else {
                    closeSpan<StrikeMarker>(output, StrikethroughSpan())
                }
            }
            "tr" -> {
                if (opening) {
                    appendLineBreak(output)
                    tableCellCount = 0
                } else {
                    appendLineBreak(output)
                }
            }
            "td", "th" -> {
                if (opening) {
                    if (tableCellCount > 0) output.append("  |  ")
                    tableCellCount++
                    if (tag.equals("th", ignoreCase = true)) startSpan(output, BoldMarker())
                } else if (tag.equals("th", ignoreCase = true)) {
                    closeSpan<BoldMarker>(output, StyleSpan(Typeface.BOLD))
                }
            }
            "hr" -> if (opening) output.append("\n────────────\n")
        }
    }

    private fun appendLineBreak(output: Editable) {
        if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
    }

    private fun startSpan(output: Editable, marker: Any) {
        output.setSpan(marker, output.length, output.length, Spanned.SPAN_MARK_MARK)
    }

    private inline fun <reified T : Any> closeSpan(output: Editable, span: Any) {
        val marker = output.getSpans(0, output.length, T::class.java).lastOrNull() ?: return
        val start = output.getSpanStart(marker)
        output.removeSpan(marker)
        if (start < output.length) {
            output.setSpan(span, start, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
