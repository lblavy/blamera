package dev.blamera.information

import android.graphics.Bitmap
import dev.blamera.image.ImageCrop
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
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
class SearchImageBoundaryTest {
    @Test
    fun aByteWiseEncoderPreservesTheExactJpegAndStopsAtTheNextByteBeyondItsBudget() = runBlocking {
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val region = ImageCrop(0, 0, 20, 10)
        try {
            val encoded = ByteArrayOutputStream()
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 80, encoded))
            val jpeg = encoded.toByteArray()
            val byteWise = AndroidSearchImagePreparer { _, output ->
                jpeg.forEach { output.write(it.toInt() and 0xff) }
                true
            }.prepareRegion(source, region)
            try {
                assertArrayEquals(jpeg, byteWise.jpeg)
            } finally {
                byteWise.close()
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    AndroidSearchImagePreparer { _, output ->
                        repeat(MAX_JPEG_BYTES) { output.write(0) }
                        output.write(1)
                        true
                    }.prepareRegion(source, region)
                }
            }
            assertFalse(source.isRecycled)
        } finally {
            source.recycle()
        }
    }

    @Test
    fun successfulEncoderWithNonJpegBytesFailsAtDecodeWithoutDisposingTheSource() {
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    AndroidSearchImagePreparer { _, output ->
                        output.write(byteArrayOf(1, 2, 3))
                        true
                    }.prepareRegion(source, ImageCrop(0, 0, 20, 10))
                }
            }
            assertFalse(source.isRecycled)
        } finally {
            source.recycle()
        }
    }
}
