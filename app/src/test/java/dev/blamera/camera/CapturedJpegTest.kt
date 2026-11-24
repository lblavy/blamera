package dev.blamera.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import dev.blamera.image.PhotoLoadResult
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturedJpegTest {
    @Test fun captureCropsThenRotatesTheJpegWithoutSavingAFile() {
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        for (y in 0 until 8) for (x in 8 until 16) bitmap.setPixel(x, y, Color.BLUE)
        val bytes = ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, this)
        }.toByteArray()
        val loaded = decodeCapturedJpeg(image(bytes, 90)) as PhotoLoadResult.Loaded
        assertEquals(8, loaded.photo.bitmap.width)
        assertEquals(16, loaded.photo.bitmap.height)
        assertTrue(Color.red(loaded.photo.bitmap.getPixel(4, 1)) > 200)
        assertTrue(Color.blue(loaded.photo.bitmap.getPixel(4, 14)) > 200)
        val cropped = decodeCapturedJpeg(
            image(bytes, 0, Rect(8, 0, 16, 8))
        ) as PhotoLoadResult.Loaded
        assertEquals(8, cropped.photo.original.width)
        assertEquals(8, cropped.photo.original.height)
        assertTrue(Color.blue(cropped.photo.bitmap.getPixel(4, 4)) > 200)
    }

    @Test fun corruptJpegAndUnsupportedRotationBecomeRecoverableFailures() {
        assertTrue(decodeCapturedJpeg(image(byteArrayOf(1, 2, 3), 0)) is PhotoLoadResult.Failed)
        assertTrue(decodeCapturedJpeg(image(byteArrayOf(), 45)) is PhotoLoadResult.Failed)
    }

    private fun image(bytes: ByteArray, rotation: Int, crop: Rect = Rect(0, 0, 16, 8)): ImageProxy {
        val info = proxy<ImageInfo> { if (it == "getRotationDegrees") rotation else null }
        val plane = proxy<ImageProxy.PlaneProxy> {
            if (it == "getBuffer") ByteBuffer.wrap(bytes) else null
        }
        return proxy {
            when (it) {
                "getFormat" -> ImageFormat.JPEG
                "getImageInfo" -> info
                "getPlanes" -> arrayOf(plane)
                "getCropRect" -> crop
                else -> null
            }
        }
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            call(method.name)
        } as T
}
