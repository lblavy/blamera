package dev.blamera.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostprocessorTest {
    @Test
    fun nonSquareImageHasClippedSourceBoxAndMaskBoundaries() {
        val raw = output()
        candidate(raw, 0, 0, 0.9f)
        val result = YoloPostprocessor().decode(raw, Letterbox(800, 400))
        assertEquals(1, result.size)
        assertEquals(ImageBox(300f, 100f, 500f, 300f), result[0].box)
        assertEquals(0, result[0].classId)
        assertTrue(result[0].mask.active.any { it })
        assertTrue(result[0].contours.any { it.start == ImagePoint(300f, 100f) })
    }

    @Test
    fun suppressionIsClassAwareAndKeepsHighestScore() {
        val raw = output()
        candidate(raw, 0, 0, 0.9f)
        candidate(raw, 1, 0, 0.8f)
        candidate(raw, 2, 15, 0.7f)
        val result = YoloPostprocessor().decode(raw, Letterbox(640, 640))
        assertEquals(listOf(0, 15), result.map { it.classId })
        assertEquals(listOf(0.9f, 0.7f), result.map { it.score })
    }

    @Test
    fun dominantCatAndDogChannelsRouteToTheirOwnFrenchLabels() {
        val raw = output()
        candidate(raw, 0, 15, 0.9f)
        candidate(raw, 1, 16, 0.8f)
        val result = YoloPostprocessor().decode(raw, Letterbox(640, 640))
        assertEquals(listOf(15, 16), result.map { it.classId })
        assertEquals(listOf("Chat", "Chien"), result.map { categoryLabel(it.classId) })
    }

    @Test
    fun emptyBelowThresholdAndOutsideImageProduceNoObjects() {
        val raw = output()
        assertTrue(YoloPostprocessor().decode(raw, Letterbox(640, 640)).isEmpty())
        candidate(raw, 0, 0, 0.34f)
        assertTrue(YoloPostprocessor().decode(raw, Letterbox(640, 640)).isEmpty())
        candidate(raw, 0, 0, 0.9f)
        raw.predictions[8400] = 40f
        raw.predictions[3 * 8400] = 20f
        assertTrue(YoloPostprocessor().decode(raw, Letterbox(800, 400)).isEmpty())
    }

    @Test
    fun invalidShapesAndNonFiniteValuesAreRejected() {
        val raw = output()
        assertThrows(VisionException::class.java) {
            YoloPostprocessor().decode(
                raw.copy(predictionShape = longArrayOf(1, 115, 8400)),
                Letterbox(640, 640)
            )
        }
        raw.predictions[0] = Float.NaN
        assertThrows(VisionException::class.java) {
            YoloPostprocessor().decode(raw, Letterbox(640, 640))
        }
        raw.predictions[0] = 0f
        raw.prototypes[0] = Float.POSITIVE_INFINITY
        assertThrows(VisionException::class.java) {
            YoloPostprocessor().decode(raw, Letterbox(640, 640))
        }
    }

    @Test
    fun contoursFollowConcavityRatherThanBoundingRectangleAndAreBounded() {
        val mask = MaskGrid(2, 2, booleanArrayOf(true, true, true, false))
        val logits = LogitGrid(2, 2, floatArrayOf(1f, 1f, 1f, -1f))
        val contours = traceIsocontour(logits, Letterbox(640, 640))
        assertTrue(contours.any { it.start.x != it.end.x && it.start.y != it.end.y })
        assertTrue(
            contours.any {
                it.start == ImagePoint(480f, 320f) ||
                    it.end == ImagePoint(480f, 320f)
            }
        )
        assertThrows(VisionException::class.java) {
            traceIsocontour(logits, Letterbox(640, 640), maxSegments = 4)
        }
        assertFalse(mask.contains(-1, 0))
    }

    @Test
    fun candidatesAndDetectionsAreBoundedWithoutDroppingTheHighestScore() {
        val raw = output()
        for (anchor in 0..320) {
            candidate(raw, anchor, 0, if (anchor == 310) 0.99f else 0.5f)
            raw.predictions[anchor] = 12f + (anchor % 25) * 24
            raw.predictions[8400 + anchor] = 12f + (anchor / 25) * 24
            raw.predictions[2 * 8400 + anchor] = 8f
            raw.predictions[3 * 8400 + anchor] = 8f
        }
        val detections = YoloPostprocessor().decode(raw, Letterbox(640, 640))
        assertEquals(20, detections.size)
        assertEquals(0.99f, detections.first().score, 0f)
    }

    @Test
    fun malformedLengthsAndPrototypeShapeFailAndEmptyMasksAreOmitted() {
        val raw = output()
        val processor = YoloPostprocessor()
        val projection = Letterbox(640, 640)
        listOf(
            raw.copy(predictions = FloatArray(1)),
            raw.copy(prototypes = FloatArray(1)),
            raw.copy(prototypeShape = longArrayOf(1))
        ).forEach {
            assertThrows(VisionException::class.java) { processor.decode(it, projection) }
        }
        candidate(raw, 0, 0, 0.9f)
        raw.prototypes.fill(-1f)
        assertTrue(processor.decode(raw, projection).isEmpty())
    }

    private fun output() = RawSegmentation(
        FloatArray(116 * 8400),
        FloatArray(32 * 160 * 160) {
            1f
        }
    )

    private fun candidate(raw: RawSegmentation, anchor: Int, type: Int, score: Float) {
        raw.predictions[anchor] = 320f
        raw.predictions[8400 + anchor] = 320f
        raw.predictions[2 * 8400 + anchor] = 160f
        raw.predictions[3 * 8400 + anchor] = 160f
        raw.predictions[(4 + type) * 8400 + anchor] = score
        raw.predictions[84 * 8400 + anchor] = 1f
    }
}
