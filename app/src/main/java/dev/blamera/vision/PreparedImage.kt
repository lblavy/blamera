package dev.blamera.vision

import kotlin.math.round

data class PreparedImage(val values: FloatArray, val letterbox: Letterbox)

fun prepareImage(pixels: IntArray, width: Int, height: Int): PreparedImage {
    require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
    val letterbox = Letterbox(width, height)
    val sampler = BilinearPixels(pixels, width, height)
    val plane = Letterbox.EDGE * Letterbox.EDGE
    val values = FloatArray(RGB_CHANNELS * plane) { PADDING / CHANNEL_MAX }
    for (y in 0 until letterbox.scaledHeight) {
        val sy = ((y + HALF_PIXEL) * height / letterbox.scaledHeight - HALF_PIXEL).coerceAtLeast(0f)
        for (x in 0 until letterbox.scaledWidth) {
            val sx = ((x + HALF_PIXEL) * width / letterbox.scaledWidth - HALF_PIXEL).coerceAtLeast(
                0f
            )
            val target = (y + letterbox.padTop) * Letterbox.EDGE + x + letterbox.padLeft
            for (channel in 0 until RGB_CHANNELS) {
                values[channel * plane + target] =
                    sampler.channel(sx, sy, (2 - channel) * BYTE_BITS)
            }
        }
    }
    return PreparedImage(values, letterbox)
}

private class BilinearPixels(
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int
) {
    fun channel(x: Float, y: Float, shift: Int): Float {
        val x0 = x.toInt().coerceAtMost(width - 1)
        val y0 = y.toInt().coerceAtMost(height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        fun channel(px: Int, py: Int) = (pixels[py * width + px] ushr shift and BYTE_MASK).toFloat()
        val dx = x - x0
        val dy = y - y0
        val top = channel(x0, y0) * (1 - dx) + channel(x1, y0) * dx
        val bottom = channel(x0, y1) * (1 - dx) + channel(x1, y1) * dx
        return round(top * (1 - dy) + bottom * dy) / CHANNEL_MAX
    }
}

private const val RGB_CHANNELS = 3
private const val PADDING = 114f
private const val CHANNEL_MAX = 255f
private const val BYTE_BITS = 8
private const val BYTE_MASK = 255
private const val HALF_PIXEL = 0.5f
