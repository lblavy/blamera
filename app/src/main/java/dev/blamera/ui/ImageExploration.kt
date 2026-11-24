package dev.blamera.ui

import dev.blamera.image.ImageCrop
import dev.blamera.vision.ImagePoint
import dev.blamera.vision.ImageViewport
import dev.blamera.vision.Letterbox
import dev.blamera.vision.VisionDetection
import kotlin.math.ceil
import kotlin.math.floor

internal data class ImageSpace(
    val width: Int,
    val height: Int,
    val viewWidth: Float,
    val viewHeight: Float
)

internal data class ImageExploration(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    fun viewport(width: Int, height: Int, viewWidth: Float, viewHeight: Float): ImageViewport {
        val fit = ImageViewport.fit(width, height, viewWidth, viewHeight)
        val scale = fit.scale * zoom
        return ImageViewport(
            scale,
            boundedOffset((viewWidth - width * scale) / 2 + panX, width * scale, viewWidth),
            boundedOffset((viewHeight - height * scale) / 2 + panY, height * scale, viewHeight)
        )
    }

    fun move(
        space: ImageSpace,
        centroid: ImagePoint,
        pan: ImagePoint,
        factor: Float
    ): ImageExploration {
        val width = space.width
        val height = space.height
        val viewWidth = space.viewWidth
        val viewHeight = space.viewHeight
        val before = viewport(width, height, viewWidth, viewHeight)
        val nextZoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val ratio = nextZoom / zoom
        val left = centroid.x - (centroid.x - before.left) * ratio + pan.x
        val top = centroid.y - (centroid.y - before.top) * ratio + pan.y
        val scale = ImageViewport.fit(width, height, viewWidth, viewHeight).scale * nextZoom
        return ImageExploration(
            nextZoom,
            boundedOffset(left, width * scale, viewWidth) - (viewWidth - width * scale) / 2,
            boundedOffset(top, height * scale, viewHeight) - (viewHeight - height * scale) / 2
        )
    }

    private fun boundedOffset(offset: Float, image: Float, view: Float) =
        if (image <= view) (view - image) / 2 else offset.coerceIn(view - image, 0f)

    private companion object {
        const val MAX_ZOOM = 6f
    }
}

internal fun objectsAt(
    detections: List<VisionDetection>,
    point: ImagePoint,
    projection: Letterbox
): List<Int> {
    val insideX = point.x >= 0 && point.x < projection.width
    val insideY = point.y >= 0 && point.y < projection.height
    if (!insideX || !insideY) {
        return emptyList()
    }
    val networkX = point.x * projection.scale + projection.padLeft
    val networkY = point.y * projection.scale + projection.padTop
    return detections.indices.filter { index ->
        val mask = detections[index].mask
        mask.contains(
            (networkX * mask.width / Letterbox.EDGE).toInt(),
            (networkY * mask.height / Letterbox.EDGE).toInt()
        )
    }.sortedBy { detections[it].box.area }
}

internal fun toggleObject(selected: Int?, hits: List<Int>): Int? =
    if (selected in hits) null else hits.firstOrNull()

internal fun visibleImageCrop(space: ImageSpace, viewport: ImageViewport): ImageCrop? =
    visibleImageRegion(space, viewport)?.takeIf {
        it.width != space.width || it.height != space.height
    }

internal fun visibleImageRegion(space: ImageSpace, viewport: ImageViewport): ImageCrop? {
    if (!validCropViewport(space, viewport)) return null
    val scale = viewport.scale.toDouble()
    val crop = ImageCrop(
        cropEdge(-viewport.left / scale, space.width, false),
        cropEdge(-viewport.top / scale, space.height, false),
        cropEdge((space.viewWidth - viewport.left) / scale, space.width, true),
        cropEdge((space.viewHeight - viewport.top) / scale, space.height, true)
    )
    val nonempty = crop.width > 0 && crop.height > 0
    return crop.takeIf { nonempty }
}

private fun validCropViewport(space: ImageSpace, viewport: ImageViewport): Boolean {
    val sourceValid = space.width > 0 && space.height > 0
    val viewValid = space.viewWidth > 0 && space.viewHeight > 0
    val finite = listOf(
        viewport.scale,
        viewport.left,
        viewport.top,
        space.viewWidth,
        space.viewHeight
    )
        .all { it.isFinite() }
    val transformValid = viewport.scale > 0 && finite
    return sourceValid && viewValid && transformValid
}

private fun cropEdge(value: Double, limit: Int, roundUp: Boolean): Int {
    val clipped = value.coerceIn(0.0, limit.toDouble())
    return (if (roundUp) ceil(clipped) else floor(clipped)).toInt()
}
