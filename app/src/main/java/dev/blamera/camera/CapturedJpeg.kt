package dev.blamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import dev.blamera.image.DecodedPhoto
import dev.blamera.image.ImageImportError
import dev.blamera.image.ImageImportException
import dev.blamera.image.ImageLimits
import dev.blamera.image.PhotoLoadResult
import dev.blamera.image.PreviewDimensions

internal fun decodeCapturedJpeg(image: ImageProxy): PhotoLoadResult = try {
    require(image.format == ImageFormat.JPEG)
    val rotation = image.imageInfo.rotationDegrees
    require(
        rotation in setOf(0, CAPTURE_QUARTER_TURN, CAPTURE_HALF_TURN, CAPTURE_REVERSE_LANDSCAPE)
    )
    val buffer = image.planes.single().buffer.duplicate()
    ImageLimits().checkEncodedSize(buffer.remaining())
    val encoded = ByteArray(buffer.remaining()).also(buffer::get)
    PhotoLoadResult.Loaded(decodePhoto(encoded, image.cropRect, rotation))
} catch (error: ImageImportException) {
    PhotoLoadResult.Failed(error.reason)
} catch (_: IllegalArgumentException) {
    PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
} catch (_: OutOfMemoryError) {
    PhotoLoadResult.Failed(ImageImportError.MEMORY)
}

private fun decodePhoto(encoded: ByteArray, crop: Rect, rotation: Int): DecodedPhoto {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
    ImageLimits().previewDimensions(options.outWidth, options.outHeight)
    val sourceWidth = options.outWidth
    val sourceHeight = options.outHeight
    require(crop.width() > 0 && crop.height() > 0)
    require(
        crop.left >= 0 && crop.top >= 0 && crop.right <= options.outWidth &&
            crop.bottom <= options.outHeight
    )
    options.inSampleSize = captureSample(options.outWidth, options.outHeight)
    options.inJustDecodeBounds = false
    val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options))
    val cropped = cropDecoded(decoded, crop, sourceWidth, sourceHeight)
    val bitmap = rotateCaptured(cropped, rotation)
    val original = if (rotation % CAPTURE_HALF_TURN == 0) {
        PreviewDimensions(crop.width(), crop.height())
    } else {
        PreviewDimensions(crop.height(), crop.width())
    }
    return DecodedPhoto(bitmap, original)
}

private fun captureSample(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > ImageLimits.PREVIEW_EDGE) sample *= 2
    return sample
}

private fun cropDecoded(bitmap: Bitmap, crop: Rect, sourceWidth: Int, sourceHeight: Int): Bitmap {
    val left = (crop.left.toLong() * bitmap.width / sourceWidth).toInt()
    val top = (crop.top.toLong() * bitmap.height / sourceHeight).toInt()
    val right = (crop.right.toLong() * bitmap.width / sourceWidth).toInt().coerceAtLeast(left + 1)
    val bottom = (crop.bottom.toLong() * bitmap.height / sourceHeight).toInt().coerceAtLeast(
        top + 1
    )
    var retained = false
    try {
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        retained = cropped === bitmap
        return cropped
    } finally {
        if (!retained) bitmap.recycle()
    }
}

private const val CAPTURE_QUARTER_TURN = 90
private const val CAPTURE_HALF_TURN = 180
private const val CAPTURE_REVERSE_LANDSCAPE = 270

private fun rotateCaptured(bitmap: Bitmap, rotation: Int): Bitmap {
    if (rotation == 0) return bitmap
    try {
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        )
    } finally {
        bitmap.recycle()
    }
}
