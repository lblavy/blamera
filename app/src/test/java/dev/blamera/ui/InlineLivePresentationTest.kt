package dev.blamera.ui

import android.Manifest
import android.graphics.Matrix
import android.os.Looper
import android.util.Size
import androidx.camera.view.transform.OutputTransform
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.VisionExecution
import dev.blamera.vision.categoryLabel
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class InlineLivePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contoursStopInPlaceAndCaptureWaitsForCancelledNativeWork() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val pending = CompletableDeferred<Unit>()
        val liveCalls = AtomicInteger()
        val photoCalls = AtomicInteger()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val camera = InlineCameraFixture()
        val session = CaptureSession(scope)
        val live = VisionAnalyzer {
            VisionExecution.mutex.withLock {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                try {
                    if (liveCalls.incrementAndGet() ==
                        2
                    ) {
                        withContext(NonCancellable) { pending.await() }
                    }
                    AnalysisResult.Success(listOf(captureDetection()))
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val photo = VisionAnalyzer {
            VisionExecution.mutex.withLock {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                try {
                    photoCalls.incrementAndGet()
                    AnalysisResult.Success(emptyList())
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        try {
            compose.setContent {
                MaterialTheme {
                    CaptureHome(
                        session,
                        CaptureTools(
                            camera,
                            photo,
                            ::categoryLabel,
                            LiveCameraConfig(live) { OutputTransform(Matrix(), Size(640, 480)) }
                        ),
                        {}
                    )
                }
            }
            compose.onNodeWithText("Analyse en direct").performClick()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            val first = InlineFrameFixture()
            compose.runOnIdle { checkNotNull(camera.processor).offer(first) }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                first.closes.get() == 1
            }
            assertEquals("First live frame must reach the analyzer", 1, liveCalls.get())
            compose.onNodeWithText("Chat").assertIsDisplayed()
            assertEquals(1, camera.views.size)
            compose.onNodeWithText("Arrêter l’analyse").performClick()
            compose.onNodeWithText("Chat").assertDoesNotExist()
            compose.onNodeWithText("Capturer").assertIsDisplayed()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            compose.onNodeWithText("Analyse en direct").performClick()
            val inFlight = InlineFrameFixture()
            compose.runOnIdle { checkNotNull(camera.processor).offer(inFlight) }
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                liveCalls.get() == 2
            }
            compose.onNodeWithText("Capturer").performClick()
            compose.onNodeWithText("Analyse en cours…").assertIsDisplayed()
            assertEquals(0, photoCalls.get())
            assertEquals(1, camera.captures)
            assertFalse(inFlight.image.isRecycled)
            pending.complete(Unit)
            compose.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                photoCalls.get() == 1 && inFlight.closes.get() == 1
            }
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            compose.onNodeWithText("Chat").assertDoesNotExist()
            assertTrue(inFlight.image.isRecycled)
            assertEquals(1, maximum.get())
            assertEquals(0, camera.activeBindings)
        } finally {
            pending.complete(Unit)
            scope.cancel()
        }
    }
}
