package dev.blamera.ui.markdown

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStructureTest {
    @Test fun headingsListsQuotesAndCodeKeepTheirContentAndReadableStructure() {
        val value = markdownText(
            "# Objet\n\n- Une **tasse**\n- Une anse\n\n" +
                "3. Premier\n4. Second\n\n> Incertain\n\n```kotlin\nval x = 1\n```\n\n`fin`\n\n---"
        )
        assertEquals(
            "Objet\n• Une tasse\n• Une anse\n3. Premier\n4. Second\n\n" +
                "Incertain\n\nval x = 1\n\nfin\n\n────────",
            value.text
        )
        assertTrue(value.spanStyles.any { it.item.fontSize.value == 24f })
        assertTrue(value.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(
            value.spanStyles.any {
                it.item.fontFamily == FontFamily.Monospace &&
                    value.text.substring(it.start, it.end) == "val x = 1"
            }
        )
    }

    @Test fun nestedEmphasisEscapingAndExplicitLineBreaksRemainFaithful() {
        val value = markdownText("**Une *tasse* bleue** et \\*littéral\\*.\nLigne  \nSuite")
        assertEquals("Une tasse bleue et *littéral*. Ligne\nSuite", value.text)
        assertTrue(
            value.spanStyles.any {
                it.item.fontStyle == FontStyle.Italic &&
                    value.text.substring(it.start, it.end) == "tasse"
            }
        )
        assertTrue(value.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertFalse(value.text.contains("**"))
    }

    @Test fun nestedListsAndFollowingParagraphHaveSeparateLines() {
        val value = markdownText("- Parent\n  - Enfant\n\nAprès.\n\n## Détail\n\n### Autre")
        assertEquals("• Parent\n  • Enfant\n\nAprès.\n\nDétail\n\nAutre", value.text)
        assertTrue(value.spanStyles.any { it.item.fontSize.value == 22f })
        assertTrue(value.spanStyles.any { it.item.fontSize.value == 20f })
    }

    @Test fun indentedCodeAndRawHtmlAreInertReadableText() {
        val value = markdownText("    **code**\n\nTexte <b>inerte</b>.\n\n<div>bloc</div>\n")
        assertEquals("**code**\n\nTexte <b>inerte</b>.\n\n<div>bloc</div>", value.text)
        assertEquals(0, value.getLinkAnnotations(0, value.length).size)
        assertTrue(value.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test fun imagesKeepAltTextWithoutCreatingRemoteResources() {
        val value = markdownText("![Une **tasse**](https://example.com/photo.jpg)")
        assertEquals("Une tasse", value.text)
        assertTrue(value.getLinkAnnotations(0, value.length).isEmpty())
    }

    @Test fun incompleteMarkupAndEmptyRepliesRemainReadable() {
        assertEquals("", markdownText("").text)
        assertEquals("Une **tasse", markdownText("Une **tasse").text)
        assertEquals("texte [incomplet", markdownText("texte [incomplet").text)
    }
}
