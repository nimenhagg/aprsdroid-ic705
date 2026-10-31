package org.aprsdroid.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @Test
    fun rendersCommonMarkAndGitHubStrikethrough() {
        val html = renderReleaseNotesHtml(
            """
            ## What's new

            **Bold**, *italic*, and ~~removed~~.

            - First item
            - Second item

            [Release page](https://example.com/releases)
            """.trimIndent(),
        )

        assertTrue(html.contains("<h2>What's new</h2>"))
        assertTrue(html.contains("<strong>Bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<del>removed</del>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("href=\"https://example.com/releases\""))
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
