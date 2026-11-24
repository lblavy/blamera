package dev.blamera.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import dev.blamera.camera.PhotoCaptureProvider
import dev.blamera.camera.PreviewProviderLoader
import dev.blamera.camera.PreviewStatus
import dev.blamera.image.ImageImportError
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PhotoLoader
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.ContourSegment
import dev.blamera.vision.ImageBox
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.MaskGrid
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun entryOffersCaptureAndGalleryWithoutSelectingASource() {
        compose.setContent { BlameraApp() }
        compose.onNodeWithText("Capturer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Galerie").assertIsDisplayed()
        compose.onNodeWithText("Autoriser la caméra").assertIsDisplayed()
        compose.onNodeWithText("Aucune source sélectionnée").assertDoesNotExist()
    }

    @Test fun galleryStartsAnalysisAndMaskTogglesWithoutAnalyzingAgain() {
        var calls = 0
        val picker = show(
            analyzer = VisionAnalyzer {
                calls++
                AnalysisResult.Success(listOf(captureDetection()))
            }
        )
        pick(picker)
        assertEquals("image/*", picker.intent?.type)
        compose.onNodeWithText(
            "1 objets détectés. Touchez un objet pour le sélectionner."
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Image analysée").performTouchInput { click(center) }
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Image analysée").performTouchInput { click(center) }
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Image analysée").performTouchInput {
            pinch(
                Offset(width * 0.35f, height * 0.5f),
                Offset(width * 0.65f, height * 0.5f),
                Offset(width * 0.1f, height * 0.5f),
                Offset(width * 0.9f, height * 0.5f)
            )
        }
        assertNotEquals(
            "Zoom 100 %",
            compose.onNodeWithContentDescription("Image analysée")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(1, calls)
        picker.next = null
        compose.onNodeWithContentDescription("Galerie").performClick()
        compose.onNodeWithContentDescription("Image analysée").assertIsDisplayed()
        assertEquals(1, calls)
    }

    @Test fun analysisShowsWaitingThenAnHonestEmptyResult() {
        val pending = CompletableDeferred<AnalysisResult>()
        val picker = show(analyzer = VisionAnalyzer { pending.await() })
        try {
            pick(picker)
            compose.onNodeWithText("Analyse en cours…").assertIsDisplayed()
            compose.onNodeWithText("Analyser l’image").assertDoesNotExist()
            pending.complete(AnalysisResult.Success(emptyList()))
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            compose.onNodeWithText("Analyse en cours…").assertDoesNotExist()
        } finally {
            pending.complete(AnalysisResult.Failed)
        }
    }

    @Test fun cameraCaptureSharesTheSameAutomaticResultsScreenAndClosesItsPreview() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        var opened = 0
        var closed = 0
        val camera = object : PhotoCaptureProvider {
            override suspend fun capture() = captureLoaded()
            override fun bind(
                owner: androidx.lifecycle.LifecycleOwner,
                view: androidx.camera.view.PreviewView,
                report: (PreviewStatus) -> Unit
            ): AutoCloseable {
                opened++
                report(PreviewStatus.STREAMING)
                return AutoCloseable { closed++ }
            }
        }
        show(camera = PreviewProviderLoader { camera })
        compose.onNodeWithText("Capturer").assertIsEnabled().performClick()
        compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Caméra").assertIsDisplayed()
        assertEquals(1, opened)
        assertEquals(1, closed)
        compose.onNodeWithContentDescription("Caméra").performClick()
        compose.onNodeWithText("Capturer").assertIsEnabled()
        assertTrue(opened >= 2)
        assertEquals(opened - 1, closed)
    }

    @Test fun galleryRemainsAvailableAfterCameraRefusalAndAFailedImportCanBeReplaced() {
        val picker = show(
            loader = PhotoLoader { uri ->
                if (uri.lastPathSegment ==
                    "bad"
                ) {
                    PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
                } else {
                    captureLoaded()
                }
            }
        )
        compose.onNodeWithText("Autoriser la caméra").performClick()
        assertEquals(1, picker.permissions)
        picker.next = Uri.parse("content://synthetic/bad")
        compose.onNodeWithContentDescription("Galerie").performClick()
        compose.onNodeWithText(
            "Image illisible ou accès indisponible. Choisissez une autre image."
        ).assertIsDisplayed()
        pick(picker)
        compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
    }

    @Test fun analysisFailureOffersAnExplicitRetryAndKeepsTheImage() {
        var calls = 0
        val picker = show(
            analyzer = VisionAnalyzer {
                if (calls++ == 0) AnalysisResult.Failed else AnalysisResult.Success(emptyList())
            }
        )
        pick(picker)
        compose.onNodeWithText("Analyser").performClick()
        compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
        assertEquals(2, calls)
    }

    @Test fun failedReplacementDuringAnalysisOffersRetryAndRejectsTheCancelledNativeResult() {
        val pending = CompletableDeferred<AnalysisResult>()
        var calls = 0
        val picker = show(
            loader = PhotoLoader { uri ->
                if (uri.lastPathSegment == "bad") {
                    PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
                } else {
                    captureLoaded()
                }
            },
            analyzer = VisionAnalyzer {
                if (calls++ == 0) {
                    withContext(NonCancellable) { pending.await() }
                } else {
                    AnalysisResult.Success(emptyList())
                }
            }
        )
        try {
            pick(picker)
            compose.onNodeWithText("Analyse en cours…").assertIsDisplayed()
            picker.next = Uri.parse("content://synthetic/bad")
            compose.onNodeWithContentDescription("Galerie").performClick()
            compose.onNodeWithContentDescription("Image analysée").assertIsDisplayed()
            compose.onNodeWithText("Analyser").assertIsDisplayed().performClick()
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            pending.complete(AnalysisResult.Success(listOf(captureDetection())))
            compose.waitForIdle()
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            assertEquals(2, calls)
        } finally {
            pending.complete(AnalysisResult.Failed)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun overlappingObjectsCanBeChosenInThePanelAndRetappingClearsTheFill() {
        val corners = listOf(
            ImagePoint(240f, 180f),
            ImagePoint(400f, 180f),
            ImagePoint(400f, 300f),
            ImagePoint(240f, 300f)
        )
        val inner = captureDetection(16).copy(
            box = ImageBox(240f, 180f, 400f, 300f),
            mask = MaskGrid(
                160,
                160,
                BooleanArray(160 * 160) {
                    it % 160 in 60 until 100 && it / 160 in 65 until 95
                }
            ),
            contours = corners.indices.map { ContourSegment(corners[it], corners[(it + 1) % 4]) }
        )
        lateinit var view: View
        var analyses = 0
        val picker = show(
            analyzer = VisionAnalyzer {
                analyses++
                AnalysisResult.Success(listOf(captureDetection(), inner))
            },
            captureView = { view = it }
        )
        pick(picker)
        val image = compose.onNodeWithContentDescription("Image analysée")
        image.performTouchInput { click(center) }
        compose.onNodeWithText("Objets superposés à cet endroit").assertIsDisplayed()
        compose.onNodeWithText("Chien").assertIsDisplayed()
        compose.onNodeWithText("Chien · 2").assertIsSelected()
        awaitPhotoLayout()
        val innerFill = shownPixel(view, image.fetchSemanticsNode().boundsInRoot.center)
        assertTrue(Color.red(innerFill) in 37 until 255)
        compose.onNodeWithText("Chat · 1").performClick()
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onNodeWithText("Chat · 1").assertIsSelected()
        awaitPhotoLayout()
        val outerFill = shownPixel(view, image.fetchSemanticsNode().boundsInRoot.center)
        assertNotEquals(innerFill, outerFill)
        image.performTouchInput { click(this.center) }
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithText("2 objets détectés. Touchez un objet pour le sélectionner.")
            .assertIsDisplayed()
        assertEquals(
            Color.rgb(36, 48, 64),
            shownPixel(view, image.fetchSemanticsNode().boundsInRoot.center)
        )
        assertEquals(1, analyses)
    }

    @Test fun replacingAnImageShowsLoadingWhileKeepingItsUsableResults() {
        val pending = CompletableDeferred<PhotoLoadResult>()
        val picker = show(
            loader = PhotoLoader { uri ->
                if (uri.lastPathSegment == "slow") pending.await() else captureLoaded()
            },
            analyzer = VisionAnalyzer { AnalysisResult.Success(listOf(captureDetection())) }
        )
        try {
            pick(picker)
            picker.next = Uri.parse("content://synthetic/slow")
            compose.onNodeWithContentDescription("Galerie").performClick()
            compose.onNodeWithText("Chargement de l’image…").assertIsDisplayed()
            compose.onNodeWithContentDescription("Image analysée").assertIsDisplayed()
            compose.onNodeWithText("Analyser").assertIsNotEnabled()
            compose.onNodeWithText("Analyser").assertIsNotEnabled()
            pending.complete(PhotoLoadResult.Failed(ImageImportError.UNREADABLE))
            compose.onNodeWithText("Chargement de l’image…").assertDoesNotExist()
            compose.onNodeWithContentDescription("Image analysée").performTouchInput {
                click(center)
            }
            compose.onNodeWithText("Chat").assertIsDisplayed()
        } finally {
            pending.complete(PhotoLoadResult.Failed(ImageImportError.UNREADABLE))
        }
    }

    @Test fun aSuccessfulReplacementUsesTheSelectedUriAndAnalyzesItsNewPixelsAutomatically() {
        val references = mutableListOf<String>()
        val widths = mutableListOf<Int>()
        val picker = show(
            loader = PhotoLoader { uri ->
                references.add(uri.toString())
                if (uri.lastPathSegment == "other") captureLoaded(200, 100) else captureLoaded()
            },
            analyzer = VisionAnalyzer {
                widths.add(it.width)
                AnalysisResult.Success(emptyList())
            }
        )
        pick(picker)
        picker.next = Uri.parse("content://synthetic/other")
        compose.onNodeWithContentDescription("Galerie").performClick()
        compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
        assertEquals(listOf("content://synthetic/photo", "content://synthetic/other"), references)
        assertEquals(listOf(640, 200), widths)
    }

    @Test fun aLatePickerLoadCannotReplaceTheNewerImageOrTriggerItsAnalysis() {
        val pending = CompletableDeferred<PhotoLoadResult>()
        val entered = CompletableDeferred<Unit>()
        val old = captureLoaded()
        val widths = mutableListOf<Int>()
        val picker = show(
            loader = PhotoLoader { uri ->
                if (uri.lastPathSegment == "slow") {
                    entered.complete(Unit)
                    withContext(NonCancellable) { pending.await() }
                } else {
                    captureLoaded(200, 100)
                }
            },
            analyzer = VisionAnalyzer {
                widths.add(it.width)
                AnalysisResult.Success(emptyList())
            }
        )
        try {
            picker.next = Uri.parse("content://synthetic/slow")
            compose.onNodeWithContentDescription("Galerie").performClick()
            compose.waitUntil { entered.isCompleted }
            picker.next = Uri.parse("content://synthetic/new")
            compose.onNodeWithContentDescription("Galerie").performClick()
            compose.onNodeWithText("Aucun objet reconnu à ce seuil.").assertIsDisplayed()
            pending.complete(old)
            compose.waitForIdle()
            assertEquals(listOf(200), widths)
            compose.onNodeWithContentDescription("Image analysée").assertIsDisplayed()
        } finally {
            pending.complete(old)
        }
    }

    @Test fun eachPickerFailureRemainsActionableInTheUnifiedEntry() {
        val picker = show(
            loader = PhotoLoader { uri ->
                PhotoLoadResult.Failed(ImageImportError.valueOf(checkNotNull(uri.lastPathSegment)))
            },
            analyzer = VisionAnalyzer { error("An unreadable image must not be analyzed") }
        )
        val messages = mapOf(
            ImageImportError.TOO_LARGE to "Image trop grande : 30 Mio ou 100 mégapixels maximum.",
            ImageImportError.UNSUPPORTED to
                "Format non pris en charge. Choisissez JPEG, PNG, WebP ou HEIF.",
            ImageImportError.UNREADABLE to
                "Image illisible ou accès indisponible. Choisissez une autre image.",
            ImageImportError.MEMORY to "Mémoire insuffisante. Choisissez une image plus petite."
        )
        messages.forEach { (reason, message) ->
            picker.next = Uri.parse("content://synthetic/${reason.name}")
            compose.onNodeWithContentDescription("Galerie").performClick()
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithContentDescription("Galerie").assertIsEnabled()
        }
    }

    private fun show(
        loader: PhotoLoader = PhotoLoader { captureLoaded() },
        analyzer: VisionAnalyzer = VisionAnalyzer { AnalysisResult.Success(emptyList()) },
        camera: PreviewProviderLoader? = null,
        captureView: (View) -> Unit = {}
    ): CapturePicker {
        val picker = CapturePicker(RuntimeEnvironment.getApplication())
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                captureView(LocalView.current)
                BlameraApp(loader, analyzer, camera)
            }
        }
        return picker
    }

    private fun pick(picker: CapturePicker) {
        picker.next = Uri.parse("content://synthetic/photo")
        compose.onNodeWithContentDescription("Galerie").performClick()
    }

    private fun shownPixel(view: View, point: Offset): Int {
        var color = 0
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            color = bitmap.getPixel(point.x.toInt(), point.y.toInt())
            bitmap.recycle()
        }
        return color
    }

    private fun awaitPhotoLayout() {
        // Wait for the common toolbar layout before sampling mask pixels.
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Lens").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
