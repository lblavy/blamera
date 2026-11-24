package dev.blamera.faces.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.opencv.core.CvException

class FaceEngineMultipleTest {
    private val frame = FaceRgbFrame(640, 640, ByteArray(640 * 640 * 3))

    @Test fun nativeMemoryFailureOnFaceTwoNeverPublishesOrEncodesFaceThree() = runBlocking {
        val kernel = Kernel(nativeMemoryFailure = true)
        var published = 0
        val engine = VerifiedFaceEngine(report = { _, _, _ -> published++ }) { kernel }
        try {
            engine.analyze(frame)
            fail("Native allocation failure must abort instead of returning a partial group")
        } catch (_: FaceEngineException) {
            assertEquals(2, kernel.encodings)
            assertEquals(0, published)
        } finally {
            engine.close()
        }
        assertEquals(1, kernel.closed)
    }

    @Test fun diagnosticsReportTheNumberOfFacesWithoutTheirIdentityOrEmbedding() = runBlocking {
        var count = -1
        var kind = ""
        val engine = VerifiedFaceEngine(report = { resultKind, size, _ ->
            kind = resultKind
            count =
                size
        }) { Kernel() }
        engine.analyze(frame)
        assertEquals("multiple", kind)
        assertEquals(3, count)
        engine.close()
    }

    @Test fun oneDetectionEncodesEveryFaceWithoutMixingBoundsAndEmbeddings() = runBlocking {
        val kernel = Kernel()
        VerifiedFaceEngine { kernel }.use { engine ->
            val result = engine.analyze(frame)
            assertTrue("Every visible face must be encoded", result is FaceEngineResult.Detected)
            val faces = (result as FaceEngineResult.Detected).faces
            assertEquals(listOf(120f, 20f, 220f), faces.map { it.bounds.left })
            assertEquals(
                listOf(1, 0, 2),
                faces.map {
                    (it as FaceObservation.Encoded).embedding.indexOf(1.0)
                }
            )
            assertEquals(1, kernel.detections)
            assertEquals(3, kernel.encodings)
        }
        assertEquals(1, kernel.closed)
    }

    @Test fun failedEncodingKeepsThatFaceExplicitAndPreservesOtherFaces() = runBlocking {
        val kernel = Kernel(failureAt = 20f)
        VerifiedFaceEngine { kernel }.use { engine ->
            val result = engine.analyze(frame)
            assertTrue(
                "One failed face must not discard the group",
                result is FaceEngineResult.Detected
            )
            val faces = (result as FaceEngineResult.Detected).faces
            assertTrue(faces[0] is FaceObservation.Encoded)
            assertEquals(
                FaceBounds(20f, 20f, 60f, 60f),
                (faces[1] as FaceObservation.Unavailable).bounds
            )
            assertTrue(faces[2] is FaceObservation.Encoded)
            assertEquals(3, kernel.encodings)
        }
    }

    @Test fun cancelledBatchStopsBeforeNextEncodingAndClosesOnce() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val kernel = Kernel(afterEncoding = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        })
        val engine = VerifiedFaceEngine { kernel }
        val running = async(Dispatchers.Default) { engine.analyze(frame) }
        try {
            assertTrue("Multiple faces must start encoding", entered.await(5, TimeUnit.SECONDS))
            running.cancel()
            engine.close()
            assertEquals(0, kernel.closed)
        } finally {
            release.countDown()
        }
        try {
            running.await()
            fail("Cancelled work cannot publish a partial list")
        } catch (_: CancellationException) {
            // The native call returned before resources were freed.
        }
        assertEquals(1, kernel.encodings)
        assertEquals(1, kernel.closed)
    }

    @Test fun resultListsAndEmbeddingsAreDefensiveImmutableSnapshots() {
        val input = mutableListOf<FaceObservation>()
        val vector = MutableList(512) { if (it == 0) 1.0 else 0.0 }
        val encoded = FaceObservation.Encoded(FaceBounds(0f, 0f, 1f, 1f), vector)
        input += encoded
        val result = FaceEngineResult.Detected(input)
        vector[0] = 0.0
        input.clear()
        assertEquals(1, result.faces.size)
        assertEquals(1.0, encoded.embedding[0], 0.0)
    }

    @Test fun moreThanTwentyFacesIsExplicitlyRefusedBeforeAnyEncoding() = runBlocking {
        val kernel = Kernel(count = 21)
        VerifiedFaceEngine { kernel }.use { engine ->
            assertEquals(FaceEngineResult.TooManyFaces, engine.analyze(frame))
            assertEquals(0, kernel.encodings)
        }
    }

    @Test fun invalidLandmarksKeepTheBoxUnidentifiedAndOtherFacesStillEncode() = runBlocking {
        val kernel = Kernel(invalidLandmarks = true)
        VerifiedFaceEngine { kernel }.use { engine ->
            val result = engine.analyze(frame)
            assertTrue(result is FaceEngineResult.Detected)
            val faces = (result as FaceEngineResult.Detected).faces
            assertEquals(3, faces.size)
            assertTrue(faces[1] is FaceObservation.Unavailable)
            assertEquals(2, faces.count { it is FaceObservation.Encoded })
        }
    }

    @Test fun outOfMemoryIsGlobalAndNeverPublishedAsAPartialList() = runBlocking {
        val kernel = Kernel(memoryFailure = true)
        VerifiedFaceEngine { kernel }.use { engine ->
            try {
                engine.analyze(frame)
                fail("Memory failure must abort the whole batch")
            } catch (_: FaceEngineException) {
                assertEquals(2, kernel.encodings)
            }
        }
    }

    private class Kernel(
        val failureAt: Float? = null,
        val count: Int = 3,
        val invalidLandmarks: Boolean = false,
        val memoryFailure: Boolean = false,
        val nativeMemoryFailure: Boolean = false,
        val afterEncoding: () -> Unit = {}
    ) : FaceKernel {
        var detections = 0
        var encodings = 0
        var closed = 0
        override fun detect(
            frame: FaceRgbFrame,
            transform: FaceDetectorTransform
        ): List<List<Double>> {
            detections++
            if (count != 3) return List(count) { row(1.0 + it * 20, .9) }
            return listOf(row(20.0, .9), row(120.0, .95), row(220.0, .8)).mapIndexed {
                    index,
                    values
                ->
                if (invalidLandmarks &&
                    index == 0
                ) {
                    values.toMutableList().apply { this[4] = Double.NaN }
                } else {
                    values
                }
            }
        }
        override fun encode(frame: FaceRgbFrame, detection: FaceDetection): FaceEncoding {
            encodings++
            if (nativeMemoryFailure && detection.bounds.left == 20f) {
                throw CvException(
                    "OpenCV(4.13.0) error: (-4:Insufficient memory) public test allocation"
                )
            }
            if (memoryFailure && detection.bounds.left == 20f) throw OutOfMemoryError()
            if (detection.bounds.left == failureAt) error("Unusable face alignment")
            afterEncoding()
            val axis = detection.bounds.left.toInt() / 100
            return FaceEncoding(
                ByteArray(112 * 112 * 3),
                List(512) {
                    if (it ==
                        axis
                    ) {
                        2.0
                    } else {
                        0.0
                    }
                }
            )
        }
        override fun close() {
            closed++
        }
        private fun row(left: Double, confidence: Double) = listOf(
            left, 20.0, 40.0, 40.0,
            left + 5, 30.0, left + 25, 30.0, left + 15, 40.0,
            left + 5, 50.0, left + 25, 50.0, confidence
        )
    }
}
