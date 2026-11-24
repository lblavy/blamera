package dev.blamera.faces

import kotlin.math.abs
import kotlin.math.sqrt

internal data class FacePolicy(val reject: Double, val accept: Double, val margin: Double) {
    init {
        require(listOf(reject, accept, margin).all { it.isFinite() })
        require(reject >= -1 && reject < accept && accept <= 1 && margin in 0.0..2.0)
    }
}

internal enum class FaceIdentityStatus { RECOGNIZED, UNKNOWN, UNCERTAIN }

internal class FaceIdentityDecision(
    val status: FaceIdentityStatus,
    val name: String?,
    val score: Double,
    val second: Double?
)

internal fun recognizeFace(
    embedding: List<Double>,
    gallery: FaceGalleryDocument
): FaceIdentityDecision {
    require(gallery.identities.isNotEmpty())
    val source = unitFaceVector(embedding)
    val scores = gallery.identities.map { person ->
        val target = unitFaceVector(person.prototype)
        FaceScore(person.id, person.name, faceDot(source, target).coerceIn(-1.0, 1.0))
    }.sortedWith(compareByDescending<FaceScore> { it.score }.thenBy { it.id })
    val first = scores.first()
    val second = scores.getOrNull(1)?.score
    val policy = gallery.policy
    val status = when {
        first.score < policy.reject -> FaceIdentityStatus.UNKNOWN
        first.score < policy.accept || (second != null && first.score - second < policy.margin) ->
            FaceIdentityStatus.UNCERTAIN
        else -> FaceIdentityStatus.RECOGNIZED
    }
    return FaceIdentityDecision(
        status,
        first.name.takeIf { status == FaceIdentityStatus.RECOGNIZED },
        first.score,
        second
    )
}

private fun unitFaceVector(values: List<Double>): List<Double> {
    require(values.size == FACE_EMBEDDING_DIMENSION && values.all { it.isFinite() })
    val largest = values.maxOf { abs(it) }
    require(largest > 0.0)
    val scaled = values.map { it / largest }
    val norm = largest * sqrt(faceDot(scaled, scaled))
    require(norm.isFinite() && norm > 0.0)
    return values.map { it / norm }
}

/** Compensated accumulation avoids rank/threshold drift from a naive 512-term float sum. */
private fun faceDot(left: List<Double>, right: List<Double>): Double {
    var total = 0.0
    var correction = 0.0
    for (index in left.indices) {
        val value = left[index] * right[index]
        val next = total + value
        correction += if (abs(total) >= abs(value)) total - next + value else value - next + total
        total = next
    }
    return total + correction
}

private class FaceScore(val id: String, val name: String, val score: Double)
private const val FACE_EMBEDDING_DIMENSION = 512
