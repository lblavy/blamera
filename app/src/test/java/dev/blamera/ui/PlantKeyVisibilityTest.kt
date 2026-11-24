package dev.blamera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import dev.blamera.botany.PlantSettings
import dev.blamera.chatgpt.storage.TrialSessionStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlantKeyVisibilityTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = JointPlantFixture()

    @After fun close() = fixture.close()

    @Test fun theEyePreservesTextAndSelectionInBothDirections() {
        compose.setContent { MaterialTheme { PlantSettingsEditor(fixture.settings) } }
        val field = compose.onNodeWithTag("plantnet-key")
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithContentDescription("Afficher la clé").assertIsEnabled().performClick()
        field.performTextInput("SYNTHETIC-KEY")
        field.performTextInputSelection(TextRange(3, 7))
        assertEquals("SYNTHETIC-KEY", renderedText())
        compose.onNodeWithContentDescription("Masquer la clé").performClick()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals("•".repeat("SYNTHETIC-KEY".length), renderedText())
        assertEquals(
            TextRange(3, 7),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        )
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        assertEquals(
            TextRange(3, 7),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        )
        assertEquals("SYNTHETIC-KEY", renderedText())
        field.performTextInput("X")
        assertEquals("SYNXIC-KEY", renderedText())
        assertEquals(0, fixture.requests)
    }

    @Test fun saveRemoveAndReopenAlwaysLeaveAnEmptyMaskedEditor() {
        var open by mutableStateOf(true)
        compose.setContent { if (open) MaterialTheme { PlantSettingsEditor(fixture.settings) } }
        val field = compose.onNodeWithTag("plantnet-key")
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        field.performTextInput("SECOND-FAKE")
        compose.onNodeWithText("Remplacer la clé").performClick()
        assertEmptyMasked()
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        field.performTextInput("UNSAVED")
        compose.onNodeWithText("Supprimer").performClick()
        assertEmptyMasked()
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        field.performTextInput("UNSAVED")
        compose.runOnIdle { open = false }
        compose.runOnIdle { open = true }
        assertEmptyMasked()
        assertEquals(0, fixture.requests)
    }

    @Test fun pendingStorageDisablesBothTheEyeAndTheEditor() {
        val pending = CompletableDeferred<String?>()
        val store = object : TrialSessionStore {
            override suspend fun load(): String? = pending.await()
            override suspend fun save(value: String) = Unit
            override suspend fun clear() = Unit
        }
        val settings = PlantSettings(fixture.scope, store)
        compose.setContent { MaterialTheme { PlantSettingsEditor(settings) } }
        compose.onNodeWithTag("plantnet-key").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Afficher la clé").assertIsNotEnabled()
        compose.runOnIdle { pending.complete(null) }
        compose.onNodeWithTag("plantnet-key").assertIsEnabled()
        compose.onNodeWithContentDescription("Afficher la clé").assertIsEnabled()
        assertEquals(0, fixture.requests)
    }

    private fun renderedText(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("plantnet-key").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        return layouts.single().layoutInput.text.text
    }

    private fun assertEmptyMasked() {
        val field = compose.onNodeWithTag("plantnet-key")
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithContentDescription("Afficher la clé").assertIsEnabled()
    }
}
