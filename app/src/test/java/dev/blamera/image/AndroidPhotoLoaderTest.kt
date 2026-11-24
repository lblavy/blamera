package dev.blamera.image

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPhotoLoaderTest {
    private val uri = Uri.parse("content://synthetic/photo")

    @Test
    fun pngIsDecodedOnIoWithDimensionsAndColors() = runBlocking {
        val image = png(8, 4)
        val caller = Thread.currentThread()
        var reader: Thread? = null
        val result = AndroidPhotoLoader({
            reader = Thread.currentThread()
            ByteArrayInputStream(image)
        }).load(uri)
        assertTrue(result is PhotoLoadResult.Loaded)
        result as PhotoLoadResult.Loaded
        assertEquals(PreviewDimensions(8, 4), result.photo.original)
        assertEquals(8, result.photo.bitmap.width)
        assertEquals(Color.RED, result.photo.bitmap.getPixel(0, 0))
        assertTrue(reader !== caller)
    }

    @Test
    fun targetSizeIsAppliedDuringNativeDecode() = runBlocking {
        val result = AndroidPhotoLoader({
            ByteArrayInputStream(png(80, 40))
        }, ImageLimits(previewEdge = 20)).load(uri)
        assertTrue(result is PhotoLoadResult.Loaded)
        result as PhotoLoadResult.Loaded
        assertEquals(PreviewDimensions(80, 40), result.photo.original)
        assertEquals(20, result.photo.bitmap.width)
        assertEquals(10, result.photo.bitmap.height)
    }

    @Test
    fun invalidInputAndUnavailableAccessAreRecoverable() = runBlocking {
        val cases = listOf<() -> java.io.InputStream?>(
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            { null },
            { throw SecurityException("synthetic") },
            { throw IOException("synthetic") }
        )
        cases.forEach { open ->
            assertEquals(
                PhotoLoadResult.Failed(ImageImportError.UNREADABLE),
                AndroidPhotoLoader({
                    open()
                }).load(uri)
            )
        }
    }

    @Test
    fun byteAndPixelBoundsAreCheckedBeforeFullDecode() = runBlocking {
        val bytes = png(8, 4)
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.TOO_LARGE),
            AndroidPhotoLoader({ ByteArrayInputStream(bytes) }, ImageLimits(maxBytes = 8)).load(uri)
        )
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.TOO_LARGE),
            AndroidPhotoLoader({
                ByteArrayInputStream(bytes)
            }, ImageLimits(maxPixels = 20)).load(uri)
        )
    }

    @Test
    fun memoryExhaustionHasAnExplicitRecoverableResult() = runBlocking {
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.MEMORY),
            AndroidPhotoLoader({ throw OutOfMemoryError("synthetic") }).load(uri)
        )
    }

    @Test
    fun webpIsDecodedAndGifIsRejectedAsUnsupported() = runBlocking {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val webp = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val decoded = AndroidPhotoLoader({ ByteArrayInputStream(webp) }).load(uri)
        assertTrue(decoded is PhotoLoadResult.Loaded)
        decoded as PhotoLoadResult.Loaded
        assertEquals(Color.BLUE, decoded.photo.bitmap.getPixel(0, 0))
        val gif = Base64.decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==", Base64.DEFAULT)
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.UNSUPPORTED),
            AndroidPhotoLoader({
                ByteArrayInputStream(gif)
            }).load(uri)
        )
    }

    @Test
    fun readerIsClosedAndEmptyInputIsRecoverable() = runBlocking {
        var closed = false
        val stream = object : ByteArrayInputStream(byteArrayOf()) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertEquals(
            PhotoLoadResult.Failed(ImageImportError.UNREADABLE),
            AndroidPhotoLoader({
                stream
            }).load(uri)
        )
        assertTrue(closed)
    }

    @Test
    fun jpegExifRotationsAlsoMoveTheColoredMarkers() = runBlocking {
        listOf(6, 8).forEach { orientation ->
            val photo = AndroidPhotoLoader({
                ByteArrayInputStream(rotatedJpeg(orientation))
            }).load(uri)
            assertTrue(photo is PhotoLoadResult.Loaded)
            photo as PhotoLoadResult.Loaded
            assertEquals(PreviewDimensions(20, 40), photo.photo.original)
            val bitmap = photo.photo.bitmap
            assertEquals(20, bitmap.width)
            assertEquals(40, bitmap.height)
            val top = bitmap.getPixel(10, 5)
            val bottom = bitmap.getPixel(10, 35)
            val red = if (orientation == 6) top else bottom
            val blue = if (orientation == 6) bottom else top
            assertTrue(Color.red(red) > 200 && Color.blue(red) < 40)
            assertTrue(Color.blue(blue) > 200 && Color.red(blue) < 40)
        }
    }

    @Test
    fun cancellationDoesNotStartAnotherDecodeWhileFirstReaderIsBlocked() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val readers = AtomicInteger()
        val bytes = png(8, 4)
        val loader = AndroidPhotoLoader({
            if (readers.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            ByteArrayInputStream(bytes)
        })
        val first = launch(kotlinx.coroutines.Dispatchers.Default) { loader.load(uri) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        first.cancel()
        val second = launch(kotlinx.coroutines.Dispatchers.Default) { loader.load(uri) }
        Thread.sleep(50)
        assertEquals(1, readers.get())
        release.countDown()
        first.cancelAndJoin()
        second.join()
        assertEquals(2, readers.get())
    }

    @Test
    fun anotherLoaderAfterRecreationWaitsForCancelledNativeWork() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val bytes = png(8, 4)
        val firstLoader = AndroidPhotoLoader({
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            ByteArrayInputStream(bytes)
        })
        val secondLoader = AndroidPhotoLoader({
            secondEntered.countDown()
            ByteArrayInputStream(bytes)
        })
        val first = launch(kotlinx.coroutines.Dispatchers.Default) { firstLoader.load(uri) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        first.cancel()
        val second = launch(kotlinx.coroutines.Dispatchers.Default) { secondLoader.load(uri) }
        try {
            assertFalse(
                "A new loader must wait for the cancelled decoder",
                secondEntered.await(500, TimeUnit.MILLISECONDS)
            )
        } finally {
            release.countDown()
            first.cancelAndJoin()
            second.join()
        }
        assertEquals(0L, secondEntered.count)
    }

    private fun rotatedJpeg(orientation: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) {
            for (y in 0 until 20) {
                bitmap.setPixel(
                    x,
                    y,
                    if (x <
                        20
                    ) {
                        Color.RED
                    } else {
                        Color.BLUE
                    }
                )
            }
        }
        val jpeg = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
            .put("II".toByteArray()).putShort(42).putInt(8).putShort(1)
            .putShort(
                0x112
            ).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0).putInt(0).array()
        return jpeg.copyOfRange(0, 2) + byteArrayOf(-1, -31, 0, 34) +
            "Exif\u0000\u0000".toByteArray() +
            tiff +
            jpeg.copyOfRange(2, jpeg.size)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
