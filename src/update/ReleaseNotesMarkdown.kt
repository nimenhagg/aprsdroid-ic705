package org.aprsdroid.app.update

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val releaseNotesExtensions: List<Extension> = listOf(
    StrikethroughExtension.create(),
    TablesExtension.create(),
)

private val releaseNotesParser: Parser = Parser.builder()
    .extensions(releaseNotesExtensions)
    .build()

private val releaseNotesHtmlRenderer: HtmlRenderer = HtmlRenderer.builder()
    .extensions(releaseNotesExtensions)
    // Release notes come from GitHub and must never be treated as trusted HTML.
    .escapeHtml(true)
    .sanitizeUrls(true)
    .build()

private val fencedCodeBlockPattern = Regex(
    "<pre><code(?: class=\"[^\"]*\")?>(.*?)</code></pre>",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

/**
 * Converts GitHub Release Markdown into Android-compatible HTML.
 *
 * Raw HTML is escaped, unsafe URL schemes are stripped, and fenced code blocks
 * are converted to explicit line breaks so Android's HTML parser preserves them.
 */
internal fun renderReleaseNotesHtml(markdown: String): String {
    val rendered = releaseNotesHtmlRenderer.render(releaseNotesParser.parse(markdown))
    return fencedCodeBlockPattern.replace(rendered) { match ->
        val code = match.groupValues[1]
            .replace("\t", "    ")
            .replace("\n", "<br>")
        "<codeblock>$code</codeblock>"
    }
}
