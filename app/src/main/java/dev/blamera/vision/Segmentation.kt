package dev.blamera.vision

import java.util.PriorityQueue

data class MaskGrid(val width: Int, val height: Int, val active: BooleanArray) {
    fun contains(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && active[y * width + x]
}

data class VisionDetection(
    val classId: Int,
    val score: Float,
    val box: ImageBox,
    val mask: MaskGrid,
    val contours: List<ContourSegment>
)

class VisionException(message: String) : RuntimeException(message)

data class RawSegmentation(
    val predictions: FloatArray,
    val prototypes: FloatArray,
    val predictionShape: LongArray =
        longArrayOf(1, YoloPostprocessor.CHANNELS.toLong(), YoloPostprocessor.ANCHORS.toLong()),
    val prototypeShape: LongArray =
        longArrayOf(
            1,
            YoloPostprocessor.MASK_CHANNELS.toLong(),
            YoloPostprocessor.MASK_EDGE.toLong(),
            YoloPostprocessor.MASK_EDGE.toLong()
        )
)

class YoloPostprocessor {
    fun decode(raw: RawSegmentation, projection: Letterbox): List<VisionDetection> {
        validate(raw)
        val selected = suppress(candidates(raw.predictions, projection))
        return selected.mapNotNull { candidate ->
            val logits =
                LogitGrid(
                    MASK_EDGE,
                    MASK_EDGE,
                    FloatArray(MASK_EDGE * MASK_EDGE) { index ->
                        maskLogit(index % MASK_EDGE, index / MASK_EDGE, candidate.anchor, raw)
                    }
                )
            val mask = maskFromLogits(logits, candidate.box, projection)
            if (mask.active.none { it }) {
                null
            } else {
                VisionDetection(
                    candidate.classId,
                    candidate.score,
                    projection.sourceBox(candidate.box),
                    mask,
                    traceIsocontour(logits, projection, candidate.box)
                )
            }
        }
    }

    private fun validate(raw: RawSegmentation) {
        if (!raw.predictionShape.contentEquals(
                longArrayOf(1, CHANNELS.toLong(), ANCHORS.toLong())
            ) ||
            !raw.prototypeShape.contentEquals(
                longArrayOf(1, MASK_CHANNELS.toLong(), MASK_EDGE.toLong(), MASK_EDGE.toLong())
            )
        ) {
            throw VisionException("Unexpected model output shape")
        }
        if (raw.predictions.size != CHANNELS * ANCHORS ||
            raw.prototypes.size != MASK_CHANNELS * MASK_EDGE * MASK_EDGE
        ) {
            throw VisionException("Unexpected output length")
        }
        validateFinite(raw)
    }

    private fun validateFinite(raw: RawSegmentation) {
        if (raw.predictions.any { !it.isFinite() } || raw.prototypes.any { !it.isFinite() }) {
            throw VisionException("Non-finite model output")
        }
    }

    private fun candidates(predictions: FloatArray, projection: Letterbox): List<Candidate> {
        val queue = PriorityQueue<Candidate>(compareBy { it.score })
        for (index in 0 until ANCHORS) {
            val candidate = candidate(predictions, index) ?: continue
            if (projection.sourceBox(candidate.box).area > 0f) retainCandidate(queue, candidate)
        }
        return queue.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.anchor })
    }

    private fun retainCandidate(queue: PriorityQueue<Candidate>, candidate: Candidate) {
        if (queue.size <
            MAX_CANDIDATES
        ) {
            queue.add(candidate)
        } else if (candidate.score >
            checkNotNull(queue.peek()).score
        ) {
            queue.poll()
            queue.add(candidate)
        }
    }

    private fun candidate(predictions: FloatArray, index: Int): Candidate? {
        val type = (0 until CLASS_COUNT).maxBy {
            predictions[(BOX_CHANNELS + it) * ANCHORS + index]
        }
        val score = predictions[(BOX_CHANNELS + type) * ANCHORS + index]
        if (score < CONFIDENCE) return null
        val cx = predictions[index]
        val cy = predictions[ANCHORS + index]
        val halfWidth = predictions[2 * ANCHORS + index] / 2
        val halfHeight = predictions[HEIGHT_CHANNEL * ANCHORS + index] / 2
        return Candidate(
            index,
            type,
            score,
            ImageBox(
                cx - halfWidth,
                cy - halfHeight,
                cx + halfWidth,
                cy + halfHeight
            )
        )
    }

    private fun suppress(candidates: List<Candidate>): List<Candidate> {
        val kept = mutableListOf<Candidate>()
        for (candidate in candidates) {
            if (kept.none {
                    it.classId == candidate.classId && it.box.iou(candidate.box) > NMS_IOU
                }
            ) {
                kept.add(candidate)
            }
            if (kept.size == MAX_DETECTIONS) break
        }
        return kept
    }

    private fun maskLogit(x: Int, y: Int, anchor: Int, raw: RawSegmentation): Float {
        var value = 0f
        for (channel in 0 until MASK_CHANNELS) {
            value += raw.predictions[(BOX_CHANNELS + CLASS_COUNT + channel) * ANCHORS + anchor] *
                raw.prototypes[channel * MASK_EDGE * MASK_EDGE + y * MASK_EDGE + x]
        }
        return value
    }

    private data class Candidate(
        val anchor: Int,
        val classId: Int,
        val score: Float,
        val box: ImageBox
    )

    companion object {
        const val CONFIDENCE = 0.35f
        const val NMS_IOU = 0.5f
        const val MAX_CANDIDATES = 300
        const val MAX_DETECTIONS = 20
        const val CLASS_COUNT = 80
        const val MASK_CHANNELS = 32
        const val MASK_EDGE = 160
        const val BOX_CHANNELS = 4
        const val CHANNELS = BOX_CHANNELS + CLASS_COUNT + MASK_CHANNELS
        const val ANCHORS = 8400
        const val HEIGHT_CHANNEL = 3
    }
}

internal fun maskFromLogits(logits: LogitGrid, box: ImageBox, projection: Letterbox): MaskGrid {
    val edge = YoloPostprocessor.MASK_EDGE
    val values = BooleanArray(edge * edge)
    for (y in 0 until edge) {
        for (x in 0 until edge) {
            if (insideMaskCrop(x, y, box, projection)) {
                values[y * edge + x] =
                    logits.values[y * edge + x] > 0f
            }
        }
    }
    return MaskGrid(edge, edge, values)
}

private fun insideMaskCrop(x: Int, y: Int, box: ImageBox, projection: Letterbox): Boolean {
    val px = x.toFloat() * Letterbox.EDGE / YoloPostprocessor.MASK_EDGE
    val py = y.toFloat() * Letterbox.EDGE / YoloPostprocessor.MASK_EDGE
    return px >= maxOf(box.left, projection.padLeft.toFloat()) &&
        px < minOf(box.right, (projection.padLeft + projection.scaledWidth).toFloat()) &&
        py >= maxOf(box.top, projection.padTop.toFloat()) &&
        py < minOf(box.bottom, (projection.padTop + projection.scaledHeight).toFloat())
}
