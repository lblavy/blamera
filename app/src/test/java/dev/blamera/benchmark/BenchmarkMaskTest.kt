package dev.blamera.benchmark

import dev.blamera.vision.Letterbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkMaskTest {
    @Test fun realLogitsCreateCroppedMsbMasksAndContoursWithoutDroppingEmptyBoxes() {
        val rows = FloatArray(20 * 38)
        floatArrayOf(0f, 160f, 640f, 480f, .9f, 1f, 1f).copyInto(rows)
        floatArrayOf(0f, 160f, 640f, 480f, .8f, 0f, -1f).copyInto(rows, 38)
        val proto = FloatArray(32 * 160 * 160)
        java.util.Arrays.fill(proto, 0, 160 * 160, 1f)
        val result = compactDetections(rows, proto, Letterbox(400, 200), listOf("cat", "dog"), true)
        assertEquals(2, result.size)
        assertEquals("dog", result[0].nameEnglish)
        assertEquals(12800, result[0].activePixels)
        assertEquals(3200, result[0].mask.size)
        assertEquals(0, result[0].mask[0].toInt())
        assertEquals(255, result[0].mask[800].toInt() and 255)
        assertEquals(400f, result[0].box.right)
        assertEquals(200f, result[0].box.bottom)
        assertTrue(result[0].segments > 0)
        assertEquals(0, result[1].activePixels)
        assertEquals(0, result[1].segments)
        val perf = compactDetections(rows, proto, Letterbox(400, 200), listOf("cat", "dog"), false)
        assertTrue(perf.all { it.mask.isEmpty() })
        assertEquals(result.map { it.segments }, perf.map { it.segments })
    }
}
