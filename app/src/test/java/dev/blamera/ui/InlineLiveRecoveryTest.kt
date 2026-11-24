package dev.blamera.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Looper
import android.util.Size
import android.view.View
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.OutputTransform
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.categoryLabel
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class InlineLiveRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aFailedFrameClearsContoursAndTheNextEmptyFrameRecoversInPlace() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val camera = InlineCameraFixture()
        val session = CaptureSession(scope)
        val calls = AtomicInteger()
        lateinit var view: View
        val live = VisionAnalyzer {
            when (calls.incrementAndGet()) {
                1 -> AnalysisResult.Success(listOf(captureDetection()))
                2 -> AnalysisResult.Failed
                else -> AnalysisResult.Success(emptyList())
            }
        }
        try {
            compose.setContent {
                view = LocalView.current
                MaterialTheme {
                    CaptureHome(
                        session,
                        CaptureTools(
                            camera,
                            VisionAnalyzer { error("Live must not call the photo model") },
                            ::categoryLabel,
                            LiveCameraConfig(live, ::fittedTestTarget)
                        ),
                        {}
                    )
                }
            }
            compose.onNodeWithText("Analyse en direct").performClick()
            offer(camera)
            compose.onNodeWithText("Chat").assertIsDisplayed()
            val contourPixels = yellowPixels(view)
            val preview = camera.views.single()
            assertTrue(
                "The first frame must paint real contours; " +
                    "viewport=${view.width}x${view.height}, " +
                    "preview=${preview.width}x${preview.height}, yellow=$contourPixels",
                contourPixels > 0
            )
            offer(camera)
            compose.onNodeWithText(
                "Analyse impossible. Arrêtez puis réessayez, ou utilisez Galerie."
            ).assertIsDisplayed()
            compose.onNodeWithText("Chat").assertDoesNotExist()
            assertEquals("Failed frames clear previous contours", 0, yellowPixels(view))
            compose.onNodeWithText("Capturer").assertIsDisplayed()
            compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
            offer(camera)
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            assertEquals(0, yellowPixels(view))
            assertEquals(3, calls.get())
            assertEquals(1, camera.views.size)
            compose.onNodeWithText("Arrêter l’analyse").performClick()
        } finally {
            scope.cancel()
        }
    }

    private fun offer(camera: InlineCameraFixture) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        val frame = InlineFrameFixture()
        compose.runOnIdle { checkNotNull(camera.processor).offer(frame) }
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            frame.closes.get() == 1
        }
        compose.waitForIdle()
    }

    private fun fittedTestTarget(preview: PreviewView): OutputTransform {
        check(preview.width > 0 && preview.height > 0) { "The camera preview must remain visible" }
        // The frame fixture uses raw 640x480 coordinates. Like FIT_CENTER in the real
        // PreviewView, its paired output must map those coordinates into the actual viewport.
        val matrix = Matrix().apply {
            check(
                setRectToRect(
                    RectF(0f, 0f, 640f, 480f),
                    RectF(0f, 0f, preview.width.toFloat(), preview.height.toFloat()),
                    Matrix.ScaleToFit.CENTER
                )
            )
        }
        return OutputTransform(matrix, Size(640, 480))
    }

    private fun yellowPixels(view: View): Int {
        var count = 0
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            count =
                pixels.count { Color.red(it) > 240 && Color.green(it) > 240 && Color.blue(it) < 20 }
            bitmap.recycle()
        }
        return count
    }
}
