package dev.blamera.faces.runtime

internal data class FaceDetectorTransform(
    val resizedWidth: Int,
    val resizedHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
    val scaleX: Double,
    val scaleY: Double
)
