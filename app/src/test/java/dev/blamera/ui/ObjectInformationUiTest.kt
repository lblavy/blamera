package dev.blamera.ui

import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.compose.material3.MaterialTheme
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
import dev.blamera.camera.PreviewProviderLoader
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
class ObjectInformationUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = CaptureSession(scope)
    private val launched = mutableListOf<Intent>()
    private val sent = mutableListOf<SearchImage>()
    private val result = FakeSharedImage()
    private lateinit var view: View
    private val analyzer = VisionAnalyzer { bitmap ->
        AnalysisResult.Success(
            listOf(
                captureDetection().copy(
                    box = dev.blamera.vision.ImageBox(
                        bitmap.width * .25f,
                        bitmap.height * .25f,
                        bitmap.width * .75f,
                        bitmap.height * .75f
                    )
                )
            )
        )
    }

    @After fun close() {
        session.close()
        scope.cancel()
    }

    @Test fun lensIsAvailableWithoutSelectionButNeverSharesUntilExplicitAction() {
        show()
        compose.onNodeWithText("Lens").assertIsDisplayed().assertIsEnabled()
        assertTrue(sent.isEmpty())
        selectObject()
        assertTrue(sent.isEmpty())
        val photo = session.photo
        val results = session.result
        val interaction = session.interaction
        search()
        await { launched.size == 1 }
        assertEquals(
            result.uri,
            launched.single().getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        )
        assertSame(photo, session.photo)
        assertSame(results, session.result)
        assertSame(interaction, session.interaction)
        compose.onNodeWithContentDescription("Image analysée").performTouchInput { click(center) }
        assertEquals(PhotoTarget.FRAME, session.target)
        compose.onNodeWithText("Lens").assertIsDisplayed()
        assertEquals(1, sent.size)
        assertFalse(result.closed)
    }

    @Test fun geminiAndDisclosuresRemainRemovedAndLensSharesNoText() {
        show()
        selectObject()
        compose.onNodeWithText("Tester Gemini").assertDoesNotExist()
        compose.onNodeWithText(
            "Cette zone sera transmise à Google Lens pour une recherche par image."
        ).assertDoesNotExist()
        assertTrue(sent.isEmpty())
        search()
        await { launched.size == 1 }
        assertFalse(launched.single().hasExtra(Intent.EXTRA_TEXT))
    }

    @Test fun pendingLensDisablesTheActionsAndPhotoReplacementRejectsItsLatePublication() {
        val pending = CompletableDeferred<SharedSearchImage>()
        show(
            SearchImagePublisher {
                sent.add(it)
                withContext(NonCancellable) { pending.await() }
            }
        )
        selectObject()
        search()
        await { sent.size == 1 }
        compose.onNodeWithText("Lens").assertIsNotEnabled()
        compose.onNodeWithText("Annuler").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            session.accept(capturePhoto(), analyzer)
            pending.complete(result)
        }
        await { result.closed }
        assertTrue(launched.isEmpty())
        assertEquals(1, sent.size)
    }

    @Test fun cancellingSearchAndReplacingTheSourceNeverOpensItsLateFile() {
        val pending = CompletableDeferred<SharedSearchImage>()
        show(
            SearchImagePublisher {
                sent.add(it)
                withContext(NonCancellable) { pending.await() }
            }
        )
        search()
        await { sent.size == 1 }
        compose.onNodeWithText("Annuler").performScrollTo().performClick()
        compose.onNodeWithText("Lens").assertIsEnabled()
        compose.runOnIdle {
            session.accept(capturePhoto(), analyzer)
            pending.complete(result)
        }
        await { result.closed }
        assertTrue(launched.isEmpty())
        assertEquals(1, sent.size)
    }

    @Test fun invalidObjectSelectionFallsBackToFrameWithoutUploadingAnything() {
        show()
        selectObject()
        compose.runOnIdle { session.interaction!!.selectHits(listOf(99)) }
        assertEquals(PhotoTarget.FRAME, session.target)
        compose.onNodeWithText("Lens").assertIsEnabled()
        compose.runOnIdle {
            session.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Success(emptyList()) })
        }
        compose.onNodeWithText("Objet").assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }

    @Test fun changingTheExplicitObjectTargetBeforeRecompositionRejectsReturnedResults() {
        val pending = CompletableDeferred<SharedSearchImage>()
        show(
            SearchImagePublisher {
                sent.add(it)
                withContext(NonCancellable) { pending.await() }
            }
        )
        selectObject()
        search()
        await { sent.size == 1 }
        compose.runOnIdle {
            session.interaction!!.selectHits(emptyList())
            pending.complete(result)
        }
        await { result.closed }
        assertTrue(launched.isEmpty())
    }

    @Test fun frameSearchStillWorksAfterZeroDetectionsOrLocalFailure() {
        show()
        compose.runOnIdle {
            session.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Failed })
        }
        search()
        await { launched.size == 1 }
        assertNull(session.selected)
        assertEquals(640, sent.single().preview.width)
        assertEquals(480, sent.single().preview.height)
    }

    @Test fun regionAndOriginalSurviveLensInPortrait() = cropSearchAndRestore(
        "frame-actions-portrait"
    )

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun regionAndOriginalSurviveLensInLandscape() = cropSearchAndRestore("frame-actions-landscape")

    private fun cropSearchAndRestore(name: String) {
        show()
        val original = session.photo
        val originalResult = session.result
        compose.onNodeWithContentDescription("Image analysée").performTouchInput {
            pinch(
                androidx.compose.ui.geometry.Offset(width * .35f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .65f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .1f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .9f, height * .5f)
            )
        }
        compose.onNodeWithText("Analyser").performClick()
        await { session.isRegion && !session.running && !session.loading }
        val crop = session.photo
        selectObject()
        search()
        await { launched.size == 1 }
        assertSame(crop, session.photo)
        assertEquals(0, session.selected)
        compose.onNodeWithContentDescription("Image utilisée").performScrollTo().assertIsDisplayed()
        assertFalse(launched.single().hasExtra(Intent.EXTRA_TEXT))
        compose.runOnIdle { saveObjectChatGptScreenshot(view, name) }
        compose.onNodeWithContentDescription("Image entière").performClick()
        assertFalse(session.isRegion)
        assertSame(original, session.photo)
        assertSame(originalResult, session.result)
        assertEquals(1, sent.size)
        assertFalse(result.closed)
    }

    private fun show(
        publisher: SearchImagePublisher = SearchImagePublisher {
            sent.add(it)
            result
        }
    ) {
        compose.runOnUiThread { session.accept(capturePhoto(), analyzer) }
        compose.setContent {
            view = LocalView.current
            MaterialTheme {
                CaptureHome(
                    session,
                    CaptureTools(
                        PreviewProviderLoader { error("No camera") },
                        analyzer,
                        ::categoryLabel,
                        information = ObjectInformationTools(
                            AndroidSearchImagePreparer(),
                            publisher,
                            fakeImageShareSender(launched::add)
                        )
                    ),
                    {}
                )
            }
        }
    }
    private fun selectObject() {
        compose.onNodeWithContentDescription("Image analysée").performTouchInput { click(center) }
    }
    private fun search() {
        compose.onNodeWithText("Lens").performScrollTo().performClick()
    }
    private fun await(condition: () -> Boolean) {
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
