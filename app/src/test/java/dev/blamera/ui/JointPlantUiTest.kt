package dev.blamera.ui

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.blamera.botany.PlantHttpResponse
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class JointPlantUiTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = JointPlantFixture()
    private var session: CaptureSession? = null
    private lateinit var view: View

    @After fun close() {
        session?.close()
        fixture.close()
    }

    @Test fun configurationOffersAMaskedEmptyKeyEditorAndReportsConfiguredState() {
        compose.setContent { MaterialTheme { PlantSettingsEditor(fixture.settings) } }
        val key = compose.onNodeWithTag("plantnet-key")
        key.assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals("", key.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Clé configurée").assertIsDisplayed()
        compose.onNodeWithText("Les photos analysées sont envoyées à Pl@ntNet.").assertIsDisplayed()
        key.performTextInput("SECOND-FAKE")
        compose.onNodeWithText("Remplacer la clé").performClick()
        assertEquals("SECOND-FAKE", fixture.store.value)
        assertEquals("", key.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Supprimer").performClick()
        assertFalse(fixture.settings.configured)
        compose.onNodeWithText("Aucune clé configurée").assertIsDisplayed()
        assertEquals(0, fixture.requests)
    }

    @Test fun reopeningTheEditorNeverRecoversAnUnsubmittedOrStoredKey() {
        var visible by mutableStateOf(true)
        compose.setContent { if (visible) MaterialTheme { PlantSettingsEditor(fixture.settings) } }
        compose.onNodeWithTag("plantnet-key").performTextInput("UNSAVED-FAKE")
        compose.runOnIdle { visible = false }
        compose.runOnIdle { visible = true }
        assertEquals(
            "",
            compose.onNodeWithTag(
                "plantnet-key"
            ).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertEquals("BLAMERA-FAKE", fixture.store.value)
        assertEquals(0, fixture.requests)
    }

    @Test fun candidatesArePresentedAsAlternativesForOneDominantPlantWithAttribution() {
        fixture.plants.start(capturePhoto())
        compose.setContent { MaterialTheme { PlantResultCard(fixture.plants) } }
        compose.onNodeWithText("Églantier — Rosa canina").assertIsDisplayed()
        compose.onNodeWithText("Autres hypothèses pour cette plante").assertIsDisplayed()
        compose.onNodeWithText("Plante de l’image analysée").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pl@ntNet").assertIsDisplayed()
    }

    @Test fun botanicalWaitAndCancellationDoNotDisableAnyPhotoTreatmentOrLaunchAnotherRequest() {
        val pending = CompletableDeferred<PlantHttpResponse>()
        fixture.response = { pending.await() }
        show()
        compose.onNodeWithText("Analyser").assertIsEnabled()
        compose.onNodeWithText("ChatGPT").assertIsEnabled()
        compose.onNodeWithText("Lens").assertIsEnabled()
        compose.onNodeWithText("Annuler Pl@ntNet").performScrollTo().performClick()
        pending.complete(plantResponse())
        compose.onNodeWithText("Églantier — Rosa canina").assertDoesNotExist()
        compose.onNodeWithText(
            "Identification Pl@ntNet interrompue."
        ).performScrollTo().assertIsDisplayed()
        assertEquals(1, fixture.requests)
    }

    @Test fun allThreeTreatmentsHaveTheSameFilledPrimaryBackground() {
        show()
        for (label in listOf("Analyser", "ChatGPT", "Lens")) {
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            var color = 0
            compose.runOnIdle {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    view.width,
                    view.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                view.draw(android.graphics.Canvas(bitmap))
                color = bitmap.getPixel(bounds.left.toInt() + 4, bounds.center.y.toInt())
                bitmap.recycle()
            }
            assertEquals("Filled background for $label", primaryColor, color)
        }
    }

    private var primaryColor = 0

    @Test fun compactResultsAndKeyConfigurationAreReachableInPortrait() =
        resultsAndConfig("joint-portrait")

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun compactResultsAndKeyConfigurationAreReachableInLandscape() =
        resultsAndConfig("joint-landscape")

    @Test fun nonPlantAndFailedBotanyRemainHonestAndDoNotDisableLocalTreatments() {
        fixture.response = { PlantHttpResponse(404, "") }
        show()
        compose.onNodeWithText(
            "Aucune plante identifiée par Pl@ntNet."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Analyser").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            fixture.response = { PlantHttpResponse(503, "") }
            session!!.retry(VisionAnalyzer { AnalysisResult.Success(emptyList()) })
        }
        compose.onNodeWithText(
            "Service Pl@ntNet indisponible. Réessayez plus tard."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lens").performScrollTo().assertIsEnabled()
        assertEquals(2, fixture.requests)
    }

    @Test fun aSingleScientificNameWithoutCommonNameIsStillAnUncertainHypothesis() {
        fixture.response =
            {
                PlantHttpResponse(
                    200,
                    """{"results":[{"score":0.3,"species":{"scientificNameWithoutAuthor":"Rosa canina"}}]}"""
                )
            }
        fixture.plants.start(capturePhoto())
        compose.setContent { MaterialTheme { PlantResultCard(fixture.plants) } }
        compose.onNodeWithText("Rosa canina").assertIsDisplayed()
        compose.onNodeWithText("Hypothèse à vérifier").assertIsDisplayed()
        compose.onNodeWithText("Autres hypothèses pour cette plante").assertDoesNotExist()
    }

    private fun resultsAndConfig(name: String) {
        show()
        compose.onNodeWithText("Églantier — Rosa canina").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Configuration").performScrollTo().performClick()
        compose.onNodeWithTag("plantnet-key").performScrollTo()
        compose.onNodeWithTag("plantnet-key").assertIsDisplayed()
        compose.onNodeWithContentDescription("Afficher la clé").performClick()
        compose.onNodeWithTag("plantnet-key").performTextInput("DEMO-FAKE")
        compose.onNodeWithContentDescription("Masquer la clé").assertIsDisplayed()
        compose.onNodeWithContentDescription("Masquer la clé").performClick()
        compose.onNodeWithText("Remplacer la clé").performScrollTo()
        compose.onNodeWithText("Remplacer la clé").assertIsDisplayed()
        compose.onNodeWithText("Supprimer").performScrollTo()
        compose.onNodeWithText("Supprimer").assertIsDisplayed()
        compose.runOnIdle {
            saveObjectChatGptScreenshot(
                checkNotNull(
                    org.robolectric.shadows.ShadowDialog.getLatestDialog().window
                ).decorView,
                "$name-config"
            )
        }
        compose.onNodeWithText("Fermer").performClick()
        compose.runOnIdle { saveObjectChatGptScreenshot(view, name) }
        assertEquals(1, fixture.requests)
    }

    private fun show() {
        val local = VisionAnalyzer { AnalysisResult.Success(emptyList()) }
        session = CaptureSession(fixture.scope, fixture.plants)
        compose.runOnUiThread { session!!.accept(capturePhoto(), local) }
        compose.setContent {
            view = LocalView.current
            MaterialTheme {
                primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                CaptureHome(
                    session!!,
                    CaptureTools(
                        PreviewProviderLoader {
                            error("No camera")
                        },
                        local,
                        ::categoryLabel
                    ),
                    {}
                )
            }
        }
    }
}
