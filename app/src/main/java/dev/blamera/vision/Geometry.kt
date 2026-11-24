package dev.blamera.vision

import kotlin.math.round

data class ImageBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    fun iou(other: ImageBox): Float {
        val intersection = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f) *
            (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        val union = area + other.area - intersection
        return if (union > 0f) intersection / union else 0f
    }
}

data class ImagePoint(val x: Float, val y: Float)
data class ContourSegment(val start: ImagePoint, val end: ImagePoint)

data class Letterbox(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
    val scale = EDGE.toFloat() / maxOf(width, height)
    val scaledWidth = round(width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = round(height * scale).toInt().coerceAtLeast(1)
    val padLeft = (EDGE - scaledWidth) / 2
    val padTop = (EDGE - scaledHeight) / 2
    fun sourcePoint(x: Float, y: Float) = ImagePoint(
        ((x - padLeft) / scale).coerceIn(0f, width.toFloat()),
        ((y - padTop) / scale).coerceIn(0f, height.toFloat())
    )
    fun sourceBox(box: ImageBox): ImageBox {
        val first = sourcePoint(box.left, box.top)
        val last = sourcePoint(box.right, box.bottom)
        return ImageBox(first.x, first.y, last.x, last.y)
    }
    companion object {
        const val EDGE = 640
    }
}

data class ImageViewport(val scale: Float, val left: Float, val top: Float) {
    fun point(point: ImagePoint) = ImagePoint(point.x * scale + left, point.y * scale + top)
    companion object {
        fun fit(
            imageWidth: Int,
            imageHeight: Int,
            viewWidth: Float,
            viewHeight: Float
        ): ImageViewport {
            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            return ImageViewport(
                scale,
                (viewWidth - imageWidth * scale) / 2,
                (viewHeight - imageHeight * scale) / 2
            )
        }
    }
}
