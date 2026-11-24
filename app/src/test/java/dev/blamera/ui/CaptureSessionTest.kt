package dev.blamera.ui

import android.graphics.Bitmap
import android.net.Uri
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageImportError
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PhotoLoader
import dev.blamera.image.PreviewDimensions
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
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureSessionTest {
    @Test fun aNewImageStartsOneAnalysisAndNavigationRejectsItsLateResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<AnalysisResult>()
        val session = CaptureSession(scope)
        var calls = 0
        val photo = photo()
        try {
            session.accept(
                photo,
                VisionAnalyzer {
                    calls++
                    withContext(NonCancellable) { pending.await() }
                }
            )
            assertSame(photo, session.photo)
            assertEquals(CaptureScreen.PHOTO, session.screen)
            assertEquals(1, calls)
            session.navigate(CaptureScreen.CAMERA)
            pending.complete(AnalysisResult.Success(emptyList()))
            assertFalse(session.running)
            assertEquals(null, session.result)
            assertEquals(CaptureScreen.CAMERA, session.screen)
            assertFalse(photo.bitmap.isRecycled)
        } finally {
            pending.complete(AnalysisResult.Failed)
            scope.cancel()
        }
    }

    @Test fun aFailedReplacementKeepsThePreviousImageAndResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val session = CaptureSession(scope)
            val photo = photo()
            val analyzer = VisionAnalyzer { AnalysisResult.Success(emptyList()) }
            session.accept(photo, analyzer)
            val previous = session.result
            session.import(
                Uri.EMPTY,
                PhotoLoader {
                    PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
                },
                analyzer
            )
            assertSame(photo, session.photo)
            assertSame(previous, session.result)
            assertEquals(
                "Image illisible ou accès indisponible. Choisissez une autre image.",
                session.error
            )
        } finally {
            scope.cancel()
        }
    }

    @Test fun aLateImportCannotReplaceANewerPhotoOrItsSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<PhotoLoadResult>()
        try {
            val session = CaptureSession(scope)
            val analyzer =
                VisionAnalyzer {
                    AnalysisResult.Success(listOf(captureDetection(), captureDetection(16)))
                }
            session.import(
                Uri.EMPTY,
                PhotoLoader {
                    withContext(NonCancellable) { pending.await() }
                },
                analyzer
            )
            val latest = photo()
            session.accept(latest, analyzer)
            session.interaction!!.selectHits(listOf(0, 1))
            assertEquals(0, session.selected)
            session.interaction!!.select(1)
            assertEquals(1, session.selected)
            session.interaction!!.select(99)
            assertEquals(1, session.selected)
            pending.complete(captureLoaded())
            assertSame(latest, session.photo)
            assertEquals(1, session.selected)
            session.interaction!!.select(1)
            assertEquals(null, session.selected)
            session.navigate(CaptureScreen.CAMERA)
            assertEquals(CaptureScreen.CAMERA, session.screen)
            assertEquals(emptyList<Int>(), session.hits)
        } finally {
            pending.complete(captureLoaded())
            scope.cancel()
        }
    }

    @Test fun retryNeedsAPhotoAndImportFailuresRemainActionable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val session = CaptureSession(scope)
            var calls = 0
            val analyzer = VisionAnalyzer {
                calls++
                AnalysisResult.Failed
            }
            session.retry(analyzer)
            assertEquals(0, calls)
            session.accept(photo(), analyzer)
            session.retry(analyzer)
            assertEquals(2, calls)
            val messages = ImageImportError.entries.map(::importError)
            assertEquals(4, messages.toSet().size)
            messages.forEach { org.junit.Assert.assertTrue(it.isNotBlank()) }
        } finally {
            scope.cancel()
        }
    }

    private fun photo() = DecodedPhoto(
        Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888),
        PreviewDimensions(8, 4)
    )
}
