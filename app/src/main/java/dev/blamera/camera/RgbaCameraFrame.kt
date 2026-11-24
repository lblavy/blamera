package dev.blamera.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
internal class RgbaCameraFrame(private val image: ImageProxy) : LiveFrame {
    override fun bitmap(): Bitmap {
        require(image.format == PixelFormat.RGBA_8888)
        require(image.width.toLong() * image.height in 1..MAX_PIXELS)
        val plane = image.planes.single()
        require(plane.pixelStride == BYTES_PER_PIXEL)
        val crop = image.cropRect
        require(crop.width() > 0 && crop.height() > 0)
        require(
            crop.left >= 0 && crop.top >= 0 && crop.right <= image.width &&
                crop.bottom <= image.height
        )
        val buffer = plane.buffer.duplicate()
        val pixels = IntArray(crop.width() * crop.height()) { index ->
            val x = crop.left + index % crop.width()
            val y = crop.top + index / crop.width()
            val offset = y * plane.rowStride + x * plane.pixelStride
            Color.argb(
                buffer.get(offset + ALPHA_CHANNEL).toInt() and CHANNEL_MASK,
                buffer.get(offset).toInt() and CHANNEL_MASK,
                buffer.get(offset + 1).toInt() and CHANNEL_MASK,
                buffer.get(offset + 2).toInt() and CHANNEL_MASK
            )
        }
        val bitmap = Bitmap.createBitmap(
            pixels,
            crop.width(),
            crop.height(),
            Bitmap.Config.ARGB_8888
        )
        return rotateOwned(bitmap)
    }

    private fun rotateOwned(bitmap: Bitmap): Bitmap {
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        try {
            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                false
            )
        } finally {
            bitmap.recycle()
        }
    }

    override fun transform() = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }.getOutputTransform(image)

    override fun close() = image.close()

    private companion object {
        const val MAX_PIXELS = 4_000_000L
        const val BYTES_PER_PIXEL = 4
        const val ALPHA_CHANNEL = 3
        const val CHANNEL_MASK = 255
    }
}
