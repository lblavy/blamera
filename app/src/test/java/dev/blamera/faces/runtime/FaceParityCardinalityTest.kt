package dev.blamera.faces.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FaceParityCardinalityTest {
    private val frame = FaceRgbFrame(640, 640, ByteArray(640 * 640 * 3))
    private val row = listOf(
        20.0, 20.0, 40.0, 40.0,
        25.0, 30.0, 45.0, 30.0, 35.0, 40.0, 25.0, 50.0, 45.0, 50.0, .95
    )

    @Test fun wrongFaceCountDisablesLaterAnalysesWithoutReloadingTheKernel() = runBlocking {
        listOf(
            1 to FaceParityExpected.None,
            1 to FaceParityExpected.Multiple(emptyList()),
            0 to FaceParityExpected.Single(
                listOf(20.0, 20.0, 60.0, 60.0),
                row.subList(4, 14),
                ByteArray(112 * 112 * 3),
                List(512) { if (it == 0) 1.0 else 0.0 }
            )
        ).forEach { (count, expected) ->
            var loaded = 0
            var checked = 0
            var closed = 0
            val qualification = FaceQualification()
            val engine = VerifiedFaceEngine {
                qualification.openKernel(open = {
                    loaded++
                    object : FaceKernel {
                        override fun detect(
                            frame: FaceRgbFrame,
                            transform: FaceDetectorTransform
                        ): List<List<Double>> {
                            checked++
                            return List(count) { row }
                        }
                        override fun encode(
                            frame: FaceRgbFrame,
                            detection: FaceDetection
                        ): FaceEncoding =
                            error("An incompatible detection must never reach encoding")
                        override fun close() {
                            closed++
                        }
                    }
                }) { kernel -> verifyFaceParity(kernel, listOf(FaceParityCase(frame, expected))) }
            }
            repeat(2) {
                try {
                    engine.analyze(frame)
                    fail("A deterministic public count mismatch must disable inference")
                } catch (_: FaceEngineException) {
                    // The two user-visible attempts both fail with the typed error.
                }
            }
            assertEquals("A failed profile must not reload a native kernel", 1, loaded)
            assertEquals("The deterministic mismatch must not be retried", 1, checked)
            assertEquals("The rejected kernel must release its ownership exactly once", 1, closed)
            engine.close()
            assertEquals(1, closed)
        }
    }
}
