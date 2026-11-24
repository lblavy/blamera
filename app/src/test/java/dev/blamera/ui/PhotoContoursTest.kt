package dev.blamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoContoursTest {
    @Test fun repeatedViewportUpdatesReuseTheSourcePathsAndANewResultBuildsNewGeometry() {
        val detections = listOf(captureDetection())
        val contours = PhotoContours(detections)
        val initial = contours.paths
        val sourceBounds = initial.single().sourcePath.getBounds()
        val target = Bitmap.createBitmap(800, 360, Bitmap.Config.ARGB_8888)
        listOf(ImageExploration(), ImageExploration(2f, 50f, -20f)).forEach { exploration ->
            val viewport = exploration.viewport(640, 480, 800f, 360f)
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                Canvas(target.asImageBitmap()),
                Size(800f, 360f)
            ) { drawPhotoContours(contours, 0, viewport) }
            assertSame(initial, contours.paths)
            assertSame(initial.single().sourcePath, contours.paths.single().sourcePath)
            assertEquals(sourceBounds, contours.paths.single().sourcePath.getBounds())
        }
        org.junit.Assert.assertTrue(android.graphics.Color.alpha(target.getPixel(280, 90)) > 0)
        assertNotSame(
            initial.single().sourcePath,
            PhotoContours(detections).paths.single().sourcePath
        )
    }
}
