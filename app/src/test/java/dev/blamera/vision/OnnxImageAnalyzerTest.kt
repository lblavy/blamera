package dev.blamera.vision

import android.graphics.Bitmap
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnnxImageAnalyzerTest {
    @Test
    fun successRunsOffTheCallerAndReportsOnlyDurations() = runBlocking {
        val caller = Thread.currentThread()
        var timing: VisionTiming? = null
        val analyzer = OnnxImageAnalyzer({ input ->
            assertTrue(Thread.currentThread() != caller)
            assertEquals(3 * 640 * 640, input.size)
            emptyOutput()
        }, { timing = it })
        assertEquals(AnalysisResult.Success(emptyList()), analyzer.analyze(bitmap()))
        assertTrue(checkNotNull(timing).totalMillis >= 0)
        assertEquals(2L, checkNotNull(timing).loadMillis)
    }

    @Test
    fun expectedBoundaryErrorsAreRecoverable() = runBlocking {
        val errors =
            listOf(
                IOException(),
                VisionException("bad shape"),
                IllegalArgumentException(),
                IllegalStateException(),
                OutOfMemoryError(),
                UnsatisfiedLinkError()
            )
        for (error in errors) {
            assertEquals(
                AnalysisResult.Failed,
                OnnxImageAnalyzer({
                    throw error
                }).analyze(bitmap())
            )
        }
    }

    @Test
    fun cancellationAcrossTwoInstancesWaitsForTheOldNativeOperation() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondCalls = AtomicInteger()
        val first =
            OnnxImageAnalyzer({
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                emptyOutput()
            })
        val second = OnnxImageAnalyzer({
            secondCalls.incrementAndGet()
            emptyOutput()
        })
        val job = launch(Dispatchers.Default) { first.analyze(bitmap()) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            val next = launch(Dispatchers.Default) { second.analyze(bitmap()) }
            Thread.sleep(50)
            assertEquals(0, secondCalls.get())
            release.countDown()
            job.cancelAndJoin()
            next.join()
            assertEquals(1, secondCalls.get())
        } finally {
            release.countDown()
            job.cancelAndJoin()
        }
    }

    private fun bitmap() = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
    private fun emptyOutput() =
        InferenceOutput(RawSegmentation(FloatArray(116 * 8400), FloatArray(32 * 160 * 160)), 2, 3)
}
