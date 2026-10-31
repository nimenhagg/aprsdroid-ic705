package org.aprsdroid.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @Test
    fun rendersCommonMarkAndGitHubExtensions() {
        val html = renderReleaseNotesHtml(
            """
            ## What's new

            **Bold**, *italic*, ~~removed~~, and `APDRMO`.

            - First item
            - Second item

            [Release page](https://example.com/releases)

            | Feature | Status |
            | --- | --- |
            | Markdown | Supported |
            """.trimIndent(),
        )

        assertTrue(html.contains("<h2>What's new</h2>"))
        assertTrue(html.contains("<strong>Bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<del>removed</del>"))
        assertTrue(html.contains("<code>APDRMO</code>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("href=\"https://example.com/releases\""))
        assertTrue(html.contains("<table>"))
    }

    @Test
    fun preservesFencedCodeBlockLineBreaksForAndroid() {
        val html = renderReleaseNotesHtml(
            """
            ```kotlin
            val callsign = "BD3QID"
            val enabled = true
            ```
            """.trimIndent(),
        )

        assertTrue(html.contains("<codeblock>"))
        assertTrue(html.contains("<br>"))
        assertTrue(html.contains("<br>val enabled = true"))
    }

    @Test
    fun escapesRawHtmlAndRemovesUnsafeLinkSchemes() {
        val html = renderReleaseNotesHtml(
            """
            <script>alert('xss')</script>

            [unsafe](javascript:alert%281%29)
            """.trimIndent(),
        )

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("href=\"javascript:"))
    }
}
