package dev.blamera.ui.markdown

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSafetyTest {
    @Test fun onlyExplicitHttpLinksBecomeInteractiveAnnotations() {
        val value = markdownText(
            "[Source](https://example.com/page?q=a#b) et [HTTP](http://example.com) " +
                "[script](javascript:alert(1)) [fichier](file:///tmp/x) " +
                "[local](content://private/x) [intent](intent://x)"
        )
        assertEquals("Source et HTTP script fichier local intent", value.text)
        assertEquals(
            listOf("https://example.com/page?q=a#b", "http://example.com"),
            value.getLinkAnnotations(0, value.length).map { (it.item as LinkAnnotation.Url).url }
        )
    }

    @Test fun invalidRelativeOrCredentialBearingLinksStayInert() {
        listOf(
            "",
            "/relative",
            "//example.com",
            "https:///missing",
            "https://name:secret@example.com",
            "https://exa mple.com",
            "data:text/html,x",
            "mailto:a@example.com"
        ).forEach { assertNull(it, safeMarkdownUrl(it)) }
        assertEquals("HTTPS://example.com/x", safeMarkdownUrl("HTTPS://example.com/x"))
    }

    @Test fun excessiveTextHasAnExplicitBoundedFallback() {
        val value = "x".repeat(MARKDOWN_MAX_CHARS + 1)
        val rendered = markdownText(value)
        assertEquals("x".repeat(MARKDOWN_MAX_CHARS) + "\n[Réponse tronquée]", rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }

    @Test fun excessiveTreeDepthOrNodeCountFallsBackWithoutLosingBoundedInput() {
        val nested = "> ".repeat(MARKDOWN_MAX_DEPTH + 5) + "**tasse**"
        val many = (1..MARKDOWN_MAX_NODES).joinToString("\n\n") { "*x*" }
        assertEquals(nested, markdownText(nested).text)
        assertEquals(many, markdownText(many).text)
    }
}
