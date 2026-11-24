package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.PreviewDimensions
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ImageBox
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.ImageViewport
import dev.blamera.vision.Letterbox
import dev.blamera.vision.LogitGrid
import dev.blamera.vision.MaskGrid
import dev.blamera.vision.VisionAnalyzer
import dev.blamera.vision.VisionDetection
import dev.blamera.vision.categoryLabel
import dev.blamera.vision.traceIsocontour
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoRegionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun zoomedPixelsAreReanalyzedAndTheWholeImageRestoresItsOriginalResults() =
        cropAndRestore("roi-portrait")

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun selectedRegionMaskAndContourStayAlignedInLandscape() = cropAndRestore("roi-landscape")

    private fun cropAndRestore(screenshot: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val session = CaptureSession(scope)
            val inputs = CopyOnWriteArrayList<Bitmap>()
            val analyzer = VisionAnalyzer { bitmap ->
                inputs.add(bitmap)
                AnalysisResult.Success(listOf(centerDetection(bitmap)))
            }
            val original = gradientPhoto()
            compose.runOnUiThread { session.accept(original, analyzer) }
            show(session, analyzer)
            val originalResult = session.result
            compose.onNodeWithText("Analyser").assertIsEnabled()
            pinch()
            compose.onNodeWithText("Analyser").assertIsEnabled()
            val region = checkNotNull(session.interaction!!.region)
            compose.onNodeWithText("Analyser").performClick()
            await { inputs.size == 2 && !session.running }
            compose.onNodeWithContentDescription("Image entière").assertIsDisplayed()
            assertNotSame(original.bitmap, inputs.last())
            assertEquals(region.width, inputs.last().width)
            assertEquals(region.height, inputs.last().height)
            assertEquals(
                original.bitmap.getPixel(region.left, region.top),
                inputs.last().getPixel(0, 0)
            )
            assertFit()
            compose.onNodeWithText("Analyser").assertIsEnabled()
            val withoutSelection = renderFrame(session)
            compose.onNodeWithContentDescription("Image analysée").performTouchInput {
                click(center)
            }
            compose.onNodeWithText("Chat").assertIsDisplayed()
            val selected = renderFrame(session)
            assertSelectionPixels(session, withoutSelection, selected)
            withoutSelection.bitmap.recycle()
            selected.bitmap.recycle()
            saveView(screenshot)
            pinch()
            compose.onNodeWithText("Analyser").assertIsEnabled().performClick()
            await { inputs.size == 3 && !session.running }
            compose.onNodeWithContentDescription("Image entière").performClick()
            assertFit()
            assertSame(original, session.photo)
            assertSame(originalResult, session.result)
            assertEquals(3, inputs.size)
            compose.onNodeWithContentDescription("Image entière").assertDoesNotExist()
            compose.onNodeWithContentDescription("Image analysée").performTouchInput {
                click(center)
            }
            compose.onNodeWithText("Chat").assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp")
    fun returningToTheWholeImageCancelsAnInFlightRegionWithoutAnotherAnalysis() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val pending = CompletableDeferred<AnalysisResult>()
        var calls = 0
        try {
            val session = CaptureSession(scope)
            val analyzer = VisionAnalyzer { bitmap ->
                if (++calls == 2) {
                    withContext(NonCancellable) { pending.await() }
                } else {
                    AnalysisResult.Success(listOf(centerDetection(bitmap)))
                }
            }
            val original = gradientPhoto()
            compose.runOnUiThread { session.accept(original, analyzer) }
            show(session, analyzer)
            val originalResult = session.result
            pinch()
            compose.onNodeWithText("Analyser").performClick()
            await { calls == 2 }
            compose.onNodeWithText("Analyse en cours…").assertIsDisplayed()
            compose.onNodeWithText("Analyser").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Caméra").assertIsDisplayed()
            compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
            saveView("roi-landscape-pending")
            compose.onNodeWithContentDescription("Image entière").assertIsEnabled().performClick()
            assertSame(original, session.photo)
            assertSame(originalResult, session.result)
            assertFit()
            pending.complete(AnalysisResult.Failed)
            compose.waitForIdle()
            assertSame(originalResult, session.result)
            assertEquals(2, calls)
            compose.onNodeWithText(
                "Analyse impossible. Réessayez ou choisissez une autre image."
            ).assertDoesNotExist()
            compose.onNodeWithText("Analyser").assertIsEnabled()
        } finally {
            pending.complete(AnalysisResult.Failed)
            scope.cancel()
        }
    }

    private fun show(session: CaptureSession, analyzer: VisionAnalyzer) {
        compose.setContent {
            view = LocalView.current
            MaterialTheme {
                CaptureHome(
                    session,
                    CaptureTools(
                        PreviewProviderLoader {
                            error("No camera needed")
                        },
                        analyzer,
                        ::categoryLabel
                    ),
                    {}
                )
            }
        }
    }

    private fun pinch() {
        compose.onNodeWithContentDescription("Image analysée").performTouchInput {
            pinch(
                Offset(width * 0.35f, height * 0.5f),
                Offset(width * 0.65f, height * 0.5f),
                Offset(width * 0.1f, height * 0.5f),
                Offset(width * 0.9f, height * 0.5f)
            )
        }
    }

    private fun assertFit() {
        assertEquals(
            "Zoom 100 %",
            compose.onNodeWithContentDescription("Image analysée")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun saveView(name: String) {
        val current = File(checkNotNull(System.getProperty("user.dir")))
        val root = if (File(
                current,
                "AGENTS.md"
            ).isFile
        ) {
            current
        } else {
            checkNotNull(current.parentFile)
        }
        val output =
            File(root, "build/reports/validation/m1-photo-refinements/app/screenshots/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        val bitmap = renderView()
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun renderView(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun renderFrame(session: CaptureSession): PhotoFrame {
        val bitmap = renderView()
        val bounds = compose.onNodeWithContentDescription("Image analysée")
            .fetchSemanticsNode().boundsInRoot
        return PhotoFrame(bitmap, checkNotNull(session.interaction).viewport, bounds)
    }

    private fun assertSelectionPixels(
        session: CaptureSession,
        plain: PhotoFrame,
        selected: PhotoFrame
    ) {
        val image = checkNotNull(session.photo).bitmap
        fun pixel(frame: PhotoFrame, x: Float, y: Float): Int {
            val point = frame.viewport.point(ImagePoint(image.width * x, image.height * y))
            return frame.bitmap.getPixel(
                (frame.bounds.left + point.x).toInt(),
                (frame.bounds.top + point.y).toInt()
            )
        }
        assertTrue(
            "Selected center is filled",
            Color.blue(pixel(selected, 0.5f, 0.5f)) -
                Color.blue(pixel(plain, 0.5f, 0.5f)) > 20
        )
        assertSameColor(
            "Outside left stays clear",
            pixel(plain, 0.1f, 0.5f),
            pixel(selected, 0.1f, 0.5f)
        )
        assertSameColor(
            "Outside above stays clear",
            pixel(plain, 0.5f, 0.1f),
            pixel(selected, 0.5f, 0.1f)
        )
    }

    private fun assertSameColor(message: String, first: Int, last: Int) {
        // Panel text can wrap differently on selection; source-space samples tolerate
        // the one-pixel filtering difference from the resized viewport.
        for (channel in listOf(Color::red, Color::green, Color::blue)) {
            assertTrue(message, kotlin.math.abs(channel(first) - channel(last)) <= 2)
        }
    }

    private fun gradientPhoto(): DecodedPhoto {
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) {
            for (x in 0 until 400) {
                bitmap.setPixel(x, y, Color.rgb(x % 256, y, 17))
            }
        }
        return DecodedPhoto(bitmap, PreviewDimensions(400, 200))
    }

    private fun centerDetection(bitmap: Bitmap): VisionDetection {
        val left = bitmap.width / 4f
        val top = bitmap.height / 4f
        val right = bitmap.width * 3 / 4f
        val bottom = bitmap.height * 3 / 4f
        val projection = Letterbox(bitmap.width, bitmap.height)
        val logits = LogitGrid(
            160,
            160,
            FloatArray(160 * 160) {
                val point = projection.sourcePoint((it % 160 + 0.5f) * 4, (it / 160 + 0.5f) * 4)
                if (point.x >= left && point.x < right && point.y >= top &&
                    point.y < bottom
                ) {
                    1f
                } else {
                    -1f
                }
            }
        )
        return VisionDetection(
            15,
            0.9f,
            ImageBox(left, top, right, bottom),
            MaskGrid(
                160,
                160,
                BooleanArray(160 * 160) { logits.values[it] > 0f }
            ),
            traceIsocontour(logits, projection)
        )
    }
}

private data class PhotoFrame(val bitmap: Bitmap, val viewport: ImageViewport, val bounds: Rect)
