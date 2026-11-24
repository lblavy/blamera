package dev.blamera.benchmark

import dev.blamera.vision.ImageBox
import dev.blamera.vision.Letterbox
import dev.blamera.vision.LogitGrid
import dev.blamera.vision.traceIsocontour

internal data class BenchmarkDetection(
    val classId: Int,
    val nameEnglish: String,
    val score: Float,
    val box: ImageBox,
    val networkBox: ImageBox,
    val mask: ByteArray,
    val activePixels: Int,
    val segments: Int
)

internal fun compactDetections(
    rows: FloatArray,
    prototypes: FloatArray,
    projection: Letterbox,
    labels: List<String>,
    retainMasks: Boolean
): List<BenchmarkDetection> = (0 until BENCHMARK_ROWS).mapNotNull { index ->
    val offset = index * BENCHMARK_ROW_WIDTH
    if (rows[offset + BENCHMARK_SCORE] == 0f) return@mapNotNull null
    val classId = rows[offset + BENCHMARK_CLASS].toInt()
    val box = ImageBox(
        rows[offset],
        rows[offset + 1],
        rows[offset + BENCHMARK_BOX_RIGHT],
        rows[offset + BENCHMARK_BOX_BOTTOM]
    )
    val logits = benchmarkLogits(rows, offset, prototypes)
    val (packed, active) = benchmarkMask(logits, box, projection)
    BenchmarkDetection(
        classId,
        labels[classId],
        rows[offset + BENCHMARK_SCORE],
        projection.sourceBox(box),
        box,
        if (retainMasks) packed else byteArrayOf(),
        active,
        if (active == 0) 0 else traceIsocontour(logits, projection, box).size
    )
}

private fun benchmarkLogits(rows: FloatArray, offset: Int, prototypes: FloatArray) = LogitGrid(
    BENCHMARK_MASK_EDGE,
    BENCHMARK_MASK_EDGE,
    FloatArray(BENCHMARK_MASK_EDGE * BENCHMARK_MASK_EDGE) { pixel ->
        var value = 0f
        for (channel in 0 until BENCHMARK_COEFFICIENTS) {
            val coefficient = rows[offset + BENCHMARK_COEFFICIENT_OFFSET + channel]
            value +=
                coefficient *
                prototypes[channel * BENCHMARK_MASK_EDGE * BENCHMARK_MASK_EDGE + pixel]
        }
        value
    }
)
private fun benchmarkMask(
    logits: LogitGrid,
    box: ImageBox,
    projection: Letterbox
): Pair<ByteArray, Int> {
    val packed = ByteArray(BENCHMARK_MASK_BYTES)
    var active = 0
    for (pixel in logits.values.indices) {
        if (logits.values[pixel] > 0f && insideBenchmarkCrop(pixel, box, projection)) {
            val byteIndex = pixel / BENCHMARK_BITS_PER_BYTE
            val bit = BENCHMARK_MSB shr (pixel % BENCHMARK_BITS_PER_BYTE)
            packed[byteIndex] = (packed[byteIndex].toInt() or bit).toByte()
            active++
        }
    }
    return packed to active
}

private fun insideBenchmarkCrop(pixel: Int, box: ImageBox, projection: Letterbox): Boolean {
    val x = (pixel % BENCHMARK_MASK_EDGE) * BENCHMARK_NETWORK_STEP
    val y = (pixel / BENCHMARK_MASK_EDGE) * BENCHMARK_NETWORK_STEP
    return x >= maxOf(box.left, projection.padLeft.toFloat()) &&
        x < minOf(box.right, (projection.padLeft + projection.scaledWidth).toFloat()) &&
        y >= maxOf(box.top, projection.padTop.toFloat()) &&
        y < minOf(box.bottom, (projection.padTop + projection.scaledHeight).toFloat())
}
