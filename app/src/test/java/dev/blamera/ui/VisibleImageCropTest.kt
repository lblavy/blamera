package dev.blamera.ui

import dev.blamera.image.ImageCrop
import dev.blamera.vision.ImageViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleImageCropTest {
    @Test fun unzoomedFitWithBlankBandsDoesNotOfferAFalseCrop() {
        val space = ImageSpace(400, 200, 300f, 500f)
        assertNull(visibleImageCrop(space, ImageViewport.fit(400, 200, 300f, 500f)))
    }

    @Test fun visiblePixelsUseTheInverseViewportAndRoundOutward() {
        assertEquals(
            ImageCrop(40, 10, 191, 136),
            visibleImageCrop(ImageSpace(400, 200, 300f, 250f), ImageViewport(2f, -80.5f, -20.25f))
        )
        val exploration = ImageExploration(2f, 65.3f, 0f)
        assertEquals(
            ImageCrop(56, 0, 257, 200),
            visibleImageCrop(
                ImageSpace(400, 200, 300f, 500f),
                exploration.viewport(400, 200, 300f, 500f)
            )
        )
    }

    @Test fun invalidViewsAndOffscreenOrZeroAreaRegionsAreRejected() {
        val space = ImageSpace(400, 200, 300f, 500f)
        listOf(
            ImageViewport(0f, 0f, 0f),
            ImageViewport(Float.NaN, 0f, 0f),
            ImageViewport(1f, 1000f, 0f)
        ).forEach { assertNull(visibleImageCrop(space, it)) }
        assertNull(visibleImageCrop(space.copy(viewWidth = 0f), ImageViewport(1f, 0f, 0f)))
    }

    @Test fun portraitZoomInLandscapeCropsOnlyHeightAndExcludesSideBands() {
        val space = ImageSpace(200, 400, 800f, 400f)
        val viewport = ImageExploration(2f).viewport(200, 400, 800f, 400f)
        assertEquals(200f, viewport.left, 0.001f)
        assertEquals(ImageCrop(0, 100, 200, 300), visibleImageCrop(space, viewport))
    }
}
