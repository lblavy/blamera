package dev.blamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import dev.blamera.faces.FaceIdentityDecision
import dev.blamera.faces.FaceIdentityStatus
import dev.blamera.faces.FacePhotoFace
import dev.blamera.faces.runtime.FaceBounds
import dev.blamera.vision.ImageViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MultipleFaceLabelsTest {
    private val clip = FaceBounds(0f, 0f, 400f, 300f)
    private val viewport = ImageViewport(2f, 5f, 7f)
    private val faces = listOf(
        face(10f, "A"),
        face(45f, "B"),
        face(130f, null)
    )

    @Test fun zeroOneAndThreeLabelsKeepTheirOwnNamesAndProjectedAnchors() {
        val painter = FaceOverlayPainter()
        assertTrue(painter.placements(emptyList(), viewport, clip, 1f, 16f).isEmpty())
        assertEquals(1, painter.placements(faces.take(1), viewport, clip, 1f, 16f).size)
        val labels = painter.placements(faces, viewport, clip, 1f, 16f)
        assertEquals(listOf("A", "B", "Inconnu"), labels.map { it.text })
        assertEquals(
            faces.map {
                projectFaceBounds(it.bounds, viewport)
            },
            labels.map { it.faceBounds }
        )
        labels.forEachIndexed { index, first ->
            labels.drop(index + 1).forEach { second ->
                assertFalse(
                    "Adjacent labels must avoid obvious overlap",
                    RectF.intersects(first.bounds.rect(), second.bounds.rect())
                )
            }
        }
    }

    @Test fun canvasDrawsOnlyTheLabelAndNeverAddsAFacialBorder() {
        val image = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        val painter = FaceOverlayPainter()
        val one = listOf(face(10f, "A"))
        val label = painter.placements(one, viewport, clip, 1f, 16f).single()
        painter.drawFaces(Canvas(image), one, viewport, 1f, 16f)
        assertNotEquals(
            Color.WHITE,
            image.getPixel(
                (label.bounds.left + label.bounds.right).toInt() / 2,
                (label.bounds.top + label.bounds.bottom).toInt() / 2
            )
        )
        assertEquals("The old facial border must disappear", Color.WHITE, image.getPixel(25, 150))
        assertEquals(Color.WHITE, image.getPixel(105, 150))
        image.recycle()
    }

    @Test fun visibleLabelsStayInsideTheImageAndOffscreenFacesHaveNoFloatingLabel() {
        val painter = FaceOverlayPainter()
        val image = FaceBounds(0f, 0f, 180f, 100f)
        val closeFaces = listOf(face(0f, "Known near edge"), face(1f, "Other"))
        val labels = painter.placements(closeFaces, ImageViewport(1f, 0f, -35f), image, 1f, 16f)
        assertEquals(2, labels.size)
        labels.forEach {
            assertTrue(
                it.bounds.left >= 0 && it.bounds.right <= 180 && it.bounds.top >= 0 &&
                    it.bounds.bottom <= 100
            )
        }
        assertFalse(RectF.intersects(labels[0].bounds.rect(), labels[1].bounds.rect()))
        assertTrue(
            painter.placements(faces, ImageViewport(2f, -1000f, 0f), clip, 1f, 16f).isEmpty()
        )
        val unavailable = FacePhotoFace(FaceBounds(10f, 20f, 40f, 60f), null)
        assertEquals(
            "Visage non analysé",
            painter.placements(listOf(unavailable), viewport, clip, 1f, 16f).single().text
        )
    }

    private fun face(left: Float, name: String?) = FacePhotoFace(
        FaceBounds(left, 40f, left + 40f, 90f),
        FaceIdentityDecision(
            if (name ==
                null
            ) {
                FaceIdentityStatus.UNKNOWN
            } else {
                FaceIdentityStatus.RECOGNIZED
            },
            name,
            if (name ==
                null
            ) {
                .1
            } else {
                .9
            },
            null
        )
    )
    private fun FaceBounds.rect() = RectF(left, top, right, bottom)
}
