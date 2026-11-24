package dev.blamera.chatgpt.ui

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.blamera.chatgpt.TrialModel
import dev.blamera.ui.saveObjectChatGptScreenshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonalChatGptCardTest {
    @get:Rule val compose = createComposeRule()
    private val gateway = FakeTrialGateway()
    private val model = ChatGptTrialViewModel(
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    @After fun close() = model.close()

    @Test fun accountControlsContainNoCreditCheckboxOrQuotaParagraph() {
        model.connect {}
        compose.setContent { MaterialTheme { ChatGptAccountControls(model, {}, {}) } }
        compose.onNodeWithTag("credits-confirmation").assertDoesNotExist()
        compose.onNodeWithText(
            "Le quota Codex / ChatGPT Work de votre abonnement est partagé avec vos autres usages."
        ).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun modelAndReasoningRemainSideBySideInA300DpPanelWithLargeFontsAndALongModelName() {
        gateway.availableModels = listOf(
            TrialModel("gpt-6.1-sol", "GPT-6.1 Sol — un nom de modèle volontairement très long")
        )
        model.connect {}
        val key = ObjectDocumentKey(Any(), 1, 0)
        model.objects.select(key)
        model.objects.prepare(key, byteArrayOf(1, 2, 3))
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Surface {
                        Column(Modifier.width(300.dp).testTag("personal-panel")) {
                            ChatGptAccountControls(model, {}, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("model-selector").assertIsDisplayed()
        compose.onNodeWithTag("reasoning-selector").assertIsDisplayed()
        val left = compose.onNodeWithTag("model-selector").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("reasoning-selector").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("personal-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 1f)
        assertTrue(left.right <= right.left)
        assertTrue(right.right <= panel.right)
        compose.onNodeWithTag("reasoning-selector").performClick()
        compose.onNodeWithText("Élevée").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("high", model.state.selectedReasoningEffort)
            assertTrue(gateway.sent.isEmpty())
            saveObjectChatGptScreenshot(view, "personal-panel-300dp-large-font")
        }
        compose.onNodeWithText("Envoyer").assertDoesNotExist()
    }
}
