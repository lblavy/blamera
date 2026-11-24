package dev.blamera.information

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import dev.blamera.vision.ImageBox
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
class SearchImageTest {
    @Test fun cropIncludesOnlyBoundedFivePercentContextFromCurrentPixels() = runBlocking {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(90f, 35f, 310f, 145f, Paint().apply { color = Color.BLUE })
        }
        val box = ImageBox(100f, 40f, 300f, 140f)
        assertEquals(Rect(90, 35, 310, 145), searchCrop(source, box))
        val callerThread = Thread.currentThread()
        val encoderThread = AtomicReference<Thread>()
        val preparer = AndroidSearchImagePreparer { bitmap, output ->
            encoderThread.set(Thread.currentThread())
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        }
        val image = preparer.prepare(source, box)
        try {
            assertNotSame(callerThread, encoderThread.get())
            assertNotSame(source, image.preview)
            assertEquals(220, image.preview.width)
            assertEquals(110, image.preview.height)
            assertTrue(Color.blue(image.preview.getPixel(100, 50)) > 240)
            assertTrue(Color.red(image.preview.getPixel(100, 50)) < 10)
            val sameBytes = BitmapFactory.decodeByteArray(image.jpeg, 0, image.jpeg.size)
            val expected = IntArray(220 * 110)
            val shown = IntArray(expected.size)
            sameBytes.getPixels(expected, 0, 220, 0, 0, 220, 110)
            image.preview.getPixels(shown, 0, 220, 0, 0, 220, 110)
            assertArrayEquals(expected, shown)
            sameBytes.recycle()
            assertFalse(image.jpeg.toString(Charsets.ISO_8859_1).contains("Exif"))
            assertTrue(image.jpeg.size <= MAX_JPEG_BYTES)
        } finally {
            image.close()
        }
        assertFalse(source.isRecycled)
        assertTrue(image.preview.isRecycled)
    }

    @Test fun longImagesAreDownscaledWithoutUpscalingOrEscapingTheSource() = runBlocking {
        val source = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        val image = AndroidSearchImagePreparer().prepare(source, ImageBox(-20f, -10f, 2420f, 1210f))
        assertEquals(1000, image.preview.width)
        assertEquals(500, image.preview.height)
        image.close()
        assertEquals(Rect(0, 0, 2400, 1200), searchCrop(source, ImageBox(0f, 0f, 2400f, 1200f)))
    }

    @Test fun invalidBoxesRecycledSourcesAndEncodingFailuresAreRejected() {
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val box = ImageBox(0f, 0f, 20f, 10f)
        listOf(
            ImageBox(Float.NaN, 0f, 1f, 1f),
            ImageBox(1f, 0f, 1f, 1f),
            ImageBox(50f, 50f, 60f, 60f),
            ImageBox(0f, 5f, 1f, 4f)
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { searchCrop(source, invalid) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { AndroidSearchImagePreparer { _, _ -> false }.prepare(source, box) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                AndroidSearchImagePreparer { _, output ->
                    output.write(ByteArray(MAX_JPEG_BYTES + 1))
                    true
                }.prepare(source, box)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AndroidSearchImagePreparer { _, _ -> true }.prepare(source, box) }
        }
        source.recycle()
        assertThrows(IllegalArgumentException::class.java) { searchCrop(source, box) }
    }
}
