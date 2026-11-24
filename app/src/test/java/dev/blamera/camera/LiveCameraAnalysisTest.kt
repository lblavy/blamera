package dev.blamera.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.view.transform.OutputTransform
import dev.blamera.vision.AnalysisResult
import dev.blamera.vision.VisionAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class LiveCameraAnalysisTest {
    private val transform = OutputTransform(Matrix(), Size(2, 2))
    private class Frame : LiveFrame {
        @Volatile var closes = 0

        @Volatile var copies = 0
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        override fun bitmap(): Bitmap {
            copies++
            return image
        }
        override fun transform() = OutputTransform(Matrix(), Size(2, 2))
        override fun close() {
            closes++
        }
    }

    @Test fun oneInFlightRejectedFramesClosedAndStaleSuccessIgnored() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reports = mutableListOf<LiveResult?>()
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = LiveFrameGate { 0 }
        val processor = LiveCameraAnalysis(
            VisionAnalyzer {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                AnalysisResult.Success(emptyList())
            },
            worker,
            { transform },
            reports::add,
            gate,
            Dispatchers.Unconfined
        )
        val first = Frame()
        processor.offer(first)
        withTimeout(5000) { entered.await() }
        val rejected = Frame()
        processor.offer(rejected)
        assertEquals(1, rejected.closes)
        assertEquals(0, rejected.copies)
        processor.invalidate()
        release.complete(Unit)
        withTimeout(5000) { while (first.closes == 0) delay(5) }
        assertEquals(1, first.closes)
        assertTrue(first.image.isRecycled)
        assertTrue(reports.all { it == null })
        processor.close()
        val late = Frame()
        processor.offer(late)
        assertEquals(1, late.closes)
        worker.cancel()
    }

    @Test fun invalidFrameReportsErrorAndReleasesResources() = runBlocking {
        val reports = java.util.concurrent.CopyOnWriteArrayList<LiveResult?>()
        val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler {
                        _,
                        _
                    ->
                }
            )
        val closed = CompletableDeferred<Unit>()
        val bad = object : LiveFrame {
            override fun bitmap(): Bitmap = throw IllegalStateException("invalid frame")
            override fun transform() = this@LiveCameraAnalysisTest.transform
            override fun close() {
                closed.complete(Unit)
            }
        }
        val live =
            LiveCameraAnalysis(
                VisionAnalyzer {
                    error("not reached")
                },
                scope,
                { transform },
                reports::add,
                LiveFrameGate { 0 },
                Dispatchers.Unconfined
            )
        live.offer(bad)
        withTimeout(5000) { closed.await() }
        assertTrue(reports.any { it?.result == AnalysisResult.Failed })
        scope.cancel()
    }

    @Test fun disabledMissingTransformAndSuccessEmptyNeverReusePreviousResult() = runBlocking {
        val reports = java.util.concurrent.CopyOnWriteArrayList<LiveResult?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var now = 0L
        var targetReady = false
        val live = LiveCameraAnalysis(
            VisionAnalyzer { AnalysisResult.Success(emptyList()) },
            scope,
            {
                if (targetReady) transform else null
            },
            reports::add,
            LiveFrameGate { now },
            Dispatchers.Unconfined
        )
        val missing = Frame()
        live.offer(missing)
        assertEquals(1, missing.closes)
        assertEquals(0, missing.copies)
        now = 1000
        targetReady = true
        val first = Frame()
        live.offer(first)
        withTimeout(5000) { while (first.closes == 0) delay(5) }
        assertEquals(AnalysisResult.Success(emptyList()), reports.last()?.result)
        live.setEnabled(false)
        assertEquals(null, reports.last())
        val disabled = Frame()
        live.offer(disabled)
        assertEquals(1, disabled.closes)
        assertEquals(0, disabled.copies)
        live.setEnabled(true)
        now = 2000
        val next = Frame()
        live.offer(next)
        withTimeout(5000) { while (next.closes == 0) delay(5) }
        assertFalse(reports.last() == null)
        live.close()
        scope.cancel()
    }

    @Test fun secondInstanceCannotCopyWhileCancelledNativeWorkStillOwnsFirstBitmap() = runBlocking {
        var now = 0L
        val gate = LiveFrameGate { now }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = LiveCameraAnalysis(
            VisionAnalyzer {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                AnalysisResult.Success(emptyList())
            },
            scope,
            { transform },
            {},
            gate,
            Dispatchers.Unconfined
        )
        val frame = Frame()
        first.offer(frame)
        withTimeout(5000) { entered.await() }
        first.close()
        assertFalse(frame.image.isRecycled)
        now = 2000
        val second =
            LiveCameraAnalysis(
                VisionAnalyzer { AnalysisResult.Success(emptyList()) },
                scope,
                { transform },
                {},
                gate,
                Dispatchers.Unconfined
            )
        val rejected = Frame()
        second.offer(rejected)
        assertEquals(1, rejected.closes)
        assertEquals(0, rejected.copies)
        release.complete(Unit)
        withTimeout(5000) { while (frame.closes == 0) delay(5) }
        assertTrue(frame.image.isRecycled)
        val accepted = Frame()
        second.offer(accepted)
        withTimeout(5000) { while (accepted.closes == 0) delay(5) }
        assertEquals(1, accepted.copies)
        second.close()
        scope.cancel()
    }

    @Test fun missingTransformAndCancelledScopeStillCloseWithoutCopy() = runBlocking {
        val scope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Unconfined)
        val frame = Frame()
        LiveCameraAnalysis(
            VisionAnalyzer {
                error("no analysis")
            },
            scope,
            { null },
            {},
            callbacks = Dispatchers.Unconfined
        ).offer(frame)
        assertEquals(1, frame.closes)
        assertEquals(0, frame.copies)
    }
}
