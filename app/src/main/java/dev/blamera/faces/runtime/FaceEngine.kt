package dev.blamera.faces.runtime

internal data class FaceRgbFrame(val width: Int, val height: Int, val pixels: ByteArray)

internal data class FaceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal sealed interface FaceEngineResult {
    data object NoFace : FaceEngineResult
    data object TooManyFaces : FaceEngineResult
    class Detected(faces: List<FaceObservation>) : FaceEngineResult {
        val faces: List<FaceObservation> = java.util.List.copyOf(faces)
    }
}

internal sealed interface FaceObservation {
    val bounds: FaceBounds
    class Encoded(override val bounds: FaceBounds, embedding: List<Double>) : FaceObservation {
        val embedding: List<Double> = java.util.List.copyOf(embedding)
    }
    data class Unavailable(override val bounds: FaceBounds) : FaceObservation
}

internal interface FaceEngine : AutoCloseable {
    suspend fun analyze(frame: FaceRgbFrame): FaceEngineResult
    override fun close()
}

internal class FaceEngineException : Exception("Analyse des visages indisponible.")

internal data class FaceDetection(val values: List<Double>) {
    val bounds: FaceBounds get() = FaceBounds(
        values[0].toFloat(),
        values[1].toFloat(),
        (values[0] + values[2]).toFloat(),
        (values[1] + values[FACE_HEIGHT_INDEX]).toFloat()
    )
}

internal const val FACE_HEIGHT_INDEX = 3

internal data class FaceEncoding(val alignedRgb: ByteArray, val embedding: List<Double>)

internal interface FaceKernel : AutoCloseable {
    fun detect(frame: FaceRgbFrame, transform: FaceDetectorTransform): List<List<Double>>
    fun encode(frame: FaceRgbFrame, detection: FaceDetection): FaceEncoding
    override fun close()
}
