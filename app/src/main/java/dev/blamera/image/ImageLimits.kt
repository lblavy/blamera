package dev.blamera.image

data class PreviewDimensions(val width: Int, val height: Int)

enum class ImageImportError { TOO_LARGE, UNSUPPORTED, UNREADABLE, MEMORY }

class ImageImportException(val reason: ImageImportError) : RuntimeException()

class ImageLimits(
    val maxBytes: Int = MAX_BYTES,
    private val maxPixels: Long = MAX_PIXELS,
    private val previewEdge: Int = PREVIEW_EDGE
) {
    fun checkEncodedSize(size: Int) {
        if (size > maxBytes) throw ImageImportException(ImageImportError.TOO_LARGE)
    }

    fun checkFormat(mimeType: String) {
        if (mimeType !in SUPPORTED_FORMATS) throw ImageImportException(ImageImportError.UNSUPPORTED)
    }

    fun previewDimensions(width: Int, height: Int): PreviewDimensions {
        if (width <= 0 || height <= 0) throw ImageImportException(ImageImportError.UNREADABLE)
        if (width.toLong() * height >
            maxPixels
        ) {
            throw ImageImportException(ImageImportError.TOO_LARGE)
        }
        val scale = minOf(1.0, previewEdge.toDouble() / maxOf(width, height))
        return PreviewDimensions(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1)
        )
    }

    companion object {
        const val MAX_BYTES = 30 * 1024 * 1024
        const val MAX_PIXELS = 100_000_000L
        const val PREVIEW_EDGE = 2048
        private val SUPPORTED_FORMATS =
            setOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic")
    }
}
