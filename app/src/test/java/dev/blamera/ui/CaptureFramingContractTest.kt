package dev.blamera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureFramingContractTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = CaptureSession(scope)

    @After fun close() {
        scope.cancel()
    }

    @Test fun visibleFrameHasChatGptActionWithoutAnyDetection() {
        show(AnalysisResult.Success(emptyList()))
        compose.onNodeWithText("ChatGPT").assertIsDisplayed()
    }

    @Test fun failedLocalAnalysisStillHasLensActionForThePhoto() {
        show(AnalysisResult.Failed)
        compose.onNodeWithText("Lens").assertIsDisplayed()
    }

    @Test fun commonLocalActionHasTheShortAnalyserLabel() {
        show(AnalysisResult.Success(emptyList()))
        compose.onNodeWithText("Analyser").assertIsDisplayed()
    }

    @Test fun frameLabelIsPassiveAndSettingsAreBehindAGear() {
        show(AnalysisResult.Success(emptyList()))
        compose.onNodeWithText("Cadrage visible").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configuration").assertIsDisplayed()
    }

    @Test fun photoNavigationUsesAccessibleIconsWithoutDuplicatedBottomText() {
        show(AnalysisResult.Success(emptyList()))
        compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
        compose.onNodeWithContentDescription("Caméra").assertIsDisplayed()
        compose.onNodeWithText("Reprendre").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun normal300DpPanelPlacesTheThreeTreatmentsOnOneRow() {
        show(AnalysisResult.Success(emptyList()))
        val first = compose.onNodeWithText("Analyser").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("ChatGPT").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("Lens").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(first.top, second.top, 1f)
        org.junit.Assert.assertEquals(first.top, third.top, 1f)
        org.junit.Assert.assertTrue(first.right <= second.left && second.right <= third.left)
    }

    @Test fun automaticTargetNeedsNoSourceChipsAndKeepsGearBesideItsPassiveLabel() {
        show(AnalysisResult.Success(emptyList()))
        compose.onNodeWithText("Cadrage").assertDoesNotExist()
        compose.onNodeWithText("Objet").assertDoesNotExist()
        compose.onNodeWithText("Cadrage visible").assertIsDisplayed()
        val label = compose.onNodeWithText("Cadrage visible").fetchSemanticsNode().boundsInRoot
        val gear = compose.onNodeWithContentDescription(
            "Configuration"
        ).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(label.center.y, gear.center.y, 1f)
    }

    private fun show(result: AnalysisResult) {
        val analyzer = VisionAnalyzer { result }
        compose.runOnUiThread { session.accept(capturePhoto(), analyzer) }
        compose.setContent {
            MaterialTheme {
                CaptureHome(
                    session,
                    CaptureTools(
                        PreviewProviderLoader { error("No camera") },
                        analyzer,
                        ::categoryLabel
                    ),
                    {}
                )
            }
        }
    }
}
