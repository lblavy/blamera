package dev.blamera.faces.runtime

import java.io.File
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF

internal data class FaceModelPaths(val detector: File, val aligner: File, val encoder: File)

internal class OpenCvFaceKernel(paths: FaceModelPaths) : FaceKernel {
    private var detector: FaceDetectorYN? = FaceDetectorYN.create(
        paths.detector.absolutePath,
        "",
        Size(FACE_DETECTOR_SIZE.toDouble(), FACE_DETECTOR_SIZE.toDouble()),
        DETECTION_CONFIDENCE,
        DETECTION_NMS,
        FACE_DETECTOR_TOP_K,
        Dnn.DNN_BACKEND_OPENCV,
        Dnn.DNN_TARGET_CPU
    )
    private var aligner: FaceRecognizerSF? = FaceRecognizerSF.create(
        paths.aligner.absolutePath,
        "",
        Dnn.DNN_BACKEND_OPENCV,
        Dnn.DNN_TARGET_CPU
    )
    private var encoder: Net? = Dnn.readNetFromONNX(paths.encoder.absolutePath).apply {
        setPreferableBackend(Dnn.DNN_BACKEND_OPENCV)
        setPreferableTarget(Dnn.DNN_TARGET_CPU)
        // Winograd may retain large transformed ResNet weights on a mobile CPU.
        enableWinograd(false)
    }

    override fun detect(frame: FaceRgbFrame, transform: FaceDetectorTransform): List<List<Double>> =
        FaceMats().use { mats ->
            val source = source(frame, mats)
            val resized = mats.create()
            Imgproc.resize(
                source,
                resized,
                Size(transform.resizedWidth.toDouble(), transform.resizedHeight.toDouble())
            )
            val canvas = mats.track(
                Mat.zeros(FACE_DETECTOR_SIZE, FACE_DETECTOR_SIZE, CvType.CV_8UC3)
            )
            val region = mats.track(
                canvas.submat(
                    transform.offsetY,
                    transform.offsetY + transform.resizedHeight,
                    transform.offsetX,
                    transform.offsetX + transform.resizedWidth
                )
            )
            resized.copyTo(region)
            val faces = mats.create()
            checkNotNull(detector).detect(canvas, faces)
            if (faces.empty()) return@use emptyList()
            require(
                faces.cols() == DETECTION_COLUMNS && faces.rows() <= FACE_DETECTOR_TOP_K &&
                    faces.type() == CvType.CV_32FC1
            )
            List(faces.rows()) { row ->
                val values = FloatArray(DETECTION_COLUMNS)
                faces.get(row, 0, values)
                values.map(Float::toDouble)
            }
        }

    override fun encode(frame: FaceRgbFrame, detection: FaceDetection): FaceEncoding =
        FaceMats().use { mats ->
            val source = source(frame, mats)
            val box = mats.track(Mat(1, DETECTION_COLUMNS, CvType.CV_32FC1))
            box.put(0, 0, detection.values.map(Double::toFloat).toFloatArray())
            val aligned = mats.create()
            checkNotNull(aligner).alignCrop(source, box, aligned)
            require(
                aligned.rows() == FACE_ALIGNED_SIZE && aligned.cols() == FACE_ALIGNED_SIZE &&
                    aligned.type() == CvType.CV_8UC3
            )
            val blob = mats.track(
                Dnn.blobFromImage(
                    aligned,
                    1.0 / ENCODER_CENTER,
                    Size(FACE_ALIGNED_SIZE.toDouble(), FACE_ALIGNED_SIZE.toDouble()),
                    Scalar(ENCODER_CENTER, ENCODER_CENTER, ENCODER_CENTER),
                    true,
                    false,
                    CvType.CV_32F
                )
            )
            val current = checkNotNull(encoder)
            current.setInput(blob)
            val feature = mats.track(current.forward())
            require(
                feature.dims() == 2 && feature.rows() == 1 &&
                    feature.cols() == FACE_EMBEDDING_DIMENSION &&
                    feature.type() == CvType.CV_32FC1
            )
            val vector = FloatArray(FACE_EMBEDDING_DIMENSION)
            feature.get(0, 0, vector)
            val rgb = mats.create()
            Imgproc.cvtColor(aligned, rgb, Imgproc.COLOR_BGR2RGB)
            val pixels = ByteArray(FACE_ALIGNED_SIZE * FACE_ALIGNED_SIZE * FACE_RGB_CHANNELS)
            rgb.get(0, 0, pixels)
            FaceEncoding(pixels, vector.map(Float::toDouble))
        }

    private fun source(frame: FaceRgbFrame, mats: FaceMats): Mat {
        val source = mats.track(Mat(frame.height, frame.width, CvType.CV_8UC3))
        source.put(0, 0, frame.pixels)
        Imgproc.cvtColor(source, source, Imgproc.COLOR_RGB2BGR)
        return source
    }

    override fun close() {
        // The official Java wrappers expose finalizers, not a public native delete.
        // Drop ownership only after the serialized inference has disposed all Mats.
        detector = null
        aligner = null
        encoder = null
    }
}

private class FaceMats : AutoCloseable {
    private val values = mutableListOf<Mat>()
    fun create(): Mat = track(Mat())
    fun track(value: Mat): Mat = value.also(values::add)
    override fun close() {
        values.asReversed().forEach(Mat::release)
    }
}

private const val DETECTION_COLUMNS = 15
private const val DETECTION_CONFIDENCE = .8f
private const val DETECTION_NMS = .3f
private const val ENCODER_CENTER = 127.5
