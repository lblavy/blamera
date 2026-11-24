package dev.blamera.benchmark

import android.graphics.Bitmap
import dev.blamera.vision.InferenceOutput
import dev.blamera.vision.OnnxImageAnalyzer
import dev.blamera.vision.RawSegmentation
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
class BenchmarkConcurrencyTest {
    @Test fun benchmarkWaitsForCancelledImageInference() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val opened = AtomicInteger()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val analyzer = OnnxImageAnalyzer({
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            InferenceOutput(
                RawSegmentation(FloatArray(116 * 8400), FloatArray(32 * 160 * 160)),
                0,
                0
            )
        })
        val old = launch(Dispatchers.Default) { analyzer.analyze(image) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        old.cancel()
        val protocol = BenchmarkProtocol(
            BenchmarkBackend {
                opened.incrementAndGet()
                object : BenchmarkSession {
                    override val readHashNanos = 0L
                    override val sessionNanos = 0L
                    override fun input(values: FloatArray): BenchmarkExecution =
                        error("no input in this lock test")
                    override fun close() = Unit
                }
            }
        )
        val next =
            launch(Dispatchers.Default) {
                protocol.compare(listOf(BenchmarkModel("b", "a", "h", 1, 80)), emptyList())
            }
        try {
            Thread.sleep(80)
            assertEquals(0, opened.get())
        } finally {
            release.countDown()
            old.cancelAndJoin()
            next.join()
            image.recycle()
        }
        assertEquals(1, opened.get())
    }
}
