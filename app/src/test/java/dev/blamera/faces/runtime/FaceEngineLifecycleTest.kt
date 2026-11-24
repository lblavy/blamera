package dev.blamera.faces.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FaceEngineLifecycleTest {
    private val frame = FaceRgbFrame(640, 640, ByteArray(640 * 640 * 3))

    @Test fun closeDefersResourceDisposalUntilNativeWorkHasReturned() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val kernel =
            Kernel(detecting = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            })
        val engine = VerifiedFaceEngine { kernel }
        val result = async(Dispatchers.Default) { engine.analyze(frame) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            engine.close()
            assertEquals(0, kernel.closed.get())
            result.cancel()
        } finally {
            release.countDown()
        }
        try {
            result.await()
            fail("Cancellation must suppress an obsolete native result")
        } catch (_: CancellationException) {
            // Resources are released in the worker's finally block.
        }
        assertEquals(1, kernel.closed.get())
        engine.close()
        assertEquals(1, kernel.closed.get())
    }

    @Test fun callsAreSerializedAndCancelledOpeningCanRetry() = runBlocking {
        var attempts = 0
        val kernel = Kernel()
        val engine = VerifiedFaceEngine {
            attempts++
            if (attempts == 1) throw CancellationException()
            kernel
        }
        try {
            engine.analyze(frame)
            fail("Opening cancellation must propagate")
        } catch (_: CancellationException) {
            // No kernel was adopted.
        }
        val results = List(3) {
            async(Dispatchers.Default) { engine.analyze(frame) }
        }.map { it.await() }
        assertTrue(results.all { it is FaceEngineResult.Detected })
        assertEquals(2, attempts)
        engine.close()
        assertEquals(1, kernel.closed.get())
    }

    @Test fun nativeRuntimeLinkageAndMemoryFailuresExposeOnlyTheTypedError() = runBlocking {
        listOf<Throwable>(
            IllegalStateException("native details"),
            UnsatisfiedLinkError("path"),
            OutOfMemoryError("buffer")
        )
            .forEach { failure ->
                val engine = VerifiedFaceEngine { throw failure }
                typedFailure { engine.analyze(frame) }
                engine.close()
            }
    }

    @Test fun invalidNativeEncodingIsLocalAndInvalidFramesNeverPublishResults() = runBlocking {
        listOf(
            FaceEncoding(byteArrayOf(), List(512) { 1.0 }),
            FaceEncoding(ByteArray(112 * 112 * 3), List(512) { 0.0 })
        ).forEach { output ->
            val engine = VerifiedFaceEngine { Kernel(output = output) }
            val result = engine.analyze(frame) as FaceEngineResult.Detected
            assertTrue(result.faces.single() is FaceObservation.Unavailable)
            engine.close()
        }
        var opened = false
        val engine = VerifiedFaceEngine {
            opened = true
            Kernel()
        }
        typedFailure { engine.analyze(frame.copy(width = 0)) }
        assertTrue(!opened)
        engine.close()
    }

    private suspend fun typedFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("A native failure must not expose model internals")
        } catch (failed: FaceEngineException) {
            assertEquals("Analyse des visages indisponible.", failed.message)
        }
    }

    private class Kernel(
        val detecting: () -> Unit = {},
        val output: FaceEncoding = FaceEncoding(
            ByteArray(112 * 112 * 3),
            List(512) {
                if (it ==
                    0
                ) {
                    1.0
                } else {
                    0.0
                }
            }
        )
    ) : FaceKernel {
        val closed = AtomicInteger()
        override fun detect(
            frame: FaceRgbFrame,
            transform: FaceDetectorTransform
        ): List<List<Double>> {
            detecting()
            return listOf(
                listOf(
                    20.0, 20.0, 40.0, 40.0,
                    25.0, 30.0, 45.0, 30.0, 35.0, 40.0, 25.0, 50.0, 45.0, 50.0, .95
                )
            )
        }
        override fun encode(frame: FaceRgbFrame, detection: FaceDetection) = output
        override fun close() {
            closed.incrementAndGet()
        }
    }
}
