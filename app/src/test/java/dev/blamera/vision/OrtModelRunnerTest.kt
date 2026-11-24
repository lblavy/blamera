package dev.blamera.vision

import android.graphics.BitmapFactory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrtModelRunnerTest {
    @Test
    fun officialModelSegmentsPublicNasaPersonOnCpu() {
        val fixture = checkNotNull(System.getProperty("blamera.fixture"))
        val image = checkNotNull(BitmapFactory.decodeFile(fixture))
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val input = prepareImage(pixels, image.width, image.height)
        val output = OrtModelRunner({
            File(System.getProperty("blamera.model")).readBytes()
        }, HASH).run(input.values)
        assertTrue(output.loadMillis >= 0 && output.inferenceMillis >= 0)
        val detections = YoloPostprocessor().decode(output.raw, input.letterbox)
        val person = detections.single { it.classId == 0 }
        assertEquals(0.8841f, person.score, 0.03f)
        assertEquals(13.37f, person.box.left, 3f)
        assertEquals(510.8f, person.box.bottom, 3f)
        assertTrue(person.mask.active.count { it } > 9000)
        assertTrue(person.contours.size > 100)
        assertTrue(person.contours.all { it.start.x in 0f..512f && it.end.y in 0f..512f })
        val reference = File(File(fixture).parentFile, "astronaut-pytorch-mask.bin").readBytes()
        assertEquals(640 * 640, reference.size)
        var intersection = 0
        var union = 0
        for (index in reference.indices) {
            val actual = person.mask.contains((index % 640) / 4, (index / 640) / 4)
            val expected = reference[index].toInt() != 0
            if (actual && expected) intersection++
            if (actual || expected) union++
        }
        val iou = intersection.toFloat() / union
        assertTrue("Quantized contour mask IoU=$iou", iou >= 0.95f)
        println("NASA person score=${person.score}, maskIoU=$iou, segments=${person.contours.size}")
        saveContourComparison("nasa", cellEdges(person.mask, input.letterbox), person.contours)
    }

    @Test
    fun modelMismatchFailsBeforeOpeningNativeSession() {
        val error = assertThrows(VisionException::class.java) {
            OrtModelRunner({ byteArrayOf(1, 2) }, HASH).run(FloatArray(3 * 640 * 640))
        }
        assertTrue(error.message.orEmpty().contains("SHA-256"))
        assertThrows(IllegalArgumentException::class.java) {
            OrtModelRunner({ error("Must not read") }, HASH).run(FloatArray(1))
        }
    }

    companion object {
        const val HASH = "38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b"
    }
}
