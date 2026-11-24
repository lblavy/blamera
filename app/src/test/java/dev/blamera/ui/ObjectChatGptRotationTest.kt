package dev.blamera.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.blamera.MainActivity
import dev.blamera.chatgpt.ui.ChatGptTrialViewModel
import dev.blamera.chatgpt.ui.FakeTrialGateway
import dev.blamera.image.ImageCrop
import dev.blamera.information.AndroidSearchImagePreparer
import dev.blamera.information.ObjectInformationTools
import dev.blamera.information.SearchImage
import dev.blamera.information.SearchImagePreparer
import dev.blamera.information.SearchImagePublisher
import dev.blamera.information.fakeImageShareSender
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ImageBox
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObjectChatGptRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationKeepsPendingJpegButRealBackgroundCancelsIt() {
        val gateway = FakeTrialGateway()
        val model = ChatGptTrialViewModel(gateway)
        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            ViewModelProvider(
                compose.activity,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        modelClass.cast(model)!!
                }
            )[ChatGptTrialViewModel::class.java]
        }
        compose.activityRule.scenario.recreate()
        var gate = CompletableDeferred<Unit>()
        lateinit var capture: CaptureSession
        lateinit var operations: PhotoTargetActions
        var exports = 0
        compose.runOnIdle {
            capture = ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            operations = capture.information(
                ObjectInformationTools(
                    object : SearchImagePreparer {
                        override suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage =
                            error("Exact region required")
                        override suspend fun prepareRegion(
                            source: Bitmap,
                            crop: ImageCrop
                        ): SearchImage {
                            exports++
                            withContext(NonCancellable) {
                                gate.await()
                            }
                            return AndroidSearchImagePreparer().prepareRegion(
                                source,
                                crop
                            )
                        }
                    },
                    SearchImagePublisher { error("No Lens publication") },
                    fakeImageShareSender { error("No external launch") }
                ),
                model
            )
            capture.accept(capturePhoto(), VisionAnalyzer { AnalysisResult.Success(emptyList()) })
            model.connect { }
        }
        try {
            compose.onNodeWithText("ChatGPT").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(operations.preparing) }
            val snapshot = operations.snapshot
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertTrue(
                    "Configuration recreation must keep the pending export",
                    operations.preparing
                )
                assertSame(snapshot, operations.snapshot)
            }
            gate.complete(Unit)
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                gateway.sent.size ==
                    1
            }
            assertEquals(1, exports)
            compose.onNodeWithText("Une tasse bleue.").performScrollTo().assertIsDisplayed()
            gate = CompletableDeferred()
            compose.onNodeWithText("ChatGPT").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(operations.preparing) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("Real background cancels pending JPEG", !operations.preparing)
            gate.complete(Unit)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            assertEquals(1, gateway.sent.size)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test fun mainRotationKeepsAnInFlightRequestThenItsAnswerAndCropWithoutAnotherSend() {
        val gateway = FakeTrialGateway()
        val model = ChatGptTrialViewModel(gateway)
        // Install a fake network boundary in the real Activity store before its recreation.
        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            ViewModelProvider(
                compose.activity,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        modelClass.cast(model)!!
                }
            )[ChatGptTrialViewModel::class.java]
        }
        compose.activityRule.scenario.recreate()
        lateinit var capture: CaptureSession
        compose.runOnIdle {
            capture = ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            capture.accept(
                capturePhoto(),
                VisionAnalyzer {
                    AnalysisResult.Success(listOf(captureDetection()))
                }
            )
            capture.interaction!!.selectHits(listOf(0))
        }
        val pending = CompletableDeferred<String>()
        compose.runOnIdle {
            model.connect { }
            gateway.responseGate = pending
        }
        compose.onNodeWithText("ChatGPT").performScrollTo().performClick()
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            model.objects.document != null && gateway.sent.size == 1
        }
        val input = checkNotNull(model.objects.document)
        assertEquals(1, gateway.sent.size)
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            assertSame(
                model,
                ViewModelProvider(compose.activity)[ChatGptTrialViewModel::class.java]
            )
            assertSame(
                capture,
                ViewModelProvider(compose.activity)[CaptureHolder::class.java].session
            )
            assertSame(input, model.objects.document)
            assertTrue(model.state.busy)

            pending.complete("**Objet actuel**\n\n- Une caractéristique visible.")
        }
        compose.onNodeWithText("Objet actuel", substring = true)
            .performScrollTo().assertIsDisplayed()
        val response = model.state.response
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Objet actuel", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertSame(input, model.objects.document)
            assertEquals(response, model.state.response)
            assertArrayEquals(input.jpeg, gateway.sent.single().jpeg)
            assertEquals(1, gateway.connections)
            assertEquals(1, gateway.sent.size)
            assertEquals(0, capture.selected)
            saveObjectChatGptScreenshot(compose.activity.window.decorView, "rotated-response")
        }
    }
}
