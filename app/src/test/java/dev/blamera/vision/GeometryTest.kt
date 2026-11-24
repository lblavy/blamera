package dev.blamera.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeometryTest {
    @Test
    fun nonSquareLetterboxAndInverseProjectionClipToImage() {
        val box = Letterbox(800, 400)
        assertEquals(0.8f, box.scale, 0.00001f)
        assertEquals(640, box.scaledWidth)
        assertEquals(320, box.scaledHeight)
        assertEquals(160, box.padTop)
        assertEquals(ImagePoint(100f, 50f), box.sourcePoint(80f, 200f))
        assertEquals(ImageBox(0f, 0f, 800f, 400f), box.sourceBox(ImageBox(-10f, 0f, 700f, 640f)))
        val portrait = Letterbox(400, 800)
        assertEquals(160, portrait.padLeft)
        assertEquals(ImagePoint(50f, 100f), portrait.sourcePoint(200f, 80f))
    }

    @Test
    fun fitProjectionUsesSamePaddingAsDisplayedImage() {
        val view = ImageViewport.fit(800, 400, 400f, 400f)
        assertEquals(ImagePoint(50f, 125f), view.point(ImagePoint(100f, 50f)))
        val portrait = ImageViewport.fit(400, 800, 400f, 400f)
        assertEquals(ImagePoint(125f, 50f), portrait.point(ImagePoint(50f, 100f)))
        val wideWindow = ImageViewport.fit(800, 400, 800f, 320f)
        assertEquals(ImagePoint(80f, 0f), wideWindow.point(ImagePoint(0f, 0f)))
        assertEquals(ImagePoint(720f, 320f), wideWindow.point(ImagePoint(800f, 400f)))
        val tallWindow = ImageViewport.fit(800, 400, 320f, 800f)
        assertEquals(ImagePoint(0f, 320f), tallWindow.point(ImagePoint(0f, 0f)))
        assertEquals(ImagePoint(320f, 480f), tallWindow.point(ImagePoint(800f, 400f)))
    }

    @Test
    fun boxOverlapAndDisjointAreMeasured() {
        val box = ImageBox(0f, 0f, 10f, 10f)
        assertEquals(1f, box.iou(box), 0.00001f)
        assertEquals(0f, box.iou(ImageBox(20f, 20f, 30f, 30f)), 0.00001f)
        assertEquals(0f, ImageBox(0f, 0f, 0f, 0f).iou(ImageBox(0f, 0f, 0f, 0f)), 0f)
        assertEquals(1f / 3f, box.iou(ImageBox(5f, 0f, 15f, 10f)), 0.00001f)
    }

    @Test
    fun invalidInputsAreRejectedAndRgbIsPlanarWith114Padding() {
        assertThrows(IllegalArgumentException::class.java) { prepareImage(intArrayOf(), 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { Letterbox(0, 1) }
        val input = prepareImage(intArrayOf(0xffff0000.toInt(), 0xffff0000.toInt()), 2, 1)
        val plane = 640 * 640
        assertEquals(114f / 255f, input.values[0], 0.00001f)
        assertEquals(1f, input.values[320 * 640 + 320], 0.00001f)
        assertEquals(0f, input.values[plane + 320 * 640 + 320], 0.00001f)
        assertEquals(0f, input.values[2 * plane + 320 * 640 + 320], 0.00001f)
    }
}
