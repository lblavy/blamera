package dev.blamera.vision

internal class CompactPhotoDecoder {
    fun decode(raw: RawSegmentation, projection: Letterbox): List<VisionDetection> {
        require(
            raw.predictionShape.contentEquals(
                longArrayOf(1, MAX_OBJECTS.toLong(), ROW_SIZE.toLong())
            )
        )
        require(
            raw.prototypeShape.contentEquals(
                longArrayOf(1, MASK_CHANNELS.toLong(), EDGE.toLong(), EDGE.toLong())
            )
        )
        require(
            raw.predictions.size == MAX_OBJECTS * ROW_SIZE &&
                raw.prototypes.size == MASK_CHANNELS * PIXELS
        )
        require(raw.predictions.all(Float::isFinite) && raw.prototypes.all(Float::isFinite))
        return raw.predictions.asList().chunked(ROW_SIZE).mapNotNull { row ->
            decodeRow(row, raw, projection)
        }
    }

    private fun decodeRow(
        row: List<Float>,
        raw: RawSegmentation,
        projection: Letterbox
    ): VisionDetection? {
        if (row[SCORE] == 0f) {
            require(row.all { it == 0f }) { "Nonzero padding in compact model" }
            return null
        }
        require(row[SCORE] in YoloPostprocessor.CONFIDENCE..1f)
        val classId = row[CLASS].toInt()
        require(row[CLASS] == classId.toFloat() && classId in 0 until CATEGORY_COUNT)
        val box = ImageBox(row[0], row[1], row[2], row[BOX_BOTTOM])
        require(box.area > 0f)
        val logits = reconstruct(row.drop(COEFFICIENT_START), raw.prototypes)
        val mask = maskFromLogits(logits, box, projection)
        return if (mask.active.none { it }) {
            null
        } else {
            VisionDetection(
                classId,
                row[SCORE],
                projection.sourceBox(box),
                mask,
                traceIsocontour(logits, projection, box)
            )
        }
    }

    private fun reconstruct(coefficients: List<Float>, prototypes: FloatArray): LogitGrid {
        val logits = FloatArray(PIXELS)
        coefficients.forEachIndexed { channel, weight ->
            val start = channel * PIXELS
            for (pixel in logits.indices) logits[pixel] += weight * prototypes[start + pixel]
        }
        return LogitGrid(EDGE, EDGE, logits)
    }

    private companion object {
        const val MAX_OBJECTS = 20
        const val ROW_SIZE = 38
        const val CATEGORY_COUNT = 4585
        const val EDGE = 160
        const val PIXELS = EDGE * EDGE
        const val SCORE = 4
        const val CLASS = 5
        const val COEFFICIENT_START = 6
        const val MASK_CHANNELS = 32
        const val BOX_BOTTOM = 3
    }
}
