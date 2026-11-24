package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.faces.FaceGallerySettings
import dev.blamera.faces.FaceMemoryStore
import dev.blamera.faces.FacePhotoAnalysis
import dev.blamera.faces.FacePhotoState
import dev.blamera.faces.faceDecoder
import dev.blamera.faces.faceFixture
import dev.blamera.faces.faceVector
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.faces.runtime.FaceEngine
import dev.blamera.faces.runtime.FaceEngineResult
import dev.blamera.faces.runtime.FaceObservation
import dev.blamera.faces.runtime.FaceRgbFrame
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PreviewDimensions
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceCaptureSessionTest {
    @Test fun photoRegionAndOriginalAlwaysReceiveTheirOwnFaceResults() = runBlocking {
        val gallery = FaceGallerySettings(this, FaceMemoryStore(faceFixture()), faceDecoder())
        val engine = CaptureFaceEngine { frame ->
            val vector = if (frame.width == 40) faceVector(1.0) else faceVector(0.0, 1.0)
            FaceEngineResult.Detected(
                listOf(
                    FaceObservation.Encoded(
                        FaceBounds(0f, 0f, frame.width.toFloat(), frame.height.toFloat()),
                        vector
                    )
                )
            )
        }
        val recognition = FacePhotoAnalysis(this, gallery, engine)
        val session = CaptureSession(this, faces = gallery, recognition = recognition)
        val original = photo(40, 20)
        session.accept(original, analyzer)
        await { recognition.state is FacePhotoState.Detected }
        assertEquals(
            "Personne synthétique A",
            (recognition.state as FacePhotoState.Detected).identity.name
        )
        val interaction = checkNotNull(session.interaction)
        interaction.size = Size(40f, 20f)
        interaction.move(Offset(20f, 10f), Offset.Zero, 2f)
        val crop = photo(20, 10)
        session.analyzeVisible(analyzer, PhotoCropper { _, _ -> PhotoLoadResult.Loaded(crop) })
        await {
            (recognition.state as? FacePhotoState.Detected)?.identity?.name ==
                "Personne synthétique B"
        }
        session.restoreOriginal()
        await {
            (recognition.state as? FacePhotoState.Detected)?.identity?.name ==
                "Personne synthétique A"
        }
        assertSame(original, session.photo)
        assertEquals(40f, (recognition.state as FacePhotoState.Detected).bounds.right)
        session.navigate(CaptureScreen.CAMERA)
        assertSame(FacePhotoState.Empty, recognition.state)
        session.close()
        assertEquals(1, engine.closes)
        assertFalse(original.bitmap.isRecycled)
    }

    @Test fun backgroundRejectsLateFaceAndForegroundRestartsWithoutBlockingObjects() = runBlocking {
        val gallery = FaceGallerySettings(this, FaceMemoryStore(faceFixture()), faceDecoder())
        val pending = CompletableDeferred<FaceEngineResult>()
        var calls = 0
        val result = FaceEngineResult.Detected(
            listOf(FaceObservation.Encoded(FaceBounds(0f, 0f, 40f, 20f), faceVector(1.0)))
        )
        val engine = CaptureFaceEngine {
            if (++calls == 1) withContext(NonCancellable) { pending.await() } else result
        }
        val recognition = FacePhotoAnalysis(this, gallery, engine)
        val session = CaptureSession(this, faces = gallery, recognition = recognition)
        session.accept(photo(40, 20), analyzer)
        await { calls == 1 && !session.running }
        assertTrueObjects(session)
        session.onBackground()
        pending.complete(result)
        repeat(20) { yield() }
        assertSame(FacePhotoState.Empty, recognition.state)
        session.onForeground()
        await { recognition.state is FacePhotoState.Detected }
        assertEquals(2, calls)
        assertTrueObjects(session)
        session.close()
    }

    private fun assertTrueObjects(session: CaptureSession) {
        org.junit.Assert.assertTrue(session.result is AnalysisResult.Success)
    }

    private fun photo(width: Int, height: Int) = DecodedPhoto(
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
        PreviewDimensions(width, height)
    )
    private suspend fun await(condition: () -> Boolean) {
        repeat(2000) {
            yield()
            if (condition()) return
            kotlinx.coroutines.delay(1)
        }
        error("Capture face result did not arrive")
    }
    private val analyzer = VisionAnalyzer { AnalysisResult.Success(emptyList()) }
    private val FacePhotoState.Detected.identity get() = requireNotNull(faces.single().identity)
    private val FacePhotoState.Detected.bounds get() = faces.single().bounds
}

internal class CaptureFaceEngine(private val action: suspend (FaceRgbFrame) -> FaceEngineResult) :
    FaceEngine {
    var closes = 0
    override suspend fun analyze(frame: FaceRgbFrame) = action(frame)
    override fun close() {
        closes++
    }
}
