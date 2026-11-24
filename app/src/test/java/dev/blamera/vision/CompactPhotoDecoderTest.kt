package dev.blamera.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactPhotoDecoderTest {
    @Test fun compactRowsKeepTheFullVocabularyAndReconstructCroppedMasks() {
        val raw = example()
        val detections = CompactPhotoDecoder().decode(raw, Letterbox(640, 320))
        assertEquals(1, detections.size)
        val detection = detections.single()
        assertEquals(3307, detection.classId)
        assertEquals(.9f, detection.score)
        assertEquals(ImageBox(40f, 0f, 600f, 320f), detection.box)
        assertTrue(detection.mask.contains(80, 80))
        assertFalse(detection.mask.contains(1, 1))
        assertTrue(detection.contours.isNotEmpty())
        assertTrue(detection.contours.all { it.start.x in 0f..640f && it.end.y in 0f..320f })
    }

    @Test fun malformedOutputsAreRejectedInsteadOfProducingWrongCategoriesOrMasks() {
        val decoder = CompactPhotoDecoder()
        val projection = Letterbox(640, 320)
        val base = example()
        val invalid = listOf(
            base.copy(predictionShape = longArrayOf(1, 19, 38)),
            base.copy(prototypeShape = longArrayOf(1, 32, 159, 160)),
            base.copy(predictions = FloatArray(3)),
            base.copy(prototypes = FloatArray(3)),
            base.copy(prototypes = base.prototypes.copyOf().apply { this[0] = Float.NaN })
        )
        invalid.forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { decoder.decode(raw, projection) }
        }
        for ((column, value) in listOf(
            4 to -.1f,
            4 to 1.1f,
            5 to -1f,
            5 to 4585f,
            5 to 1.5f,
            2 to 0f,
            38 to 1f,
            0 to Float.NaN
        )) {
            val rows = base.predictions.copyOf().apply { this[column] = value }
            assertThrows(IllegalArgumentException::class.java) {
                decoder.decode(base.copy(predictions = rows), projection)
            }
        }
    }

    @Test fun emptyCroppedMasksAndPaddedRowsDoNotBecomeSelectableObjects() {
        val base = example()
        val empty = base.copy(prototypes = FloatArray(32 * 160 * 160))
        assertTrue(CompactPhotoDecoder().decode(empty, Letterbox(640, 320)).isEmpty())
        val padOnly = base.copy(predictions = FloatArray(20 * 38))
        assertTrue(CompactPhotoDecoder().decode(padOnly, Letterbox(640, 320)).isEmpty())
    }

    private fun example(): RawSegmentation {
        val rows = FloatArray(20 * 38)
        listOf(40f, 160f, 600f, 480f, .9f, 3307f, 1f).forEachIndexed { i, value -> rows[i] = value }
        val prototypes = FloatArray(32 * 160 * 160)
        prototypes.fill(1f, 0, 160 * 160)
        return RawSegmentation(
            rows,
            prototypes,
            longArrayOf(1, 20, 38),
            longArrayOf(1, 32, 160, 160)
        )
    }
}
