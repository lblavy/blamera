package dev.blamera.ui.markdown

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptMarkdownTest {
    @Test fun responseEmphasisIsRenderedWithoutLeakingMarkdownMarkers() {
        val result = markdownText("Une **tasse bleue** et ses *incertitudes*.")
        assertEquals("Une tasse bleue et ses incertitudes.", result.text)
        assertTrue(
            result.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    result.text.substring(it.start, it.end) == "tasse bleue"
            }
        )
    }
}
