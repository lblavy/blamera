package dev.blamera.image

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ImageCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

internal fun interface PhotoCropper {
    suspend fun crop(photo: DecodedPhoto, region: ImageCrop): PhotoLoadResult
}

internal class AndroidPhotoCropper(
    private val copy: (Bitmap, ImageCrop) -> Bitmap = ::copyImageRegion
) : PhotoCropper {
    override suspend fun crop(photo: DecodedPhoto, region: ImageCrop): PhotoLoadResult =
        withContext(Dispatchers.Default) {
            ImageMemory.mutex.withLock { copyOwned(photo, region) }
        }

    private suspend fun copyOwned(photo: DecodedPhoto, region: ImageCrop): PhotoLoadResult {
        var image: Bitmap? = null
        var published = false
        return try {
            currentCoroutineContext().ensureActive()
            validateCrop(photo.bitmap, region)
            val copied = copy(photo.bitmap, region)
            image = copied
            currentCoroutineContext().ensureActive()
            val result = PhotoLoadResult.Loaded(
                DecodedPhoto(copied, PreviewDimensions(region.width, region.height))
            )
            published = true
            result
        } catch (_: IllegalArgumentException) {
            PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
        } catch (_: OutOfMemoryError) {
            PhotoLoadResult.Failed(ImageImportError.MEMORY)
        } finally {
            if (!published && image !== photo.bitmap) image?.recycle()
        }
    }
}

private fun validateCrop(bitmap: Bitmap, region: ImageCrop) {
    require(!bitmap.isRecycled)
    val startsInside = region.left >= 0 && region.top >= 0
    val endsInside = region.right <= bitmap.width && region.bottom <= bitmap.height
    val nonempty = region.width > 0 && region.height > 0
    require(startsInside && endsInside && nonempty)
}

private fun copyImageRegion(bitmap: Bitmap, region: ImageCrop): Bitmap =
    Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
