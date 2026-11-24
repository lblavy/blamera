package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.blamera.image.ImageCrop
import dev.blamera.image.PhotoCropper
import dev.blamera.image.PhotoLoadResult
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoRegionSessionTest {
    @Test fun absentPhotoViewportAndBusyAnalysisIgnoreLateRegionRequests() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<AnalysisResult>()
        var calls = 0
        val analyzer = VisionAnalyzer {
            calls++
            withContext(NonCancellable) { pending.await() }
        }
        val cropper = PhotoCropper { _, _ -> error("No region should be copied") }
        try {
            val session = CaptureSession(scope)
            assertNull(session.selected)
            assertTrue(session.hits.isEmpty())
            session.analyzeVisible(analyzer, cropper)
            assertEquals(0, calls)
            session.accept(capturePhoto(400, 200), analyzer)
            assertTrue(session.running)
            assertNull(session.interaction!!.region)
            session.analyzeVisible(analyzer, cropper)
            zoom(session)
            session.analyzeVisible(analyzer, cropper)
            session.analyzeVisible(analyzer, cropper)
            assertEquals(1, calls)
            assertFalse(session.isRegion)
            pending.complete(AnalysisResult.Success(emptyList()))
            session.interaction!!.size = Size.Zero
            session.analyzeVisible(analyzer, cropper)
            assertEquals(1, calls)
            assertFalse(session.isRegion)
        } finally {
            pending.complete(AnalysisResult.Failed)
            scope.cancel()
        }
    }

    @Test fun repeatedRegionsKeepOneOriginalAndRestoreItsResultsWithoutInference() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val session = CaptureSession(scope)
            val seen = mutableListOf<Bitmap>()
            val analyzer = VisionAnalyzer {
                seen.add(it)
                AnalysisResult.Success(emptyList())
            }
            val original = capturePhoto(400, 200)
            session.accept(original, analyzer)
            val originalResult = session.result
            val regions = mutableListOf<ImageCrop>()
            val cropper = PhotoCropper { _, crop ->
                regions.add(crop)
                PhotoLoadResult.Loaded(capturePhoto(crop.width, crop.height))
            }
            zoom(session)
            session.analyzeVisible(analyzer, cropper)
            assertEquals(2, seen.size)
            assertEquals(ImageCrop(100, 50, 300, 150), regions.single())
            assertEquals(200, seen.last().width)
            assertEquals(100, seen.last().height)
            assertSame(session.photo!!.bitmap, seen.last())
            assertTrue(session.isRegion)
            assertEquals(1f, session.interaction!!.exploration.zoom, 0.001f)
            zoom(session)
            session.analyzeVisible(analyzer, cropper)
            assertEquals(3, seen.size)
            assertEquals(100, seen.last().width)
            session.restoreOriginal()
            assertSame(original, session.photo)
            assertSame(originalResult, session.result)
            assertFalse(session.isRegion)
            assertEquals(1f, session.interaction!!.exploration.zoom, 0.001f)
            assertEquals(3, seen.size)
        } finally {
            scope.cancel()
        }
    }

    @Test fun restoreRejectsLateRegionResultsAndReplacementResetsTheOrigin() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<AnalysisResult>()
        try {
            val session = CaptureSession(scope)
            var calls = 0
            val analyzer = VisionAnalyzer {
                if (++calls == 2) {
                    withContext(NonCancellable) { pending.await() }
                } else {
                    AnalysisResult.Success(emptyList())
                }
            }
            val original = capturePhoto(400, 200)
            session.accept(original, analyzer)
            val rootResult = session.result
            zoom(session)
            session.analyzeVisible(
                analyzer,
                PhotoCropper { _, crop ->
                    PhotoLoadResult.Loaded(capturePhoto(crop.width, crop.height))
                }
            )
            assertTrue(session.running)
            session.restoreOriginal()
            assertSame(original, session.photo)
            assertSame(rootResult, session.result)
            pending.complete(AnalysisResult.Failed)
            assertSame(rootResult, session.result)
            val replacement = capturePhoto(300, 150)
            session.accept(replacement, analyzer)
            session.restoreOriginal()
            assertSame(replacement, session.photo)
            assertFalse(session.isRegion)
        } finally {
            pending.complete(AnalysisResult.Failed)
            scope.cancel()
        }
    }

    @Test fun aNewImageCannotBeReplacedByACancelledRegionCopy() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<PhotoLoadResult>()
        try {
            val session = CaptureSession(scope)
            val analyzer = VisionAnalyzer { AnalysisResult.Success(emptyList()) }
            session.accept(capturePhoto(400, 200), analyzer)
            zoom(session)
            session.analyzeVisible(
                analyzer,
                PhotoCropper { _, _ ->
                    withContext(NonCancellable) { pending.await() }
                }
            )
            assertTrue(session.loading)
            val replacement = capturePhoto(300, 150)
            session.accept(replacement, analyzer)
            pending.complete(PhotoLoadResult.Loaded(capturePhoto(200, 100)))
            assertSame(replacement, session.photo)
            assertFalse(session.isRegion)
            assertFalse(replacement.bitmap.isRecycled)
        } finally {
            pending.complete(PhotoLoadResult.Loaded(capturePhoto(200, 100)))
            scope.cancel()
        }
    }

    private fun zoom(session: CaptureSession) {
        val interaction = checkNotNull(session.interaction)
        interaction.size = Size(400f, 200f)
        interaction.move(Offset(200f, 100f), Offset.Zero, 2f)
    }
}
