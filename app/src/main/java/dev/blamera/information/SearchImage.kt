package dev.blamera.information

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.blamera.image.ImageCrop
import dev.blamera.image.ImageMemory
import dev.blamera.vision.ImageBox
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SearchImage(val preview: Bitmap, val jpeg: ByteArray) : AutoCloseable {
    override fun close() = preview.recycle()
}

internal fun interface SearchImagePreparer {
    suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage

    suspend fun prepareRegion(source: Bitmap, crop: ImageCrop): SearchImage =
        AndroidSearchImagePreparer().prepareRegion(source, crop)
}

internal class AndroidSearchImagePreparer private constructor(
    private val maxEdge: Int,
    private val maxJpegBytes: Int,
    private val encode: (Bitmap, ByteArrayOutputStream) -> Boolean
) : SearchImagePreparer {
    constructor(
        encode: (Bitmap, ByteArrayOutputStream) -> Boolean = { image, output ->
            image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    ) : this(MAX_SEARCH_EDGE, MAX_JPEG_BYTES, encode)

    constructor(maxEdge: Int, jpegQuality: Int, maxJpegBytes: Int) : this(
        maxEdge,
        maxJpegBytes,
        { image, output ->
            image.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
        }
    )
    override suspend fun prepare(source: Bitmap, box: ImageBox): SearchImage {
        val crop = searchCrop(source, box)
        return prepareRegion(source, ImageCrop(crop.left, crop.top, crop.right, crop.bottom))
    }

    override suspend fun prepareRegion(source: Bitmap, crop: ImageCrop): SearchImage {
        var prepared: SearchImage? = null
        var returned = false
        return try {
            val result = withContext(Dispatchers.Default) {
                ImageMemory.mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    prepareImage(source, crop).also { prepared = it }
                }
            }
            returned = true
            result
        } finally {
            if (!returned) prepared?.close()
        }
    }

    private fun prepareImage(source: Bitmap, region: ImageCrop): SearchImage {
        require(!source.isRecycled)
        require(
            region.left >= 0 && region.top >= 0 && region.right <= source.width &&
                region.bottom <= source.height
        )
        require(region.width > 0 && region.height > 0)
        val crop = Rect(region.left, region.top, region.right, region.bottom)
        val ratio = min(1f, maxEdge.toFloat() / max(crop.width(), crop.height()))
        val width = max(1, (crop.width() * ratio).roundToInt())
        val height = max(1, (crop.height() * ratio).roundToInt())
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            Canvas(image).drawBitmap(
                source,
                crop,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            val output = BoundedJpegOutput(maxJpegBytes)
            check(encode(image, output)) { "Image encoding failed" }
            val jpeg = output.toByteArray()
            require(jpeg.isNotEmpty() && jpeg.size <= maxJpegBytes)
            val preview = checkNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
            SearchImage(preview, jpeg)
        } finally {
            image.recycle()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 80
    }
}

internal const val MAX_JPEG_BYTES = 2 * 1024 * 1024
internal const val MAX_SEARCH_EDGE = 1000
private const val SEARCH_CONTEXT_MARGIN = 0.05f

private class BoundedJpegOutput(private val maximum: Int) : ByteArrayOutputStream() {
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(count.toLong() + length <= maximum)
        super.write(bytes, offset, length)
    }

    override fun write(value: Int) {
        require(count < maximum)
        super.write(value)
    }
}

internal fun searchCrop(source: Bitmap, box: ImageBox): Rect {
    require(!source.isRecycled)
    require(listOf(box.left, box.top, box.right, box.bottom).all(Float::isFinite))
    require(box.right > box.left && box.bottom > box.top)
    val marginX = (box.right - box.left) * SEARCH_CONTEXT_MARGIN
    val marginY = (box.bottom - box.top) * SEARCH_CONTEXT_MARGIN
    val crop = Rect(
        floor(box.left - marginX).toInt().coerceIn(0, source.width),
        floor(box.top - marginY).toInt().coerceIn(0, source.height),
        ceil(box.right + marginX).toInt().coerceIn(0, source.width),
        ceil(box.bottom + marginY).toInt().coerceIn(0, source.height)
    )
    require(crop.width() > 0 && crop.height() > 0)
    return crop
}
