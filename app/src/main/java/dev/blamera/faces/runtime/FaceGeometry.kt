package dev.blamera.faces.runtime

import kotlin.math.max
import kotlin.math.min

internal fun detectorTransform(frame: FaceRgbFrame): FaceDetectorTransform {
    validateFaceFrame(frame)
    val scale = min(
        FACE_DETECTOR_SIZE.toDouble() / frame.width,
        FACE_DETECTOR_SIZE.toDouble() / frame.height
    )
    // Python round uses ties-to-even; Kotlin roundToInt uses a different tie rule.
    val width = max(1, Math.rint(frame.width * scale).toInt())
    val height = max(1, Math.rint(frame.height * scale).toInt())
    return FaceDetectorTransform(
        width,
        height,
        (FACE_DETECTOR_SIZE - width) / 2,
        (FACE_DETECTOR_SIZE - height) / 2,
        width.toDouble() / frame.width,
        height.toDouble() / frame.height
    )
}

internal fun remapFaceRows(
    rows: List<List<Double>>,
    frame: FaceRgbFrame,
    transform: FaceDetectorTransform
): List<FaceDetection> {
    val valid = remapFaceCandidates(rows, frame, transform).filter { validFaceDetection(it, frame) }
    require(valid.size <= FACE_MAX_CANDIDATES)
    return valid
}

internal fun remapFaceCandidates(
    rows: List<List<Double>>,
    frame: FaceRgbFrame,
    transform: FaceDetectorTransform
): List<FaceDetection> {
    require(rows.size <= FACE_DETECTOR_TOP_K)
    return rows.mapNotNull { row ->
        require(row.size == FACE_DETECTION_VALUES)
        remapFace(row, frame, transform)
    }.sortedByDescending { it.values.last() }
}

private fun remapFace(
    row: List<Double>,
    frame: FaceRgbFrame,
    transform: FaceDetectorTransform
): FaceDetection? {
    val values = row.toMutableList()
    val left = (row[0] - transform.offsetX) / transform.scaleX
    val top = (row[1] - transform.offsetY) / transform.scaleY
    val right = min(frame.width.toDouble(), left + row[2] / transform.scaleX)
    val bottom = min(frame.height.toDouble(), top + row[FACE_HEIGHT_INDEX] / transform.scaleY)
    values[0] = max(0.0, left)
    values[1] = max(0.0, top)
    values[2] = right - values[0]
    values[FACE_HEIGHT_INDEX] = bottom - values[1]
    for (point in FACE_LANDMARK_START until FACE_SCORE_INDEX step 2) {
        values[point] = (row[point] - transform.offsetX) / transform.scaleX
        values[point + 1] = (row[point + 1] - transform.offsetY) / transform.scaleY
    }
    return FaceDetection(values).takeIf {
        validFaceBounds(values, frame) && values[FACE_SCORE_INDEX] in 0.0..1.0
    }
}

internal fun validFaceDetection(detection: FaceDetection, frame: FaceRgbFrame): Boolean {
    val values = detection.values
    val invalidShape = values.size != FACE_DETECTION_VALUES || values.any { !it.isFinite() }
    if (invalidShape ||
        !validFaceBounds(values, frame) || values[FACE_SCORE_INDEX] !in 0.0..1.0
    ) {
        return false
    }
    val points = (FACE_LANDMARK_START until FACE_SCORE_INDEX step 2).map {
        values[it] to values[it + 1]
    }
    return points.distinct().size >= FACE_MIN_DISTINCT_LANDMARKS && points.all { (x, y) ->
        x in 0.0..frame.width.toDouble() && y in 0.0..frame.height.toDouble()
    }
}

private fun validFaceBounds(values: List<Double>, frame: FaceRgbFrame): Boolean =
    values[0] >= 0 && values[1] >= 0 && values[2] > 0 && values[FACE_HEIGHT_INDEX] > 0 &&
        values[0] + values[2] <= frame.width + FACE_BOUNDS_TOLERANCE &&
        values[1] + values[FACE_HEIGHT_INDEX] <= frame.height + FACE_BOUNDS_TOLERANCE

internal fun validateFaceFrame(frame: FaceRgbFrame) {
    require(frame.width in 1..FACE_MAX_FRAME_EDGE && frame.height in 1..FACE_MAX_FRAME_EDGE)
    require(frame.pixels.size == frame.width * frame.height * FACE_RGB_CHANNELS)
}

internal fun normalizedFaceEmbedding(vector: List<Double>): List<Double> {
    require(vector.size == FACE_EMBEDDING_DIMENSION && vector.all { it.isFinite() })
    val norm = vector.fold(0.0) { total, value -> Math.hypot(total, value) }
    require(norm.isFinite() && norm > 0)
    return vector.map { it / norm }
}

internal const val FACE_DETECTOR_SIZE = 640
internal const val FACE_DETECTOR_TOP_K = 5000
internal const val FACE_MAX_CANDIDATES = 20
internal const val FACE_ALIGNED_SIZE = 112
internal const val FACE_EMBEDDING_DIMENSION = 512
internal const val FACE_RGB_CHANNELS = 3
private const val FACE_MAX_FRAME_EDGE = 2048
private const val FACE_DETECTION_VALUES = 15
private const val FACE_LANDMARK_START = 4
private const val FACE_SCORE_INDEX = 14
private const val FACE_MIN_DISTINCT_LANDMARKS = 3
private const val FACE_BOUNDS_TOLERANCE = .001
