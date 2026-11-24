package dev.blamera.chatgpt.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatGptComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invalidPreviewHasALocalReadableFallbackWithoutARequest() {
        compose.setContent {
            MaterialTheme { PreparedJpegImage(byteArrayOf(1), "Objet", Modifier) }
        }
        compose.onNodeWithText("Aperçu indisponible.").assertIsDisplayed()
    }

    @Test fun retainedResponseCanNameItsModelEvenWhenTheCatalogIsUnavailable() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    ChatGptResponse(TrialUiState(response = "**Objet**", responseModel = "vision"))
                }
            }
        }
        compose.onNodeWithText("Modèle utilisé : vision").assertIsDisplayed()
        compose.onNodeWithText("Objet").assertIsDisplayed()
    }
}
