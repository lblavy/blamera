package dev.blamera.camera

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class RgbaCameraFrameTest {
    private fun image(
        rotation: Int,
        format: Int = PixelFormat.RGBA_8888,
        width: Int = 4,
        height: Int = 3,
        crop: Rect = Rect(1, 0, 4, 2),
        stride: Int = 4
    ): ImageProxy {
        val bytes = ByteArray(60)
        for (y in 0..2) {
            for (x in 0..3) {
                val i = y * 20 + x * 4
                bytes[i] = (x * 60).toByte()
                bytes[i + 2] = (y * 100).toByte()
                bytes[i + 3] = 255.toByte()
            }
        }
        val plane = proxy<ImageProxy.PlaneProxy> { name ->
            when (name) {
                "getBuffer" -> ByteBuffer.wrap(bytes)
                "getPixelStride" -> stride
                "getRowStride" -> 20
                else -> null
            }
        }
        val info = proxy<ImageInfo> { if (it == "getRotationDegrees") rotation else null }
        return proxy { name ->
            when (name) {
                "getWidth" -> width
                "getHeight" -> height
                "getFormat" -> format
                "getPlanes" -> arrayOf(plane)
                "getImageInfo" -> info
                "getCropRect" -> crop
                else -> null
            }
        }
    }

    @Test fun rgbaPaddingCropAndFourRotationsKeepAsymmetricPixelsAndCoordinates() {
        val expectedCorners =
            listOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(2f, 0f),
                floatArrayOf(3f, 2f),
                floatArrayOf(0f, 3f)
            )
        listOf(0, 90, 180, 270).forEachIndexed { index, rotation ->
            val frame = RgbaCameraFrame(image(rotation))
            val bitmap = frame.bitmap()
            assertEquals(if (rotation % 180 == 0) 3 else 2, bitmap.width)
            assertEquals(if (rotation % 180 == 0) 2 else 3, bitmap.height)
            val corner = floatArrayOf(-1f, -1f)
            frame.transform().matrix.mapPoints(corner)
            assertArrayEquals(expectedCorners[index], corner, 0.01f)
            val red = when (rotation) {
                0 -> bitmap.getPixel(0, 0)
                90 -> bitmap.getPixel(1, 0)
                180 -> bitmap.getPixel(2, 1)
                else -> bitmap.getPixel(0, 2)
            }
            assertEquals(Color.rgb(60, 0, 0), red)
            bitmap.recycle()
        }
    }

    @Test fun rejectsNonRgbaBeforeAllocating() {
        assertThrows(IllegalArgumentException::class.java) {
            RgbaCameraFrame(image(0, 35)).bitmap()
        }
    }

    @Test fun rejectsHugeEmptyOutOfBoundsAndWrongPixelStrideBeforeReadingBuffer() {
        val invalid = listOf(
            image(0, width = 5000, height = 5000), image(0, width = 0),
            image(0, crop = Rect(0, 0, 0, 1)), image(0, crop = Rect(0, 0, 1, 0)),
            image(0, crop = Rect(-1, 0, 1, 1)), image(0, crop = Rect(0, -1, 1, 1)),
            image(
                0,
                crop = Rect(0, 0, 5, 1)
            ),
            image(0, crop = Rect(0, 0, 1, 4)), image(0, stride = 3)
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { RgbaCameraFrame(it).bitmap() }
        }
    }

    @Test fun cropRotatedCoordinatesProjectIntoFitPaddingAcrossFourRotationsAndResize() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val source = RgbaCameraFrame(image(rotation)).transform()
            val width = if (rotation % 180 == 0) 3f else 2f
            val height = if (rotation % 180 == 0) 2f else 3f
            listOf(600f to 320f, 320f to 600f).forEach { (vw, vh) ->
                val scale = minOf(vw / width, vh / height)
                val left = (vw - width * scale) / 2
                val top = (vh - height * scale) / 2
                val toView = android.graphics.Matrix(source.matrix).apply {
                    postScale(scale, scale)
                    postTranslate(left, top)
                }
                val target = androidx.camera.view.transform.OutputTransform(
                    toView,
                    android.util.Size(3, 2)
                )
                val points = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
                androidx.camera.view.transform.CoordinateTransform(source, target).mapPoints(points)
                assertArrayEquals(
                    floatArrayOf(
                        left,
                        top,
                        left + width * scale,
                        top,
                        left + width * scale,
                        top + height * scale,
                        left,
                        top + height * scale
                    ),
                    points,
                    0.001f
                )
            }
        }
    }

    private inline fun <reified T> proxy(crossinline value: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            value(method.name)
        } as T
}
