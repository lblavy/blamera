package dev.blamera.faces.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FaceGeometryBoundaryTest {
    private val frame = FaceRgbFrame(640, 640, ByteArray(640 * 640 * 3))
    private val valid =
        listOf(
            20.0, 20.0, 40.0, 40.0,
            25.0, 30.0, 45.0, 30.0, 35.0, 40.0, 25.0, 50.0, 45.0, 50.0, .95
        )

    @Test fun sourceCoordinatesAndConfidenceMustBeFiniteAndInsideTheFrame() {
        assertTrue(validFaceDetection(FaceDetection(valid), frame))
        assertFalse(validFaceDetection(FaceDetection(emptyList()), frame))
        listOf(
            0 to -1.0, 1 to -1.0, 2 to 0.0, 3 to 0.0,
            2 to 1000.0, 3 to 1000.0, 14 to -0.1, 14 to 1.1,
            4 to -1.0, 5 to -1.0, 4 to 641.0, 5 to 641.0, 0 to Double.NaN
        ).forEach { (index, value) ->
            val invalid = valid.toMutableList().apply { this[index] = value }
            assertFalse(
                "Invalid native coordinate $index must be rejected",
                validFaceDetection(FaceDetection(invalid), frame)
            )
        }
        val collapsed = valid.toMutableList().apply { for (index in 4..13) this[index] = 30.0 }
        assertFalse(validFaceDetection(FaceDetection(collapsed), frame))
    }

    @Test fun detectorResultsAreClippedSortedAndBoundedBeforeEncoding() {
        val rows = List(25) { index -> valid.toMutableList().apply { this[14] = index / 25.0 } }
        val found = remapFaceCandidates(rows, frame, detectorTransform(frame))
        assertEquals(25, found.size)
        invalid { remapFaceRows(rows, frame, detectorTransform(frame)) }
        assertTrue(
            found.zipWithNext().all { (left, right) ->
                left.values.last() >=
                    right.values.last()
            }
        )
        val clipped = valid.toMutableList().apply {
            this[0] = -10.0
            this[1] = -5.0
        }
        assertEquals(
            FaceBounds(0f, 0f, 30f, 35f),
            remapFaceRows(listOf(clipped), frame, detectorTransform(frame)).single().bounds
        )
        invalid { remapFaceRows(List(5001) { valid }, frame, detectorTransform(frame)) }
    }

    @Test fun invalidFrameDimensionsAndOverflowingNormsAreRejectedBeforeNativeCalls() {
        listOf(0 to 1, 1 to 0, 2049 to 1, 1 to 2049).forEach { (width, height) ->
            invalid { validateFaceFrame(FaceRgbFrame(width, height, byteArrayOf())) }
        }
        invalid { normalizedFaceEmbedding(List(512) { Double.POSITIVE_INFINITY }) }
        invalid { normalizedFaceEmbedding(List(512) { Double.MAX_VALUE }) }
        assertEquals(
            1.0,
            normalizedFaceEmbedding(
                List(512) {
                    if (it ==
                        0
                    ) {
                        Double.MIN_VALUE
                    } else {
                        0.0
                    }
                }
            )[0],
            0.0
        )
    }

    private fun invalid(block: () -> Unit) {
        try {
            block()
            fail("Invalid geometry cannot reach the recognizer")
        } catch (_: IllegalArgumentException) {
            // Explicit numeric boundary.
        }
    }
}
