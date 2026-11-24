package dev.blamera.ui

import dev.blamera.vision.ImageBox
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.Letterbox
import dev.blamera.vision.MaskGrid
import dev.blamera.vision.VisionDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageExplorationTest {
    @Test fun zoomKeepsTheTouchedImagePointAndPanIsBounded() {
        val moved = ImageExploration().move(
            ImageSpace(400, 200, 400f, 400f),
            ImagePoint(100f, 200f),
            ImagePoint(0f, 0f),
            2f
        )
        val viewport = moved.viewport(400, 200, 400f, 400f)
        assertEquals(2f, viewport.scale, 0.001f)
        assertEquals(100f, viewport.point(ImagePoint(100f, 100f)).x, 0.001f)
        assertEquals(200f, viewport.point(ImagePoint(100f, 100f)).y, 0.001f)
        val bounded = moved.move(
            ImageSpace(400, 200, 400f, 400f),
            ImagePoint(200f, 200f),
            ImagePoint(9999f, 9999f),
            1f
        ).viewport(400, 200, 400f, 400f)
        assertEquals(0f, bounded.left, 0.001f)
        assertEquals(0f, bounded.top, 0.001f)
    }

    @Test fun hitUsesMaskAndLetterboxRatherThanTheBox() {
        val mask = MaskGrid(4, 4, BooleanArray(16) { it == 5 })
        val large = VisionDetection(1, 0.9f, ImageBox(0f, 0f, 400f, 200f), mask, emptyList())
        val small = large.copy(box = ImageBox(100f, 0f, 200f, 100f))
        val detections = listOf(large, small)
        assertEquals(
            listOf(1, 0),
            objectsAt(detections, ImagePoint(110f, 10f), Letterbox(400, 200))
        )
        assertEquals(
            emptyList<Int>(),
            objectsAt(detections, ImagePoint(300f, 100f), Letterbox(400, 200))
        )
        assertEquals(
            emptyList<Int>(),
            objectsAt(detections, ImagePoint(-1f, 0f), Letterbox(400, 200))
        )
        assertNull(toggleObject(0, listOf(1, 0)))
        assertEquals(1, toggleObject(null, listOf(1, 0)))
        assertNull(toggleObject(1, emptyList()))
    }

    @Test fun zoomLimitsKeepTheImageReachableAndZoomingOutRecentersIt() {
        val space = ImageSpace(400, 200, 400f, 500f)
        val magnified = ImageExploration().move(
            space,
            ImagePoint(200f, 250f),
            ImagePoint(-9999f, -9999f),
            100f
        )
        assertEquals(6f, magnified.zoom, 0.001f)
        val edge = magnified.viewport(400, 200, 400f, 500f)
        assertEquals(-2000f, edge.left, 0.001f)
        assertEquals(-700f, edge.top, 0.001f)
        val restored = magnified.move(
            space,
            ImagePoint(100f, 300f),
            ImagePoint(9999f, 9999f),
            0.001f
        )
        val fit = restored.viewport(400, 200, 400f, 500f)
        assertEquals(1f, restored.zoom, 0.001f)
        assertEquals(0f, fit.left, 0.001f)
        assertEquals(150f, fit.top, 0.001f)
    }
}
