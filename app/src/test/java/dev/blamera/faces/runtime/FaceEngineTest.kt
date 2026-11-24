package dev.blamera.faces.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FaceEngineTest {
    @Test fun letterboxUsesIndependentActualScalesAndPythonTiesToEven() {
        val frame = frame(1024, 513)
        val transform = detectorTransform(frame)
        assertEquals(640, transform.resizedWidth)
        assertEquals(321, transform.resizedHeight)
        assertEquals(0, transform.offsetX)
        assertEquals(159, transform.offsetY)
        assertEquals(640.0 / 1024, transform.scaleX, 0.0)
        assertEquals(321.0 / 513, transform.scaleY, 0.0)
        val tie = detectorTransform(frame(256, 129))
        assertEquals(322, tie.resizedHeight)
    }

    @Test fun remappingClipsBoundsAndRejectsInvalidLandmarksWithoutChoosingAnotherFace() {
        val frame = frame(100, 50)
        val transform = detectorTransform(frame)
        val raw = listOf(
            128.0, 224.0, 256.0, 192.0,
            192.0, 288.0, 320.0, 288.0, 256.0, 320.0,
            192.0, 352.0, 320.0, 352.0, 0.95
        )
        val mapped = remapFaceRows(listOf(raw), frame, transform).single()
        assertEquals(FaceBounds(20f, 10f, 60f, 40f), mapped.bounds)
        assertEquals(30.0, mapped.values[4], 0.0)
        assertEquals(20.0, mapped.values[5], 0.0)
        assertTrue(
            remapFaceRows(
                listOf(
                    raw.toMutableList().apply {
                        this[4] = -1.0
                    }
                ),
                frame,
                transform
            ).isEmpty()
        )
        expectFailure { remapFaceRows(listOf(listOf(1.0)), frame, transform) }
    }

    @Test fun embeddingsAreFiniteUnitVectorsOfExactly512Dimensions() {
        val input = List(512) { if (it < 2) 3.0 else 0.0 }
        val actual = normalizedFaceEmbedding(input)
        assertEquals(1.0, actual.sumOf { it * it }, 1e-12)
        expectFailure { normalizedFaceEmbedding(List(512) { 0.0 }) }
        expectFailure { normalizedFaceEmbedding(listOf(1.0)) }
        expectFailure {
            normalizedFaceEmbedding(input.toMutableList().apply { this[0] = Double.NaN })
        }
        expectFailure { detectorTransform(frame(1, 1).copy(pixels = byteArrayOf())) }
    }

    @Test fun engineIsLazyEncodesEveryFaceAndClosesOnce() = runBlocking {
        val kernel = FakeKernel()
        var opened = 0
        val engine = VerifiedFaceEngine {
            opened++
            kernel
        }
        assertEquals(0, opened)
        val input = frame(640, 640)
        val batch = engine.analyze(input) as FaceEngineResult.Detected
        val single = batch.faces.single() as FaceObservation.Encoded
        assertEquals(FaceBounds(20f, 20f, 60f, 60f), single.bounds)
        assertEquals(1.0, single.embedding[0], 0.0)
        kernel.count = 2
        assertEquals(2, (engine.analyze(input) as FaceEngineResult.Detected).faces.size)
        assertEquals(3, kernel.encoded)
        kernel.count = 0
        assertEquals(FaceEngineResult.NoFace, engine.analyze(input))
        assertEquals(1, opened)
        engine.close()
        engine.close()
        assertEquals(1, kernel.closed)
        try {
            engine.analyze(input)
            fail("A closed engine cannot reopen native resources")
        } catch (_: FaceEngineException) {
            // Expected public failure, without model details.
        }
    }

    private fun frame(width: Int, height: Int) =
        FaceRgbFrame(width, height, ByteArray(width * height * 3))

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Invalid face data must be rejected")
        } catch (_: IllegalArgumentException) {
            // Explicit validation boundary.
        }
    }

    private class FakeKernel : FaceKernel {
        var count = 1
        var encoded = 0
        var closed = 0
        override fun detect(frame: FaceRgbFrame, transform: FaceDetectorTransform) = List(count) {
            listOf(
                20.0, 20.0, 40.0, 40.0,
                25.0, 30.0, 45.0, 30.0, 35.0, 40.0, 25.0, 50.0, 45.0, 50.0, .95
            )
        }
        override fun encode(frame: FaceRgbFrame, detection: FaceDetection): FaceEncoding {
            encoded++
            return FaceEncoding(ByteArray(112 * 112 * 3), List(512) { if (it == 0) 2.0 else 0.0 })
        }
        override fun close() {
            closed++
        }
    }
}
