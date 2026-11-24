package dev.blamera.ui

import android.graphics.BitmapFactory
import android.os.Looper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.Density
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.chatgpt.ChatGptTrialException
import dev.blamera.chatgpt.TrialFailure
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FRAME_CHATGPT_INSTRUCTION
import dev.blamera.chatgpt.ui.FakeTrialGateway
import dev.blamera.chatgpt.ui.OBJECT_CHATGPT_INSTRUCTION
import dev.blamera.image.ImageCrop
import dev.blamera.information.AndroidSearchImagePreparer
import dev.blamera.information.FakeSharedImage
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.ObjectSearchState
import dev.blamera.information.SearchImage
import dev.blamera.information.SearchImagePreparer
import dev.blamera.information.SearchImagePublisher
import dev.blamera.information.SharedSearchImage
import dev.blamera.information.fakeImageShareSender
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ImageBox
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObjectChatGptUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = CaptureSession(scope)
    private val gateway = FakeTrialGateway()
    private val model = ChatGptTrialViewModel(gateway) { error("No synthetic image") }
    private val analyzer = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }
    private var jpeg: ByteArray? = null
    private var publications = 0
    private var preparations = 0
    private lateinit var view: View

    @After fun close() {
        session.close()
        model.close()
        scope.cancel()
    }

    @Test fun exactObjectAndInstructionSendOnlyAfterConnectionAndSecondExplicitAction() {
        show()
        selectObject()
        compose.onNodeWithText(OBJECT_CHATGPT_INSTRUCTION).assertDoesNotExist()
        assertEquals(0, preparations)
        assertTrue(gateway.sent.isEmpty())
        compose.runOnIdle { saveObjectChatGptScreenshot(view, "portrait-before-send") }
        connectAccount()
        assertEquals(0, preparations)
        assertTrue(gateway.sent.isEmpty())
        send()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { saveObjectChatGptScreenshot(view, "portrait-response") }
        assertEquals(1, gateway.sent.size)
        assertArrayEquals(jpeg, gateway.sent.single().jpeg)
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg!!.size)
        assertEquals(352, decoded.width)
        assertEquals(264, decoded.height)
        decoded.recycle()
        assertEquals(0, publications)
        assertEquals(0, session.selected)
    }

    @Test fun replacingTheSourceCancelsWithoutPublishingThePreviousObjectsLateAnswer() {
        show()
        selectObject()
        connectAccount()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        send()
        await { gateway.sent.size == 1 }
        compose.runOnIdle {
            session.accept(capturePhoto(), analyzer)
            assertNull(model.objects.document)
            assertFalse(model.state.busy)
            pending.complete("Ancienne image")
        }
        compose.onNodeWithText("Ancienne image").assertDoesNotExist()
        assertEquals(1, gateway.connections)
        assertEquals(1, gateway.sent.size)
        compose.onNodeWithText("ChatGPT").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun configurationIsHiddenUntilGearAndQuotaErrorsAllowOnlyExplicitRetry() {
        show()
        selectObject()
        connectAccount()
        compose.onNodeWithTag("model-selector").assertDoesNotExist()
        compose.onNodeWithContentDescription("Configuration").performClick()
        compose.onNodeWithTag("model-selector").performClick()
        compose.onNodeWithText("Autre modèle").performClick()
        assertTrue(gateway.sent.isEmpty())
        compose.runOnIdle {
            saveObjectChatGptScreenshot(
                checkNotNull(
                    org.robolectric.shadows.ShadowDialog.getLatestDialog().window
                ).decorView,
                "landscape-configuration"
            )
        }
        compose.onNodeWithText("Fermer").performClick()
        gateway.analysisFailure = ChatGptTrialException(TrialFailure.USAGE_LIMIT)
        send()
        compose.onNodeWithText(
            TrialFailure.USAGE_LIMIT.message
        ).performScrollTo().assertIsDisplayed()
        assertEquals(1, gateway.sent.size)
        gateway.analysisFailure = null
        send()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        assertEquals("other-fixture", gateway.sent.last().model)
        assertEquals(2, gateway.sent.size)
        compose.runOnIdle { saveObjectChatGptScreenshot(view, "landscape-response") }
    }

    @Test fun cancelKeepsTheFrozenCropAndRejectsItsLateAnswerUntilAnotherExplicitSend() {
        show()
        selectObject()
        connectAccount()
        val pending = CompletableDeferred<String>()
        gateway.responseGate = pending
        send()
        await { model.state.busy }
        compose.onNodeWithText("Annuler").performScrollTo().performClick()
        pending.complete("Réponse annulée")
        compose.onNodeWithText("Réponse annulée").assertDoesNotExist()
        assertNotNull(model.objects.document)
        assertEquals(1, gateway.sent.size)
        gateway.responseGate = null
        send()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        assertEquals(2, gateway.sent.size)
    }

    @Test fun preparationFailureCanBeRetriedFromTheSameChatGptActionWithoutPrematureUpload() {
        show(failFirstPreparation = true)
        connectAccount()
        send()
        compose.onNodeWithText("Préparation impossible. Vous pouvez réessayer.")
            .performScrollTo().assertIsDisplayed()
        assertTrue(gateway.sent.isEmpty())
        send()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        assertEquals(2, preparations)
        assertEquals(1, gateway.sent.size)
        assertEquals(0, publications)
    }

    @Test fun failedLocalAnalysisCanSendTheFrameWithoutSelectionWithItsOwnInstruction() {
        show(result = AnalysisResult.Failed)
        connectAccount()
        send()
        compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
        assertEquals(FRAME_CHATGPT_INSTRUCTION, gateway.sent.single().instruction)
        val image = BitmapFactory.decodeByteArray(jpeg, 0, jpeg!!.size)
        assertEquals(640, image.width)
        assertEquals(480, image.height)
        image.recycle()
        assertNull(session.selected)
    }

    @Test fun normalToolbarFitsThreeActionsAndLargeFontReflowsWithoutLosingConfiguration() {
        show(largeFont = true)
        compose.onNodeWithText("Analyser").assertIsDisplayed()
        compose.onNodeWithText("ChatGPT").assertIsDisplayed()
        compose.onNodeWithText("Lens").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configuration").performClick()
        compose.onNodeWithText("Fermer").assertIsDisplayed().performClick()
        assertEquals(0, preparations)
        assertTrue(gateway.sent.isEmpty())
        compose.runOnIdle { saveObjectChatGptScreenshot(view, "portrait-large-font-toolbar") }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun landscapeConfigurationWithLargeFontsKeepsSelectorsAccountMenuAndCloseAccessible() {
        gateway.availableModels = listOf(
            dev.blamera.chatgpt.TrialModel(
                "gpt-6.1-sol",
                "GPT-6.1 Sol — un nom volontairement très long"
            )
        )
        show(largeFont = true)
        connectAccount()
        compose.onNodeWithContentDescription("Configuration").performScrollTo().performClick()
        compose.onNodeWithTag("model-selector").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reasoning-selector").performScrollTo().performClick()
        compose.onNodeWithText("Élevée").performScrollTo().performClick()
        compose.onNodeWithTag("reasoning-selector").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compte ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Limites ChatGPT").assertIsDisplayed().performClick()
        compose.onNodeWithText("Fermer").assertIsDisplayed()
        compose.runOnIdle {
            saveObjectChatGptScreenshot(
                checkNotNull(
                    org.robolectric.shadows.ShadowDialog.getLatestDialog().window
                ).decorView,
                "landscape-large-font-configuration"
            )
            assertEquals("high", model.state.selectedReasoningEffort)
            assertEquals(0, preparations)
            assertTrue(gateway.sent.isEmpty())
        }
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithTag("model-selector").assertDoesNotExist()
        compose.onNodeWithText("Lens").assertIsDisplayed()
    }

    private fun connectAccount() {
        compose.onNodeWithText("ChatGPT").performScrollTo().performClick()
        await { model.state.canAnalyze }
        assertTrue(gateway.sent.isEmpty())
    }
    private fun send() {
        compose.onNodeWithText("ChatGPT").performScrollTo().performClick()
    }
    private fun selectObject() {
        compose.onNodeWithContentDescription("Image analysée").performTouchInput { click(center) }
    }
    private fun await(condition: () -> Boolean) {
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
    private fun show(
        failFirstPreparation: Boolean = false,
        result: AnalysisResult? = null,
        largeFont: Boolean = false
    ) {
        val actualAnalyzer = if (result == null) analyzer else VisionAnalyzer { result }
        compose.runOnUiThread { session.accept(capturePhoto(), actualAnalyzer) }
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeFont) 1.5f else 1f)
            ) {
                MaterialTheme {
                    CaptureHome(
                        session,
                        CaptureTools(
                            PreviewProviderLoader {
                                error("No camera")
                            },
                            actualAnalyzer,
                            ::categoryLabel,
                            information = ObjectInformationTools(
                                object : SearchImagePreparer {
                                    override suspend fun prepare(
                                        source: android.graphics.Bitmap,
                                        box: ImageBox
                                    ): SearchImage = error("Exact pixel region required")
                                    override suspend fun prepareRegion(
                                        source: android.graphics.Bitmap,
                                        crop: ImageCrop
                                    ): SearchImage {
                                        preparations++
                                        if (failFirstPreparation &&
                                            preparations == 1
                                        ) {
                                            throw java.io.IOException("Fixture")
                                        }
                                        return AndroidSearchImagePreparer().prepareRegion(
                                            source,
                                            crop
                                        ).also {
                                            jpeg =
                                                it.jpeg.copyOf()
                                        }
                                    }
                                },
                                SearchImagePublisher {
                                    publications++
                                    FakeSharedImage()
                                },
                                fakeImageShareSender { }
                            ),
                            chatGpt = model
                        ),
                        {}
                    )
                }
            }
        }
    }
}
