package dev.blamera.information

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.blamera.image.ImageCrop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameSearchImageTest {
    @Test fun exactFrameHasNoObjectMarginAndClosingFullFrameNeverRecyclesTheSource() = runBlocking {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(100f, 50f, 300f, 150f, Paint().apply { color = Color.BLUE })
        }
        val preparer = AndroidSearchImagePreparer()
        val image = preparer.prepareRegion(source, ImageCrop(100, 50, 300, 150))
        assertEquals(200, image.preview.width)
        assertEquals(100, image.preview.height)
        assertTrue(Color.blue(image.preview.getPixel(3, 3)) > 220)
        image.close()
        val full = preparer.prepareRegion(source, ImageCrop(0, 0, 400, 200))
        full.close()
        assertFalse(source.isRecycled)
    }

    @Test fun aLegacySamUsesTheExactFrameFallbackWithoutCallingItsObjectCropImplementation() =
        runBlocking {
            val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            Canvas(source).apply {
                drawColor(Color.RED)
                drawRect(100f, 50f, 300f, 150f, Paint().apply { color = Color.BLUE })
            }
            val preparer =
                SearchImagePreparer { _, _ ->
                    error("Object preparation must not handle an exact frame")
                }
            val frame = preparer.prepareRegion(source, ImageCrop(100, 50, 300, 150))
            assertEquals(200, frame.preview.width)
            assertEquals(100, frame.preview.height)
            assertTrue(Color.blue(frame.preview.getPixel(3, 3)) > 220)
            assertTrue(Color.red(frame.preview.getPixel(3, 3)) < 30)
            frame.close()
            assertFalse(source.isRecycled)
        }

    @Test fun invalidAndRecycledFrameSourcesFailBeforeEncodingWithoutAnyFallback() = runBlocking {
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        var encodes = 0
        val preparer =
            AndroidSearchImagePreparer { _, _ ->
                encodes++
                error("Invalid frame encoded")
            }
        for (crop in listOf(
            ImageCrop(0, -1, 40, 20),
            ImageCrop(0, 0, 40, 21),
            ImageCrop(0, 10, 40, 10)
        )) {
            try {
                preparer.prepareRegion(source, crop)
                org.junit.Assert.fail("Invalid region accepted")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, encodes)
                assertFalse(source.isRecycled)
            }
        }
        source.recycle()
        try {
            preparer.prepareRegion(source, ImageCrop(0, 0, 40, 20))
            org.junit.Assert.fail("Recycled source accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, encodes)
        }
    }

    @Test fun invalidPixelRegionsFailBeforeEncoding() = runBlocking {
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (crop in listOf(
            ImageCrop(-1, 0, 20, 10),
            ImageCrop(0, 0, 41, 20),
            ImageCrop(1, 1, 1, 10)
        )) {
            try {
                AndroidSearchImagePreparer().prepareRegion(source, crop)
                fail("Invalid region accepted")
            } catch (_: IllegalArgumentException) {
                assertFalse(source.isRecycled)
            }
        }
    }
}
