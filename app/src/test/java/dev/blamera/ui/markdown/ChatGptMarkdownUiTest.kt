package dev.blamera.ui.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatGptMarkdownUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun responseUsesStyledTextInTheNativeUi() {
        compose.setContent { MaterialTheme { ChatGptMarkdown("Une **tasse bleue**.") } }
        val node = compose.onNodeWithText("Une tasse bleue.").fetchSemanticsNode()
        val value = node.config[SemanticsProperties.Text].single()
        assertTrue(
            value.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    value.text.substring(it.start, it.end) == "tasse bleue"
            }
        )
    }

    @Test fun openingAnExternalLinkRequiresAnExplicitTap() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                }
            ) {
                MaterialTheme { ChatGptMarkdown("[Source](https://example.com/source)") }
            }
        }
        assertTrue(opened.isEmpty())
        compose.onNodeWithText("Source").performClick()
        assertEquals(listOf("https://example.com/source"), opened)
    }
}
