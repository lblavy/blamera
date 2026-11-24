package dev.blamera.faces.runtime

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal sealed interface FaceParityExpected {
    data object None : FaceParityExpected
    data class Multiple(val faces: List<Single>) : FaceParityExpected
    data class Single(
        val bounds: List<Double>,
        val landmarks: List<Double>,
        val alignedRgb: ByteArray,
        val embedding: List<Double>
    ) : FaceParityExpected
}

internal data class FaceParityCase(val frame: FaceRgbFrame, val expected: FaceParityExpected)

internal data class FaceParityReport(
    val cases: Int,
    val boundsError: Double,
    val landmarksError: Double,
    val alignedMae: Double,
    val embeddingCosineMin: Double,
    val prototypeScoreDelta: Double,
    val decisionAgreement: Boolean
)

internal class FaceParityFailure(val report: FaceParityReport? = null) :
    RuntimeException("Public face parity failed")

internal fun verifyFaceParity(kernel: FaceKernel, cases: List<FaceParityCase>): FaceParityReport {
    require(cases.isNotEmpty())
    val totals = ParityTotals()
    for (case in cases) {
        val transform = detectorTransform(case.frame)
        val found = remapFaceRows(kernel.detect(case.frame, transform), case.frame, transform)
        when (val expected = case.expected) {
            FaceParityExpected.None -> requireMatchingFaceCount(found.isEmpty())
            is FaceParityExpected.Multiple -> {
                requireMatchingFaceCount(found.size == expected.faces.size && found.size > 1)
                val remaining = found.toMutableList()
                for (reference in expected.faces) {
                    val matched = remaining.minBy { maxError(it.coordinates(), reference.bounds) }
                    remaining.remove(matched)
                    totals.compare(matched, kernel.encode(case.frame, matched), reference)
                }
            }
            is FaceParityExpected.Single -> {
                requireMatchingFaceCount(found.size == 1)
                totals.compare(found.single(), kernel.encode(case.frame, found.single()), expected)
            }
        }
    }
    return totals.report(cases.size).also(::requireFaceParity)
}

private fun requireMatchingFaceCount(matches: Boolean) {
    if (!matches) throw FaceParityFailure()
}

private fun requireFaceParity(report: FaceParityReport) {
    val valid = report.boundsError <= MAXIMUM_POINT_ERROR &&
        report.landmarksError <= MAXIMUM_POINT_ERROR && report.alignedMae <= MAXIMUM_RGB_MAE &&
        report.embeddingCosineMin >= MINIMUM_EMBEDDING_COSINE &&
        report.prototypeScoreDelta <= MAXIMUM_PROTOTYPE_DELTA &&
        report.decisionAgreement
    if (!valid) throw FaceParityFailure(report)
}

private class ParityTotals {
    private var bounds = 0.0
    private var landmarks = 0.0
    private var aligned = 0.0
    private var cosine = 1.0
    private var scores = 0.0
    private var singles = 0
    private var decisions = true

    fun compare(found: FaceDetection, encoded: FaceEncoding, expected: FaceParityExpected.Single) {
        val actualBounds = found.coordinates()
        bounds = max(bounds, maxError(actualBounds, expected.bounds))
        landmarks =
            max(
                landmarks,
                maxError(found.values.subList(LANDMARK_START, LANDMARK_END), expected.landmarks)
            )
        aligned = max(aligned, rgbError(encoded.alignedRgb, expected.alignedRgb))
        val actual = normalizedFaceEmbedding(encoded.embedding)
        val reference = normalizedFaceEmbedding(expected.embedding)
        val currentCosine = actual.indices.sumOf { actual[it] * reference[it] }
        cosine = min(cosine, currentCosine)
        scores = max(scores, abs(1.0 - currentCosine))
        // Fixed public P and -P exercise both ends of the frozen decision rule.
        decisions = decisions && parityDecision(currentCosine) == parityDecision(1.0) &&
            parityDecision(-currentCosine) == parityDecision(-1.0)
        singles++
    }

    fun report(count: Int): FaceParityReport {
        require(singles > 0)
        return FaceParityReport(count, bounds, landmarks, aligned, cosine, scores, decisions)
    }
}

private fun FaceDetection.coordinates(): List<Double> = listOf(
    values[0],
    values[1],
    values[0] + values[2],
    values[1] + values[FACE_HEIGHT_INDEX]
)

private fun maxError(actual: List<Double>, expected: List<Double>): Double {
    require(actual.size == expected.size && actual.isNotEmpty() && expected.all(Double::isFinite))
    return actual.indices.maxOf { abs(actual[it] - expected[it]) }
}

private fun rgbError(actual: ByteArray, expected: ByteArray): Double {
    require(
        actual.size == FACE_ALIGNED_SIZE * FACE_ALIGNED_SIZE * FACE_RGB_CHANNELS &&
            actual.size == expected.size
    )
    return actual.indices.sumOf {
        abs((actual[it].toInt() and BYTE_MASK) - (expected[it].toInt() and BYTE_MASK))
    }.toDouble() /
        actual.size
}

private fun parityDecision(score: Double): Int = when {
    score < PARITY_REJECT -> -1
    score < PARITY_ACCEPT -> 0
    else -> 1
}

internal const val MAXIMUM_POINT_ERROR = 1.0
internal const val MAXIMUM_RGB_MAE = 1.0
internal const val MINIMUM_EMBEDDING_COSINE = .9999
internal const val MAXIMUM_PROTOTYPE_DELTA = .002
private const val LANDMARK_START = 4
private const val LANDMARK_END = 14
private const val BYTE_MASK = 255
private const val PARITY_REJECT = .30
private const val PARITY_ACCEPT = .50
