package org.aprsdroid.app.update

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val releaseNotesExtensions: List<Extension> = listOf(
    StrikethroughExtension.create(),
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

/**
 * Converts GitHub Release Markdown into safe HTML for the update dialog's TextView.
 *
 * Raw HTML is escaped and unsafe URL schemes are stripped by the CommonMark renderer.
 */
internal fun renderReleaseNotesHtml(markdown: String): String =
    releaseNotesHtmlRenderer.render(releaseNotesParser.parse(markdown))
