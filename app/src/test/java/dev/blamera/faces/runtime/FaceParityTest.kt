package dev.blamera.faces.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FaceParityTest {
    private val row =
        listOf(
            20.0, 20.0, 40.0, 40.0,
            25.0, 30.0, 45.0, 30.0, 35.0, 40.0, 25.0, 50.0, 45.0, 50.0, .95
        )
    private val frame = FaceRgbFrame(640, 640, ByteArray(640 * 640 * 3))
    private val pixels = ByteArray(112 * 112 * 3)
    private val vector = List(512) { if (it == 0) 1.0 else 0.0 }
    private val expected = FaceParityExpected.Single(
        listOf(20.0, 20.0, 60.0, 60.0),
        row.subList(4, 14),
        pixels,
        vector
    )

    @Test fun exactPublicResultsAndUnsignedPixelValuesQualify() {
        val report = verifyFaceParity(kernel(), listOf(FaceParityCase(frame, expected)))
        assertEquals(1.0, report.embeddingCosineMin, 0.0)
        assertEquals(0.0, report.prototypeScoreDelta, 0.0)
        assertTrue(report.decisionAgreement)
        val white = pixels.map { 255.toByte() }.toByteArray()
        val whiteReference = expected.copy(alignedRgb = white)
        assertEquals(
            0.0,
            verifyFaceParity(
                kernel(pixels = white),
                listOf(FaceParityCase(frame, whiteReference))
            ).alignedMae,
            0.0
        )
    }

    @Test fun boundaryAndLandmarkErrorsCannotBeHiddenByAnAccurateEmbedding() {
        mismatch(expected.copy(bounds = listOf(22.0, 20.0, 60.0, 60.0))) {
            assertEquals(2.0, it.boundsError, 0.0)
        }
        mismatch(
            expected.copy(
                landmarks = expected.landmarks.toMutableList().apply {
                    this[0] += 2
                }
            )
        ) { assertEquals(2.0, it.landmarksError, 0.0) }
    }

    @Test fun alignedPixelAndEmbeddingDriftFailTheFrozenLimits() {
        mismatch(
            expected.copy(
                alignedRgb = ByteArray(pixels.size) {
                    2
                }
            )
        ) { assertEquals(2.0, it.alignedMae, 0.0) }
        mismatch(expected.copy(embedding = List(512) { if (it == 1) 1.0 else 0.0 })) {
            assertEquals(0.0, it.embeddingCosineMin, 0.0)
            assertEquals(1.0, it.prototypeScoreDelta, 0.0)
            assertTrue(!it.decisionAgreement)
        }
    }

    @Test fun emptyCasesWrongKindsAndMalformedReferencesNeverQualify() {
        invalid { verifyFaceParity(kernel(), emptyList()) }
        countMismatch {
            verifyFaceParity(kernel(), listOf(FaceParityCase(frame, FaceParityExpected.None)))
        }
        countMismatch {
            verifyFaceParity(
                kernel(),
                listOf(FaceParityCase(frame, FaceParityExpected.Multiple(emptyList())))
            )
        }
        invalid {
            verifyFaceParity(
                kernel(),
                listOf(FaceParityCase(frame, expected.copy(bounds = emptyList())))
            )
        }
        invalid {
            verifyFaceParity(
                kernel(),
                listOf(
                    FaceParityCase(
                        frame,
                        expected.copy(
                            bounds = List(4) {
                                Double.NaN
                            }
                        )
                    )
                )
            )
        }
        invalid {
            verifyFaceParity(
                kernel(),
                listOf(FaceParityCase(frame, expected.copy(alignedRgb = byteArrayOf())))
            )
        }
        invalid {
            verifyFaceParity(
                kernel(count = 0),
                listOf(FaceParityCase(frame, FaceParityExpected.None))
            )
        }
    }

    private fun kernel(count: Int = 1, pixels: ByteArray = this.pixels) = object : FaceKernel {
        override fun detect(frame: FaceRgbFrame, transform: FaceDetectorTransform) =
            List(count) { row }
        override fun encode(frame: FaceRgbFrame, detection: FaceDetection) =
            FaceEncoding(pixels, vector)
        override fun close() = Unit
    }

    private fun mismatch(reference: FaceParityExpected.Single, check: (FaceParityReport) -> Unit) {
        try {
            verifyFaceParity(kernel(), listOf(FaceParityCase(frame, reference)))
            fail("Numerical mismatch must disable recognition")
        } catch (failed: FaceParityFailure) {
            check(requireNotNull(failed.report))
        }
    }

    private fun countMismatch(block: () -> Unit) {
        try {
            block()
            fail("A public face count mismatch must be a deterministic parity failure")
        } catch (failed: FaceParityFailure) {
            assertEquals(null, failed.report)
        }
    }

    private fun invalid(block: () -> Unit) {
        try {
            block()
            fail("Invalid reference must be refused")
        } catch (_: IllegalArgumentException) {
            // No report can claim qualification from malformed references.
        }
    }
}
