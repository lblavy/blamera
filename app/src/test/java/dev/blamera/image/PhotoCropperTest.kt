package dev.blamera.image

import android.graphics.Bitmap
import android.graphics.Color
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCropperTest {
    @Test fun aRegionCopiesOnlyItsSourcePixelsAndPreservesTheOriginal() = runBlocking {
        val bitmap = Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888)
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                bitmap.setPixel(
                    x,
                    y,
                    Color.rgb(x * 20, y * 30, 7)
                )
            }
        }
        val original = DecodedPhoto(bitmap, PreviewDimensions(10, 6))
        val result = AndroidPhotoCropper().crop(original, ImageCrop(2, 1, 8, 5))
        assertTrue(
            "A valid region must produce a decoded working image",
            result is PhotoLoadResult.Loaded
        )
        val cropped = (result as PhotoLoadResult.Loaded).photo
        assertNotSame(bitmap, cropped.bitmap)
        assertEquals(6, cropped.bitmap.width)
        assertEquals(4, cropped.bitmap.height)
        assertEquals(bitmap.getPixel(2, 1), cropped.bitmap.getPixel(0, 0))
        assertEquals(bitmap.getPixel(7, 4), cropped.bitmap.getPixel(5, 3))
        assertEquals(Color.rgb(0, 0, 7), bitmap.getPixel(0, 0))
        assertFalse(bitmap.isRecycled)
    }

    @Test fun cancellingNativeCopyRecyclesOnlyTheUnpublishedCrop() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val produced = AtomicReference<Bitmap>()
        val original =
            DecodedPhoto(
                Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888),
                PreviewDimensions(10, 6)
            )
        val cropper = AndroidPhotoCropper { bitmap, region ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Bitmap.createBitmap(
                bitmap,
                region.left,
                region.top,
                region.width,
                region.height
            ).also(produced::set)
        }
        val job = launch(Dispatchers.Default) { cropper.crop(original, ImageCrop(2, 1, 8, 5)) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertFalse(original.bitmap.isRecycled)
            release.countDown()
            withTimeout(5000) { job.join() }
            assertTrue(produced.get().isRecycled)
            assertFalse(original.bitmap.isRecycled)
        } finally {
            release.countDown()
            job.cancel()
        }
    }

    @Test fun invalidRegionsAndMemoryFailureKeepTheOriginal() = runBlocking {
        val photo =
            DecodedPhoto(
                Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888),
                PreviewDimensions(10, 6)
            )
        for (region in listOf(
            ImageCrop(-1, 0, 5, 5),
            ImageCrop(0, 0, 11, 5),
            ImageCrop(2, 2, 2, 3)
        )) {
            assertEquals(
                PhotoLoadResult.Failed(ImageImportError.UNREADABLE),
                AndroidPhotoCropper().crop(photo, region)
            )
        }
        val unavailable =
            AndroidPhotoCropper { _, _ -> throw OutOfMemoryError("synthetic boundary failure") }
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.MEMORY),
            unavailable.crop(photo, ImageCrop(1, 1, 4, 4))
        )
        assertFalse(photo.bitmap.isRecycled)
    }
}
