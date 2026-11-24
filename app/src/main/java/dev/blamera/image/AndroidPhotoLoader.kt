package dev.blamera.image

import android.graphics.ImageDecoder
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidPhotoLoader(
    private val open: (Uri) -> InputStream?,
    private val limits: ImageLimits = ImageLimits()
) : PhotoLoader {
    override suspend fun load(uri: Uri): PhotoLoadResult = withContext(Dispatchers.IO) {
        // ImageDecoder is synchronous: serialize even cancelled requests until native decode returns.
        ImageMemory.mutex.withLock { decode(uri) }
    }

    private fun decode(uri: Uri): PhotoLoadResult = try {
        val encoded = open(uri)?.use { it.readNBytes(limits.maxBytes + 1) }
        if (encoded == null) {
            PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
        } else {
            limits.checkEncodedSize(encoded.size)
            decodeBytes(encoded)
        }
    } catch (error: ImageImportException) {
        PhotoLoadResult.Failed(error.reason)
    } catch (_: IOException) {
        PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
    } catch (_: SecurityException) {
        PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
    } catch (_: IllegalArgumentException) {
        PhotoLoadResult.Failed(ImageImportError.UNREADABLE)
    } catch (_: OutOfMemoryError) {
        PhotoLoadResult.Failed(ImageImportError.MEMORY)
    }

    private fun decodeBytes(encoded: ByteArray): PhotoLoadResult {
        lateinit var original: PreviewDimensions
        val bitmap = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(ByteBuffer.wrap(encoded))
        ) {
                decoder,
                info,
                _
            ->
            limits.checkFormat(info.mimeType)
            original = PreviewDimensions(info.size.width, info.size.height)
            val preview = limits.previewDimensions(original.width, original.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(preview.width, preview.height)
        }
        return PhotoLoadResult.Loaded(DecodedPhoto(bitmap, original))
    }
}
