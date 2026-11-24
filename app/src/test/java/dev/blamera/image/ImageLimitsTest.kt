package dev.blamera.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageLimitsTest {
    private val limits = ImageLimits()

    @Test
    fun fiftyMegapixelLandscapeIsReducedKeepingAspectRatio() {
        assertEquals(PreviewDimensions(2048, 1536), limits.previewDimensions(8192, 6144))
        assertEquals(PreviewDimensions(1536, 2048), limits.previewDimensions(6144, 8192))
        assertEquals(PreviewDimensions(20, 10), limits.previewDimensions(20, 10))
    }

    @Test
    fun pixelAndStreamLimitsRejectOversizedInputsAtTheirBoundaries() {
        assertEquals(
            ImageImportError.TOO_LARGE,
            failure {
                limits.previewDimensions(10_001, 10_000)
            }
        )
        limits.previewDimensions(10_000, 10_000)
        limits.checkEncodedSize(ImageLimits.MAX_BYTES)
        assertEquals(
            ImageImportError.TOO_LARGE,
            failure {
                limits.checkEncodedSize(ImageLimits.MAX_BYTES + 1)
            }
        )
    }

    @Test
    fun invalidDimensionsAreNotPreviewed() {
        assertEquals(ImageImportError.UNREADABLE, failure { limits.previewDimensions(0, 10) })
        assertEquals(ImageImportError.UNREADABLE, failure { limits.previewDimensions(10, -1) })
        assertEquals(PreviewDimensions(2048, 1), limits.previewDimensions(100_000, 1))
    }

    @Test
    fun onlyDocumentedStillImageFormatsAreAccepted() {
        listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heif",
            "image/heic"
        ).forEach(limits::checkFormat)
        assertEquals(ImageImportError.UNSUPPORTED, failure { limits.checkFormat("image/gif") })
        assertEquals(ImageImportError.UNSUPPORTED, failure { limits.checkFormat("image/svg+xml") })
    }

    private fun failure(action: () -> Unit) =
        assertThrows(ImageImportException::class.java, action).reason
}
