package dev.blamera.vision

import kotlin.math.floor

/** Model-space samples at cell centers, with bilinear extension to the crop boundary. */
data class LogitGrid(val width: Int, val height: Int, val values: FloatArray) {
    init {
        require(width in 1..YoloPostprocessor.MASK_EDGE && height in 1..YoloPostprocessor.MASK_EDGE)
        require(values.size == width * height)
        if (values.any { !it.isFinite() }) throw VisionException("Non-finite mask logits")
    }

    fun sample(modelX: Float, modelY: Float): Float {
        val x = (modelX * width / Letterbox.EDGE - HALF_PIXEL).coerceIn(0f, (width - 1).toFloat())
        val y = (modelY * height / Letterbox.EDGE - HALF_PIXEL).coerceIn(0f, (height - 1).toFloat())
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val top = blend(values[y0 * width + x0], values[y0 * width + x1], x - x0)
        val bottom = blend(values[y1 * width + x0], values[y1 * width + x1], x - x0)
        return blend(top, bottom, y - y0)
    }
}

private fun blend(first: Float, last: Float, weight: Float) = first * (1 - weight) + last * weight
private const val HALF_PIXEL = 0.5f
